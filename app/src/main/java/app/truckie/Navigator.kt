package app.truckie

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
 */
object Navigator {
    /** The intent that opens [app] with driving directions to [stop]. Also used by the arrival notification's Navigate button. */
    fun intent(app: NavApp, stop: Stop): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(app.navigationUri(stop)))
            .setPackage(app.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun navigate(context: Context, app: NavApp, stop: Stop) {
        val direct = intent(app, stop)
        try {
            context.startActivity(direct)
            return
        } catch (_: ActivityNotFoundException) {
            // App not installed: fall through to the browser link.
        }
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(app.webUri(stop))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Toast.makeText(context, "${app.label} isn't installed; opened directions in the browser.", Toast.LENGTH_LONG).show()
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No app can open directions. Install ${app.label}.", Toast.LENGTH_LONG).show()
        }
    }
}
