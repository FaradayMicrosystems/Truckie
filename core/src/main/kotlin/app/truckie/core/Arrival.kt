package app.truckie.core

/**
 * One GPS reading. [timeMillis] must come from a clock that never jumps (Android: elapsedRealtime).
 * [speedMps] and [accuracyM] are null when the phone didn't report them.
 */
data class Fix(val at: LatLng, val timeMillis: Long, val accuracyM: Float? = null, val speedMps: Float? = null)

/** Settings for auto-complete on arrival. */
data class ArrivalRules(
    /** How close to the stop's pin counts as "at the stop". */
    val radiusM: Int = DEFAULT_RADIUS_M,
    /** How long the vehicle must stay stopped inside the zone. */
    val dwellMillis: Long = 120_000,
    /** Readings less accurate than this are ignored: the clock pauses, it doesn't guess. */
    val maxAccuracyM: Float = 30f,
    /** Under this speed counts as stopped (walking to the door still counts). */
    val stoppedKmh: Double = 7.0,
    /** The vehicle must have driven at least this fast while heading to this stop before it can arrive. */
    val drivingKmh: Double = 15.0,
) {
    companion object {
        const val DEFAULT_RADIUS_M = 75
        val RADIUS_CHOICES = listOf(50, 75, 100)
    }
}

sealed interface ArrivalState {
    /** Nothing to watch, or the reading was too poor to use. */
    data object Idle : ArrivalState
    /** Watching [stopId]; [metres] away. Not yet seen driving towards it. */
    data class Heading(val stopId: Long, val metres: Int) : ArrivalState
    /** Stopped inside the zone; auto-completes in [secondsLeft] unless the vehicle moves. */
    data class Waiting(val stopId: Long, val secondsLeft: Int) : ArrivalState
    /** Stopped inside the zone for the full time: complete [stopId] now. Reported once per arrival. */
    data class Arrived(val stopId: Long) : ArrivalState
}

/**
 * Decides when the vehicle has arrived at the next stop: inside the zone AND stopped for the full dwell time.
 *
 * Safety rules, so it never fires before you get there:
 * - Only the next stop (top of the list) is watched, so a stop next door can't fire.
 * - A stop can only arrive after the vehicle was seen driving (≥ 15 km/h) while it was the next stop.
 *   Starting the app while parked, or two stops on the same street without moving the truck, needs a tap.
 * - Poor GPS readings are skipped. Leaving the zone or moving faster than 7 km/h resets the clock to zero.
 * - After a stop arrives it is never auto-completed again until the vehicle leaves its zone (so Undo sticks).
 */
class ArrivalDetector(var rules: ArrivalRules = ArrivalRules()) {
    private var targetId: Long? = null
    private var drove = false
    private var dwellStart: Long? = null
    private var lastGood: Fix? = null
    /** Stop that already arrived; ignored until a good reading outside its zone. */
    private var firedId: Long? = null

    fun reset() {
        targetId = null
        drove = false
        dwellStart = null
        lastGood = null
        firedId = null
    }

    fun onFix(next: Stop?, fix: Fix): ArrivalState {
        if (next == null || next.isDone) {
            targetId = null
            return ArrivalState.Idle
        }
        if (next.id != targetId) {
            targetId = next.id
            drove = false
            dwellStart = null
        }
        val acc = fix.accuracyM
        if (acc == null || acc > rules.maxAccuracyM) return ArrivalState.Idle

        val speedKmh = speedKmh(fix)
        lastGood = fix
        val metres = Geo.haversineMeters(fix.at, next.location)
        val inside = metres <= rules.radiusM

        if (!inside && firedId == next.id) firedId = null
        if (speedKmh != null && speedKmh >= rules.drivingKmh) drove = true

        val stopped = speedKmh != null && speedKmh < rules.stoppedKmh
        if (!inside || !stopped || !drove || firedId == next.id) {
            dwellStart = null
            return ArrivalState.Heading(next.id, metres.toInt())
        }
        val start = dwellStart ?: fix.timeMillis.also { dwellStart = it }
        val waited = fix.timeMillis - start
        if (waited >= rules.dwellMillis) {
            firedId = next.id
            dwellStart = null
            drove = false
            return ArrivalState.Arrived(next.id)
        }
        return ArrivalState.Waiting(next.id, ((rules.dwellMillis - waited + 999) / 1000).toInt())
    }

    /** Speed from the phone if it gave one, otherwise worked out from the last good reading. */
    private fun speedKmh(fix: Fix): Double? {
        fix.speedMps?.let { return it * 3.6 }
        val prev = lastGood ?: return null
        val dt = (fix.timeMillis - prev.timeMillis) / 1000.0
        if (dt <= 0.5) return null
        return Geo.haversineMeters(prev.at, fix.at) / dt * 3.6
    }
}
