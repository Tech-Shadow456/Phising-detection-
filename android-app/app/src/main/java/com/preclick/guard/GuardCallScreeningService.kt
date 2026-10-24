package com.preclick.guard

import android.content.Context
import android.telecom.Call
import android.telecom.CallScreeningService

/** Checks the caller's NUMBER only (no audio) against a local spam list. Never blocks the call. */
class GuardCallScreeningService : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        try {
            if (Prefs.isEnabled(this)) {
                val number = details.handle?.schemeSpecificPart ?: ""
                if (SpamNumbers.isSpam(this, number)) {
                    Notifier.alert(this, "Possible spam call", "The number $number is on the spam list.")
                    Prefs.addHistory(this, "Spam call - $number")
                }
            }
        } catch (e: Throwable) {
            // ignore
        }
        respondToCall(details, CallResponse.Builder().build())
    }
}

object SpamNumbers {
    private var numbers: Set<String>? = null

    @Synchronized
    fun isSpam(ctx: Context, raw: String): Boolean {
        if (numbers == null) {
            numbers = ctx.assets.open("spam_numbers.txt").bufferedReader().readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { last10(it) }
                .toSet()
        }
        val n = last10(raw)
        return n.isNotEmpty() && numbers!!.contains(n)
    }

    private fun last10(s: String) = s.filter { it.isDigit() }.takeLast(10)
}
