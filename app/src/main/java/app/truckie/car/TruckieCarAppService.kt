package app.truckie.car

import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import app.truckie.ArrivalService
import app.truckie.Arrivals

/** Entry point for Android Auto. Same setup as pinnr. */
class TruckieCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator {
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        return if (debuggable) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }
    }

    override fun onCreateSession(): Session = object : Session() {
        init {
            lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onCreate(owner: LifecycleOwner) {
                    Arrivals.carConnected = true
                    // Truckie is on the car screen now, so Android lets auto-complete start watching.
                    ArrivalService.startIfWanted(carContext)
                }
                override fun onDestroy(owner: LifecycleOwner) { Arrivals.carConnected = false }
            })
        }

        override fun onCreateScreen(intent: Intent): Screen = NextStopScreen(carContext)

        /** "Navigate" on the auto-complete alert on the car screen. */
        override fun onNewIntent(intent: Intent) {
            if (intent.action == ArrivalService.ACTION_CAR_NAVIGATE) {
                runCatching { carContext.startCarApp(Intent(CarContext.ACTION_NAVIGATE, intent.data)) }
            }
        }
    }
}
