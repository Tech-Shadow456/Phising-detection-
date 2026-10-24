package com.preclick.guard

import android.content.Context

/** Runs the full pipeline: message score + link checks + alert. Call from a background thread. */
object Analyzer {
    data class Verdict(
        val level: String,
        val risk: Double,
        val reasons: List<String>,
        val linkNotes: List<String>
    )

    fun evaluate(ctx: Context, text: String): Verdict {
        Scorer.load(ctx)
        val r = Scorer.analyze(text)
        var level = r.level
        val notes = ArrayList<String>()
        for (u in r.links.take(3)) {
            val lr = Backend.check(ctx, u) ?: continue
            notes.add("$u -> ${lr.level}: ${lr.notes.joinToString("; ")}")
            if (lr.level == "High risk") level = "Phishing"
            else if (lr.level == "Be careful" && level == "Safe") level = "Suspicious"
        }
        return Verdict(level, r.risk, r.reasons, notes)
    }

    private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }

    fun format(v: Verdict): String {
        if (v.level == "Safe") return "Safe (risk ${(v.risk * 100).toInt()}%)"
        val sb = StringBuilder("${v.level.uppercase()} (risk ${(v.risk * 100).toInt()}%)")
        v.reasons.forEach { sb.append("\n- ").append(cap(it)) }
        v.linkNotes.forEach { sb.append("\n- Link: ").append(it) }
        return sb.toString()
    }

    fun process(ctx: Context, app: String, text: String) {
        val v = evaluate(ctx, text)
        if (v.level == "Safe") return
        val lines = ArrayList<String>()
        v.reasons.forEach { lines.add(cap(it)) }
        v.linkNotes.forEach { lines.add(it) }
        val body = lines.joinToString("\n")
        Notifier.alert(ctx, "${v.level} message in $app", body)
        Prefs.addHistory(ctx, "${v.level} - $app: ${text.take(60)}")
    }
}
