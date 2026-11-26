package app.truckie

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * v0.11.0: optional helper so Navigate can put Truckie into split screen by itself when it starts full screen.
 * Phones ignore FLAG_ACTIVITY_LAUNCH_ADJACENT from a full-screen app, but an accessibility service may press the
 * system "split screen" action (GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN). It reads nothing on screen and handles no events.
 * The driver turns it on in Android Settings > Accessibility > Truckie split screen.
 */
class SplitScreenHelper : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: android.content.Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onDestroy() { instance = null; super.onDestroy() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    companion object {
        @Volatile private var instance: SplitScreenHelper? = null

        /** True when the driver has turned the helper on in Accessibility settings. */
        val isOn: Boolean get() = instance != null

        /** Presses the system split-screen action. False when the helper is off or the phone refused. */
        fun enterSplitScreen(): Boolean =
            runCatching { instance?.performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN) ?: false }.getOrDefault(false)
    }
}
