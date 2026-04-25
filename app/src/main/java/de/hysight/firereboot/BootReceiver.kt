package de.hysight.firereboot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-starts [HttpService] after the device boots or this APK is replaced.
 *
 * Listening to multiple actions because vendors are inconsistent:
 *  - `BOOT_COMPLETED` — standard.
 *  - `LOCKED_BOOT_COMPLETED` — fires earlier on direct-boot-aware devices.
 *  - `QUICKBOOT_POWERON` — Samsung / Fire OS quick-boot variants. The `com.htc.*` form
 *    is harmless legacy from older Android and only fires on those devices.
 *  - `MY_PACKAGE_REPLACED` — fires after `adb install -r`, so the server self-restarts on
 *    upgrade without needing the user to reopen the app.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                context.startForegroundService(Intent(context, HttpService::class.java))
            }
        }
    }
}
