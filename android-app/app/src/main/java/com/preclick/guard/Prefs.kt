package com.preclick.guard

import android.content.Context
import org.json.JSONArray

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("guard", Context.MODE_PRIVATE)

    fun isEnabled(c: Context): Boolean = sp(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("enabled", v).apply()

    fun backendUrl(c: Context): String = sp(c).getString("backend", "") ?: ""
    fun setBackendUrl(c: Context, v: String) =
        sp(c).edit().putString("backend", v.trim().trimEnd('/')).apply()

    @Synchronized
    fun addHistory(c: Context, line: String) {
        val old = JSONArray(sp(c).getString("history", "[]"))
        val out = JSONArray()
        out.put(line)
        for (i in 0 until minOf(old.length(), 29)) out.put(old.getString(i))
        sp(c).edit().putString("history", out.toString()).apply()
    }

    fun history(c: Context): List<String> {
        val arr = JSONArray(sp(c).getString("history", "[]"))
        return (0 until arr.length()).map { arr.getString(it) }
    }
}
