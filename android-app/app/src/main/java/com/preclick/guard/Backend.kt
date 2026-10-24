package com.preclick.guard

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Talks to the link-checker server. Only the link is sent, never the message text. */
object Backend {
    data class LinkResult(val url: String, val level: String, val risk: Double, val notes: List<String>)

    private val cache = HashMap<String, LinkResult>()

    /** Call from a background thread only. Returns null if no server is set or it cannot be reached. */
    fun check(ctx: Context, url: String): LinkResult? {
        val base = Prefs.backendUrl(ctx)
        if (base.isEmpty()) return null
        synchronized(cache) { cache[url]?.let { return it } }
        return try {
            val conn = URL("$base/check?url=" + URLEncoder.encode(url, "UTF-8"))
                .openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 25000
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val o = JSONObject(body)
            val arr = o.getJSONArray("notes")
            val notes = (0 until arr.length()).map { arr.getString(it) }
            val r = LinkResult(url, o.getString("level"), o.getDouble("risk"), notes)
            synchronized(cache) { cache[url] = r }
            r
        } catch (e: Throwable) {
            null
        }
    }
}
