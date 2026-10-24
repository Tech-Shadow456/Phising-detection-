# PreClick Guard - Android app

Kotlin app with no external libraries.

| File | Job |
|---|---|
| MainActivity | ON/OFF switch, permission checklist, test box, alert history |
| GuardNotificationListener | Receives every incoming notification and scans it |
| Scorer | On-device model (model.json) + rules |
| Backend / Analyzer | Link checks through the server, then the final verdict and alert |
| LinkAccessibilityService | Shows a note with an arrow near links on screen |
| GuardCallScreeningService | Checks the caller number against assets/spam_numbers.txt |
| GuardService / BootReceiver | Keeps protection alive and restarts it after reboot |

Before building: run `python src/export_model.py` to create `app/src/main/assets/model.json`.

Build on GitHub: push, open the Actions tab, run "Build Android APK", download `PreClickGuard-apk`.
