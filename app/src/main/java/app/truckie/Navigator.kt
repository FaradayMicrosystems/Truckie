package app.truckie

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.os.Handler
import android.os.Looper
import app.truckie.car.CarLink
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

    /**
     * v0.5.1, Android Auto only: when the phone is connected to Android Auto, the route goes to the
     * car screen and the phone stays on Truckie. Not connected: Maps or Waze opens on the phone as before.
     */
    fun navigate(context: Context, app: NavApp, stop: Stop) {
        if (CarLink.navigate(stop)) {
            toast(context, "Sent to Android Auto: ${stop.address}")
            return
        }
        if (CarLink.projecting) {
            // Connected to the car, but Truckie isn't open on the car screen, so Android won't let it send the route.
            toast(context, "Open Truckie on the car screen once, then tap Navigate again. (Maps stays off the phone while you're connected.)")
            return
        }
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

    private fun toast(context: Context, text: String) =
        Handler(Looper.getMainLooper()).post { Toast.makeText(context.applicationContext, text, Toast.LENGTH_LONG).show() }
}
