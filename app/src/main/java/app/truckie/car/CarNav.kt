package app.truckie.car

import android.content.Intent
import android.net.Uri
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import app.truckie.core.Stop

/**
 * In the car, Android Auto opens its own default navigation app (set in Android Auto's settings),
 * not the Google Maps / Waze choice from the phone menu.
 */
fun CarContext.navigateTo(stop: Stop) {
    try {
        startCarApp(Intent(CarContext.ACTION_NAVIGATE, Uri.parse("geo:${stop.location.toQuery()}")))
    } catch (e: Exception) {
        CarToast.makeText(this, "Couldn't open navigation: ${e.message}", CarToast.LENGTH_LONG).show()
    }
}
