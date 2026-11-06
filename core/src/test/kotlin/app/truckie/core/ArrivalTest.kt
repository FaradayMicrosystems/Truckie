package app.truckie.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ArrivalTest {
    private val home = LatLng(-33.8688, 151.2093)
    private val stop = Stop(1, "1 Test St", home)

    /** A point [m] metres north of the stop (1 degree of latitude ≈ 111,195 m). */
    private fun north(m: Double) = LatLng(home.lat + m / 111_195.0, home.lng)

    private fun fix(m: Double, sec: Long, kmh: Double?, acc: Float = 10f) =
        Fix(north(m), sec * 1000, acc, kmh?.let { (it / 3.6).toFloat() })

    /** Drives in from 1 km away, then stops [m] metres from the stop. Returns the detector, now at t=100 s. */
    private fun arriveAndStop(d: ArrivalDetector, m: Double = 20.0): ArrivalDetector {
        d.onFix(stop, fix(1000.0, 0, 50.0))
        d.onFix(stop, fix(300.0, 60, 40.0))
        d.onFix(stop, fix(m, 100, 0.0))
        return d
    }

    @Test fun firesAfterTwoMinutesStoppedInside() {
        val d = arriveAndStop(ArrivalDetector())
        assertEquals(ArrivalState.Waiting(1, 120), d.onFix(stop, fix(20.0, 100, 0.0)))
        assertEquals(ArrivalState.Waiting(1, 1), d.onFix(stop, fix(20.0, 219, 2.0)))
        assertEquals(ArrivalState.Arrived(1), d.onFix(stop, fix(20.0, 220, 0.0)))
    }

    @Test fun firesOnlyOnce() {
        val d = arriveAndStop(ArrivalDetector())
        assertEquals(ArrivalState.Arrived(1), d.onFix(stop, fix(20.0, 220, 0.0)))
        // Undo restores the stop: still parked there, so it must not fire again.
        assertIs<ArrivalState.Heading>(d.onFix(stop, fix(20.0, 400, 0.0)))
        assertIs<ArrivalState.Heading>(d.onFix(stop, fix(20.0, 600, 0.0)))
    }

    @Test fun headingSaysWhetherInsideTheZone() {
        // Drives for the GPS indicator: yellow outside, green inside.
        val d = ArrivalDetector()
        assertEquals(false, (d.onFix(stop, fix(1000.0, 0, 50.0)) as ArrivalState.Heading).inside)
        assertEquals(false, (d.onFix(stop, fix(300.0, 10, 50.0)) as ArrivalState.Heading).inside)
        assertEquals(true, (d.onFix(stop, fix(200.0, 20, 50.0)) as ArrivalState.Heading).inside) // inside but still moving
    }

    @Test fun neverFiresOutsideTheZone() {
        val d = ArrivalDetector()
        d.onFix(stop, fix(1000.0, 0, 50.0))
        // Parked 300 m away (outside the 250 m default) for 10 minutes.
        for (t in 60L..660L step 10) assertIs<ArrivalState.Heading>(d.onFix(stop, fix(300.0, t, 0.0)))
    }

    @Test fun drivingPastNeverFires() {
        val d = ArrivalDetector()
        d.onFix(stop, fix(1000.0, 0, 50.0))
        for (t in 1L..300L) assertIs<ArrivalState.Heading>(d.onFix(stop, fix(10.0, t, 40.0)))
    }

    @Test fun movingResetsTheClock() {
        val d = arriveAndStop(ArrivalDetector())
        d.onFix(stop, fix(20.0, 200, 0.0))
        assertIs<ArrivalState.Heading>(d.onFix(stop, fix(30.0, 210, 20.0)))
        assertEquals(ArrivalState.Waiting(1, 120), d.onFix(stop, fix(20.0, 220, 0.0)))
        assertIs<ArrivalState.Waiting>(d.onFix(stop, fix(20.0, 339, 0.0)))
        assertEquals(ArrivalState.Arrived(1), d.onFix(stop, fix(20.0, 340, 0.0)))
    }

    @Test fun poorAccuracyIsIgnored() {
        val d = arriveAndStop(ArrivalDetector())
        // ±50 m readings neither count nor reset.
        assertEquals(ArrivalState.Idle, d.onFix(stop, fix(20.0, 150, 0.0, acc = 50f)))
        assertEquals(ArrivalState.Idle, d.onFix(stop, fix(500.0, 160, 60.0, acc = 50f)))
        assertEquals(ArrivalState.Arrived(1), d.onFix(stop, fix(20.0, 220, 0.0)))
    }

    @Test fun parkedAtStartNeedsATap() {
        // App opened while already parked at the stop: never seen driving, so it doesn't fire.
        val d = ArrivalDetector()
        for (t in 0L..600L step 10) assertIs<ArrivalState.Heading>(d.onFix(stop, fix(10.0, t, 0.0)))
    }

    @Test fun nextDoorStopNeedsTheTruckToMove() {
        val d = arriveAndStop(ArrivalDetector())
        assertEquals(ArrivalState.Arrived(1), d.onFix(stop, fix(20.0, 220, 0.0)))
        val nextDoor = Stop(2, "3 Test St", north(30.0))
        // Still parked: the next stop 30 m away must not auto-complete.
        for (t in 230L..800L step 10) assertIs<ArrivalState.Heading>(d.onFix(nextDoor, fix(20.0, t, 0.0)))
    }

    @Test fun speedWorkedOutWhenPhoneGivesNone() {
        val d = ArrivalDetector()
        d.onFix(stop, fix(1000.0, 0, null))
        d.onFix(stop, fix(500.0, 30, null)) // 500 m in 30 s = 60 km/h
        d.onFix(stop, fix(20.0, 60, null))  // 480 m in 30 s: still driving
        assertEquals(ArrivalState.Waiting(1, 120), d.onFix(stop, fix(20.0, 70, null)))
        assertEquals(ArrivalState.Arrived(1), d.onFix(stop, fix(21.0, 190, null)))
    }

    @Test fun radiusSettingIsUsed() {
        val d = ArrivalDetector(ArrivalRules(radiusM = 50))
        arriveAndStop(d, m = 60.0)
        assertIs<ArrivalState.Heading>(d.onFix(stop, fix(60.0, 300, 0.0)))
    }

    @Test fun doneOrMissingStopIsIdle() {
        val d = ArrivalDetector()
        assertEquals(ArrivalState.Idle, d.onFix(null, fix(0.0, 0, 0.0)))
        assertEquals(ArrivalState.Idle, d.onFix(stop.copy(doneAt = 1), fix(0.0, 0, 0.0)))
    }

    @Test fun stopTimerIsAdjustable() {
        val d = ArrivalDetector(ArrivalRules(dwellMillis = 60_000))
        d.onFix(stop, fix(1000.0, 0, 50.0))
        d.onFix(stop, fix(300.0, 60, 40.0))
        assertEquals(ArrivalState.Waiting(1, 60), d.onFix(stop, fix(20.0, 100, 0.0)))
        assertEquals(ArrivalState.Arrived(1), d.onFix(stop, fix(20.0, 160, 0.0)))
        assertEquals(listOf("1:00", "1:30", "2:00", "2:30", "3:00"), ArrivalRules.DWELL_CHOICES_SECONDS.map { ArrivalRules.mmss(it) })
    }
}
