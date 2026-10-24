package com.preclick.guard

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.Executors

/** Receives every incoming notification (WhatsApp, Telegram, SMS, Gmail...) while protection is ON. */
class GuardNotificationListener : NotificationListenerService() {
    private val pool = Executors.newSingleThreadExecutor()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!Prefs.isEnabled(this)) return
        if (sbn.packageName == packageName) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return

        val ex = n.extras
        val body = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: return
        if (body.isBlank()) return

        val app = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (e: Throwable) {
            sbn.packageName
        }
        pool.execute {
            try {
                Analyzer.process(applicationContext, app, body)
            } catch (e: Throwable) {
                // never crash the listener
            }
        }
    }
}
