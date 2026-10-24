package com.preclick.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts protection after the phone reboots (if the switch was ON). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && Prefs.isEnabled(context)) {
            try {
                context.startForegroundService(Intent(context, GuardService::class.java))
            } catch (e: Exception) {
                // the notification listener still restarts by itself
            }
        }
    }
}
