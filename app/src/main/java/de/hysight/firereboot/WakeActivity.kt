package de.hysight.firereboot

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.WindowManager

class WakeActivity : Activity() {
    companion object {
        private const val WAKE_TIMEOUT_MS = 3000L
        private const val FINISH_DELAY_MS = 2000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        // FULL_WAKE_LOCK is deprecated since API 17 but remains the only reliable way to
        // bring the screen back from off on Fire OS 7. The window flags above are not enough
        // when the display has fully timed out. Held briefly with ACQUIRE_CAUSES_WAKEUP to
        // force the wake; we release explicitly at finish() and rely on the OS timeout as a
        // safety net.
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wl = pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or
            PowerManager.ACQUIRE_CAUSES_WAKEUP or
            PowerManager.ON_AFTER_RELEASE,
            "FireReboot:wake"
        )
        wl.acquire(WAKE_TIMEOUT_MS)

        // Stay on screen briefly so the wake actually registers, then close ourselves.
        Handler(Looper.getMainLooper()).postDelayed({
            if (wl.isHeld) wl.release()
            finish()
        }, FINISH_DELAY_MS)
    }
}
