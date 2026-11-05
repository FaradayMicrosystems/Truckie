package app.truckie.car

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.car.app.CarContext
import app.truckie.core.NavApp
import app.truckie.core.Stop
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * v0.5.1: while Truckie's car session is alive, the phone's Navigate sends the route to the car
 * screen instead of opening Maps on the phone.
 */
object CarLink {
    /** Set while Truckie's Android Auto session exists (opened on the car screen at least once this drive). */
    @Volatile var carContext: CarContext? = null

    /** Phone is projecting to Android Auto (from CarConnection), whether or not Truckie is open there. */
    @Volatile var projecting = false

    /** Starts navigation to [stop] on the car screen. True if Android Auto took it. */
    fun navigate(stop: Stop): Boolean {
        val car = carContext ?: return false
        val go = { runCatching { car.startCarApp(Intent(CarContext.ACTION_NAVIGATE, Uri.parse(NavApp.carGeoUri(stop)))) }.isSuccess }
        if (Looper.myLooper() == Looper.getMainLooper()) return go()
        // Car calls must run on the main thread.
        var ok = false
        val done = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post { ok = go(); done.countDown() }
        done.await(2, TimeUnit.SECONDS)
        return ok
    }
}
