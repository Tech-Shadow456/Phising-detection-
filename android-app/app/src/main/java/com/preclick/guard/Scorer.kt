package com.preclick.guard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/** On-device phishing scorer: TF-IDF + Logistic Regression weights (model.json) plus rule flags. */
object Scorer {
    data class Result(
        val level: String,
        val risk: Double,
        val modelProb: Double,
        val reasons: List<String>,
        val links: List<String>
    )

    private var loaded = false
    private val vocab = HashMap<String, Int>()
    private var idf = DoubleArray(0)
    private var coef = DoubleArray(0)
    private var intercept = 0.0
    private var urlRegex = Regex("https?://\\S+", RegexOption.IGNORE_CASE)
    private var urgent = listOf<String>()
    private var sensitive = listOf<String>()
    private var money = listOf<String>()
    private var brands = listOf<String>()
    private var shorteners = listOf<String>()
    private var badTlds = listOf<String>()
    private val tokenRegex = Regex("(?U)\\b\\w\\w+\\b")

    fun isLoaded() = loaded

    @Synchronized
    fun load(ctx: Context) {
        if (loaded) return
        val text = ctx.assets.open("model.json").bufferedReader().use { it.readText() }
        val o = JSONObject(text)
        val v = o.getJSONObject("vocab")
        val keys = v.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            vocab[k] = v.getInt(k)
        }
        idf = doubles(o.getJSONArray("idf"))
        coef = doubles(o.getJSONArray("coef"))
        intercept = o.getDouble("intercept")
        urlRegex = Regex(o.getString("url_regex"), RegexOption.IGNORE_CASE)
        urgent = strings(o.getJSONArray("urgent"))
        sensitive = strings(o.getJSONArray("sensitive"))
        money = strings(o.getJSONArray("money"))
        brands = strings(o.getJSONArray("brands"))
        shorteners = strings(o.getJSONArray("shorteners"))
        badTlds = strings(o.getJSONArray("bad_tlds"))
        loaded = true
    }

    private fun doubles(a: JSONArray) = DoubleArray(a.length()) { a.getDouble(it) }
    private fun strings(a: JSONArray) = (0 until a.length()).map { a.getString(it) }

    fun extractUrls(text: String): List<String> =
        urlRegex.findAll(text).map { it.value.trimEnd('.', ',', ')', ';', ']') }.toList()

    private fun hostOf(url: String): String {
        var u = url
        if (!u.lowercase().startsWith("http")) u = "http://$u"
        return u.substringAfter("://").substringBefore('/').substringBefore('?')
            .substringBefore('#').substringAfterLast('@').substringBefore(':').lowercase()
    }

    private fun modelProbability(text: String): Double {
        val tokens = tokenRegex.findAll(text.lowercase()).map { it.value }.toList()
        val counts = HashMap<Int, Int>()
        fun add(t: String) {
            val i = vocab[t] ?: return
            counts[i] = (counts[i] ?: 0) + 1
        }
        tokens.forEach { add(it) }
        for (i in 0 until tokens.size - 1) add(tokens[i] + " " + tokens[i + 1])

        val weights = HashMap<Int, Double>()
        var norm = 0.0
        for ((i, c) in counts) {
            val w = (1.0 + ln(c.toDouble())) * idf[i]
            weights[i] = w
            norm += w * w
        }
        norm = sqrt(norm)
        var z = intercept
        if (norm > 0) for ((i, w) in weights) z += coef[i] * (w / norm)
        return 1.0 / (1.0 + exp(-z))
    }

    private fun wordRegex(w: String) = Regex("\\b" + Regex.escape(w) + "\\b")

    private fun flags(text: String): List<Pair<Double, String>> {
        val t = text.lowercase()
        val f = ArrayList<Pair<Double, String>>()
        if (urgent.any { t.contains(it) }) f.add(0.25 to "uses urgent or threatening language")
        val warns = Regex("(do not|don't|never) share").containsMatchIn(t)
        if (!warns && sensitive.any { wordRegex(it).containsMatchIn(t) })
            f.add(0.35 to "asks for sensitive details (OTP, PIN, card, KYC)")
        if (money.any { t.contains(it) }) f.add(0.25 to "promises a prize, refund or reward")

        val urls = extractUrls(text)
        if (urls.any { u -> hostOf(u).let { h -> shorteners.any { s -> h == s || h.endsWith(".$s") } } })
            f.add(0.25 to "contains a shortened link that hides the real address")
        if (urls.any { Regex("\\d{1,3}(\\.\\d{1,3}){3}").matches(hostOf(it)) })
            f.add(0.35 to "link uses a raw IP address instead of a website name")
        if (urls.any { u -> badTlds.any { hostOf(u).endsWith(it) } })
            f.add(0.2 to "link ends in a domain type often used by scams")
        if (urls.isNotEmpty()) {
            for (b in brands) {
                if (wordRegex(b).containsMatchIn(t) && urls.none { hostOf(it).contains(b.replace(" ", "")) }) {
                    f.add(0.3 to "mentions ${b.uppercase()} but the link is not from ${b.uppercase()}")
                    break
                }
            }
        }
        return f
    }

    fun analyze(text: String): Result {
        val p = modelProbability(text)
        val fl = flags(text)
        val rs = minOf(1.0, fl.sumOf { it.first })
        var risk = 0.7 * p + 0.3 * rs
        if (rs >= 0.6) risk = max(risk, 0.6)
        val level = if (risk >= 0.6) "Phishing" else if (risk >= 0.35) "Suspicious" else "Safe"
        val reasons = fl.map { it.second }.toMutableList()
        if (level != "Safe" && reasons.isEmpty()) reasons.add("message wording is similar to known scams")
        return Result(level, risk, p, reasons, extractUrls(text))
    }
}
