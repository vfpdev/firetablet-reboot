package de.hysight.firereboot

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class RebootAccessibilityService : AccessibilityService() {

    // Set true just before opening the power menu; gates onAccessibilityEvent so the service
    // ignores window changes from other apps when no reboot was requested. Auto-resets after
    // ARMED_TIMEOUT_MS so a missed power dialog can't leave us armed forever (would otherwise
    // risk clicking "Restart"/"Reboot" labelled UI in some unrelated app).
    @Volatile private var armed = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val disarm = Runnable { armed = false }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!armed) return
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event?.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val root = rootInActiveWindow ?: return
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        for (label in REBOOT_LABELS) {
            candidates += root.findAccessibilityNodeInfosByText(label)
        }
        if (candidates.isEmpty()) return

        for (node in candidates) {
            var n: AccessibilityNodeInfo? = node
            while (n != null && !n.isClickable) n = n.parent
            if (n != null) {
                Log.i(TAG, "clicking reboot")
                n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                armed = false
                mainHandler.removeCallbacks(disarm)
                return
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance === this) instance = null
        mainHandler.removeCallbacks(disarm)
        super.onDestroy()
    }

    fun triggerReboot() {
        armed = true
        mainHandler.removeCallbacks(disarm)
        mainHandler.postDelayed(disarm, ARMED_TIMEOUT_MS)
        performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
    }

    fun lockScreen() {
        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    }

    companion object {
        private const val TAG = "RebootA11y"
        private const val ARMED_TIMEOUT_MS = 5000L

        // Fire OS shows "Neu starten" (de) / "Restart" (en) in its power menu;
        // "Reboot" covers third-party ROMs. Other locales need to be added here if we ever ship them.
        private val REBOOT_LABELS = listOf("Neu starten", "Restart", "Reboot")

        // Static reference so HttpService can call triggerReboot()/lockScreen() directly without
        // a Binder. Nulled in onDestroy so a stale reference can't outlive the service.
        @Volatile var instance: RebootAccessibilityService? = null
    }
}
