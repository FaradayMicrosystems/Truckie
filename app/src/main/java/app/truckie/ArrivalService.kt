package app.truckie

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.car.app.notification.CarAppExtender
import androidx.car.app.notification.CarPendingIntent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.truckie.car.TruckieCarAppService
import app.truckie.core.ArrivalDetector
import app.truckie.core.ArrivalRules
import app.truckie.core.ArrivalState
import app.truckie.core.Fix
import app.truckie.core.LatLng
import app.truckie.core.NavApp
import app.truckie.core.Stop
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Auto-complete on arrival (v0.5). Watches GPS while you drive, with a small "Watching for arrival"
 * notification so it keeps going while Google Maps or Waze is on screen. When the vehicle has been
 * stopped inside the next stop's zone for 2 minutes (see ArrivalDetector), it does what the green
 * button does: strikes the stop out, then opens navigation to the next stop after 5 seconds.
 * The alert has Undo, which puts the stop back.
 */
class ArrivalService : Service(), LocationListener {

    companion object {
        const val ACTION_UNDO = "app.truckie.action.UNDO_AUTO_COMPLETE"
        const val ACTION_TURN_OFF = "app.truckie.action.TURN_OFF_AUTO_COMPLETE"
        /** Sent to the car app by the alert's Navigate button on the car screen. */
        const val ACTION_CAR_NAVIGATE = "app.truckie.action.CAR_NAVIGATE"
        const val EXTRA_STOP_ID = "stopId"

        private const val CH_WATCH = "arrival_watch"
        private const val CH_ALERT = "arrival_alert"
        private const val NOTE_WATCH = 41
        const val NOTE_ALERT = 42

        private const val NEAR_INTERVAL_MS = 5_000L
        private const val FAR_INTERVAL_MS = 30_000L
        /** Further than this from the next stop: check GPS less often to save battery. */
        private const val FAR_METRES = 1_500
        private const val AUTO_NAVIGATE_MS = 5_000L

        fun hasLocationPermission(context: Context) =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        /** Android only lets a background app open Maps itself with "Display over other apps" switched on. */
        fun canOpenNavigationFromBackground(context: Context) = Settings.canDrawOverlays(context)

        /**
         * Starts watching if auto-complete is on, location is allowed and there is a stop to do.
         * Call while Truckie is on screen (phone or car): Android blocks starting it from the background.
         */
        fun startIfWanted(context: Context): Boolean {
            if (!Prefs(context).autoComplete || !hasLocationPermission(context)) return false
            if (StopRepository.init(context).stops.value.next == null) return false
            return runCatching {
                ContextCompat.startForegroundService(context, Intent(context, ArrivalService::class.java))
            }.isSuccess
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ArrivalService::class.java))
        }
    }

    private val scope = MainScope()
    private val handler = Handler(Looper.getMainLooper())
    private val detector = ArrivalDetector()
    private lateinit var repo: StopRepository
    private lateinit var prefs: Prefs
    private lateinit var lm: LocationManager

    private var intervalMs = 0L
    private var watchText = ""
    private var pendingNavigate: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        repo = StopRepository.init(this)
        prefs = Prefs(this)
        lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createChannels()
        Arrivals.running.value = true
        // Nothing left to do (all done or list cleared): stop watching and drop the notification.
        scope.launch {
            repo.stops.collect { list -> if (list.next == null && pendingNavigate == null) stopSelf() }
        }
        scope.launch { Arrivals.undone.collect { undone() } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android refuses if location isn't allowed while Truckie is in use: nothing to do then.
        if (runCatching { goForeground() }.isFailure) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!prefs.autoComplete || !hasLocationPermission(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        listen(NEAR_INTERVAL_MS)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runCatching { lm.removeUpdates(this) }
        pendingNavigate?.let { handler.removeCallbacks(it) }
        scope.cancel()
        Arrivals.running.value = false
        Arrivals.status.value = ArrivalState.Idle
        super.onDestroy()
    }

    // --- GPS ---

    @SuppressLint("MissingPermission")
    private fun listen(every: Long) {
        if (every == intervalMs) return
        intervalMs = every
        try {
            lm.removeUpdates(this)
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, every, 0f, this, Looper.getMainLooper())
        } catch (_: SecurityException) {
            stopSelf()
        } catch (_: IllegalArgumentException) {
            showWatching("GPS is off. Turn on location to auto-complete stops.")
        }
    }

    override fun onLocationChanged(location: Location) {
        val fix = Fix(
            at = LatLng(location.latitude, location.longitude),
            timeMillis = location.elapsedRealtimeNanos / 1_000_000,
            accuracyM = if (location.hasAccuracy()) location.accuracy else null,
            speedMps = if (location.hasSpeed()) location.speed else null,
        )
        detector.rules = ArrivalRules(radiusM = prefs.arrivalRadiusM)
        val next = repo.stops.value.next
        val state = detector.onFix(next, fix)
        Arrivals.status.value = state
        when (state) {
            is ArrivalState.Arrived -> arrived(state.stopId)
            is ArrivalState.Waiting -> {
                listen(NEAR_INTERVAL_MS)
                showWatching("Stopped at ${next?.address}: done in ${mmss(state.secondsLeft)} unless you move")
            }
            is ArrivalState.Heading -> {
                listen(if (state.metres > FAR_METRES) FAR_INTERVAL_MS else NEAR_INTERVAL_MS)
                showWatching("Watching for arrival at ${next?.address} (${distance(state.metres)})")
            }
            ArrivalState.Idle -> showWatching("Waiting for a good GPS fix…")
        }
    }

    // Needed on Android 8–10, where these aren't default methods yet.
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) { showWatching("GPS is off. Turn on location to auto-complete stops.") }

    // --- Arrived: same as the green button ---

    private fun arrived(id: Long) {
        val done = repo.stops.value.stops.firstOrNull { it.id == id } ?: return
        val after = repo.change { it.complete(id, System.currentTimeMillis()) }
        Arrivals.emitCompleted(id)
        val next = after.next
        val app = prefs.navApp

        when {
            next == null -> alert(done, null, "All stops done. Good on ya!")
            // The phone screen or the car screen shows its own "Navigate to next stop?" countdown.
            Arrivals.carConnected -> alert(done, next, "Next: ${next.address}. Opening navigation in 5 s on the car screen.")
            Arrivals.phoneVisible -> alert(done, next, "Next: ${next.address}")
            canOpenNavigationFromBackground(this) -> {
                alert(done, next, "Opening ${app.label} to ${next.address} in 5 s. Tap Undo if this was wrong.")
                val go = Runnable {
                    pendingNavigate = null
                    Navigator.navigate(this, app, next)
                    alert(done, next, "Navigating to ${next.address}.")
                }
                pendingNavigate = go
                handler.postDelayed(go, AUTO_NAVIGATE_MS)
            }
            else -> alert(done, next, "Tap Navigate for ${next.address}. (Turn on \"Open Maps by itself\" in Truckie's ⋮ menu to skip this tap.)")
        }
    }

    /** Undo was tapped (ArrivalActionReceiver already put the stop back): don't open navigation. */
    private fun undone() {
        pendingNavigate?.let { handler.removeCallbacks(it) }
        pendingNavigate = null
        showWatching("Stop put back. It won't auto-complete again until you drive off.")
    }

    // --- Notifications ---

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_WATCH, "Watching for arrival", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Shown while Truckie watches GPS to auto-complete stops." }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "Stop auto-completed", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Pops up when Truckie marks a stop done, with Undo." }
        )
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun receiverAction(action: String, id: Long = -1, request: Int): PendingIntent = PendingIntent.getBroadcast(
        this, request, Intent(this, ArrivalActionReceiver::class.java).setAction(action).putExtra(EXTRA_STOP_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun watchNotification(text: String): Notification =
        NotificationCompat.Builder(this, CH_WATCH)
            .setSmallIcon(R.drawable.ic_stat_truckie)
            .setContentTitle("Auto-complete is on")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp())
            .addAction(0, "Turn off", receiverAction(ACTION_TURN_OFF, request = 1))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()

    private fun goForeground() {
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, NOTE_WATCH, watchNotification(watchText.ifEmpty { "Starting GPS…" }), type)
    }

    @SuppressLint("MissingPermission") // Notifications just don't show without permission; nothing breaks.
    private fun showWatching(text: String) {
        if (text == watchText) return
        watchText = text
        runCatching { NotificationManagerCompat.from(this).notify(NOTE_WATCH, watchNotification(text)) }
    }

    @SuppressLint("MissingPermission")
    private fun alert(done: Stop, next: Stop?, text: String) {
        val title = "Done: ${done.address}"
        val undo = receiverAction(ACTION_UNDO, done.id, request = 2)
        val b = NotificationCompat.Builder(this, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat_truckie)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setAutoCancel(true)
            .setTimeoutAfter(10 * 60_000L)
            .addAction(0, "Undo", undo)

        val car = CarAppExtender.Builder()
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_truckie)
            .setImportance(NotificationManager.IMPORTANCE_HIGH)
            .addAction(R.drawable.ic_stat_truckie, "Undo", undo)

        if (next != null) {
            b.addAction(0, "Navigate", PendingIntent.getActivity(
                this, 3, Navigator.intent(prefs.navApp, next),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ))
            val carNav = Intent(ACTION_CAR_NAVIGATE, Uri.parse(NavApp.carGeoUri(next)))
                .setComponent(ComponentName(this, TruckieCarAppService::class.java))
            car.addAction(R.drawable.ic_stat_truckie, "Navigate", CarPendingIntent.getCarApp(this, 4, carNav, 0))
        }
        b.extend(car.build())
        runCatching { NotificationManagerCompat.from(this).notify(NOTE_ALERT, b.build()) }
    }

    private fun mmss(s: Int) = "%d:%02d".format(s / 60, s % 60)
    private fun distance(m: Int) = if (m < 1000) "$m m away" else "%.1f km away".format(m / 1000.0)
}
