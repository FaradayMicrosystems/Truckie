package app.truckie

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import app.truckie.core.NavApp
import app.truckie.core.Stop

/**
 * Hands one stop to Google Maps or Waze, which starts driving directions straight away.
 * Opening the next stop replaces the previous route there, so the nav app never holds the list.
 */
object Navigator {
    fun navigate(context: Context, app: NavApp, stop: Stop) {
        val direct = Intent(Intent.ACTION_VIEW, Uri.parse(app.navigationUri(stop.location)))
            .setPackage(app.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(direct)
            return
        } catch (_: ActivityNotFoundException) {
            // App not installed: fall through to the browser link.
        }
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(app.webUri(stop.location))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Toast.makeText(context, "${app.label} isn't installed; opened directions in the browser.", Toast.LENGTH_LONG).show()
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No app can open directions. Install ${app.label}.", Toast.LENGTH_LONG).show()
        }
    }
}
