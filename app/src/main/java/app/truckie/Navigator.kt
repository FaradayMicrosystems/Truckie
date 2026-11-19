package app.truckie

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import app.truckie.core.NavApp
import app.truckie.core.Stop

/**
 * Hands one stop to Google Maps or Waze, by its address text (see NavApp.navigationUri), which starts driving directions straight away.
 * Opening the next stop replaces the previous route there, so the nav app never holds the list.
 *
 * v0.8.0: in split screen, the nav app opens in the OTHER half (FLAG_ACTIVITY_LAUNCH_ADJACENT), or is reused where it
 * already is, so Truckie stays on screen. Turn off with ⋮ > "Split screen: Maps in other half" if a phone misbehaves.
 */
object Navigator {
    /** The intent that opens [app] with driving directions to [stop]. Also used by the arrival notification's Navigate button. */
    fun intent(app: NavApp, stop: Stop): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(app.navigationUri(stop)))
            .setPackage(app.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** True when Truckie is sharing the screen (split screen or a pop-up window) and the driver wants to keep it there. */
    fun keepSideBySide(context: Context): Boolean {
        if (!Prefs(context).splitScreenAdjacent) return false
        return (context as? Activity)?.isInMultiWindowMode ?: Arrivals.inMultiWindow
    }

    /** Adds "open in the other half" to [intent] when [keepSideBySide]. Harmless when Android ignores it. */
    fun sideBySide(context: Context, intent: Intent): Intent =
        if (keepSideBySide(context)) intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT) else intent

    fun navigate(context: Context, app: NavApp, stop: Stop) {
        val direct = sideBySide(context, intent(app, stop))
        try {
            context.startActivity(direct)
            return
        } catch (_: ActivityNotFoundException) {
            // App not installed: fall through to the browser link.
        } catch (_: RuntimeException) {
            // A phone that refuses the split-screen launch: open the plain way (never leave Navigate dead, see v0.5.1).
            try {
                context.startActivity(intent(app, stop))
                return
            } catch (_: ActivityNotFoundException) {
            }
        }
        try {
            context.startActivity(sideBySide(context, Intent(Intent.ACTION_VIEW, Uri.parse(app.webUri(stop))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)))
            Toast.makeText(context, "${app.label} isn't installed; opened directions in the browser.", Toast.LENGTH_LONG).show()
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No app can open directions. Install ${app.label}.", Toast.LENGTH_LONG).show()
        }
    }
}
