package com.preclick.guard

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var checklist: TextView
    private lateinit var history: TextView
    private lateinit var testResult: TextView
    private lateinit var toggle: Switch
    private lateinit var backendUrl: EditText
    private val pool = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        checklist = findViewById(R.id.checklist)
        history = findViewById(R.id.history)
        testResult = findViewById(R.id.testResult)
        toggle = findViewById(R.id.toggle)
        backendUrl = findViewById(R.id.backendUrl)
        backendUrl.setText(Prefs.backendUrl(this))
        toggle.isChecked = Prefs.isEnabled(this)

        toggle.setOnCheckedChangeListener { _, on ->
            Prefs.setEnabled(this, on)
            if (on) {
                requestNotifPermission()
                try {
                    startForegroundService(Intent(this, GuardService::class.java))
                } catch (e: Throwable) {
                    // permission may still be missing
                }
            } else {
                stopService(Intent(this, GuardService::class.java))
            }
            refresh()
        }

        findViewById<Button>(R.id.btnListener).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        findViewById<Button>(R.id.btnNotif).setOnClickListener { requestNotifPermission() }
        findViewById<Button>(R.id.btnBattery).setOnClickListener {
            try {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
                )
            } catch (e: Throwable) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
        findViewById<Button>(R.id.btnAccess).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btnCall).setOnClickListener {
            if (Build.VERSION.SDK_INT >= 29) {
                val rm = getSystemService(RoleManager::class.java)
                if (rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                    startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), 7)
                }
            }
        }
        findViewById<Button>(R.id.btnSaveUrl).setOnClickListener {
            Prefs.setBackendUrl(this, backendUrl.text.toString())
            testResult.text = "Server URL saved."
        }
        findViewById<Button>(R.id.btnTest).setOnClickListener {
            val text = findViewById<EditText>(R.id.testInput).text.toString()
            testResult.text = "Checking..."
            pool.execute {
                val out = try {
                    Analyzer.format(Analyzer.evaluate(applicationContext, text))
                } catch (e: Throwable) {
                    "Error: ${e.message}"
                }
                runOnUiThread { testResult.text = out }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun refresh() {
        val enabled = Prefs.isEnabled(this)
        val listeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        val listenerOn = listeners.contains(packageName)
        val access = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val accessOn = access.contains(packageName)
        val battery = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

        status.text = when {
            enabled && listenerOn -> "Protection ACTIVE"
            enabled -> "Turned ON, but notification access is missing"
            else -> "Protection OFF"
        }
        fun mark(ok: Boolean) = if (ok) "[OK]" else "[MISSING]"
        checklist.text = "${mark(listenerOn)} Notification access\n" +
            "${mark(battery)} Battery not restricted\n" +
            "${mark(accessOn)} Link notes (Accessibility)"
        val h = Prefs.history(this)
        history.text = if (h.isEmpty()) "No alerts yet." else h.joinToString("\n") { "- $it" }
    }
}
