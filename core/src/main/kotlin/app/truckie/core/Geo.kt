package app.truckie.core

import kotlinx.serialization.Serializable
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@Serializable
data class LatLng(val lat: Double, val lng: Double) {
    /** "lat,lng" with a dot decimal separator, whatever the phone's language. */
    fun toQuery(): String = "${fmt(lat)},${fmt(lng)}"

    private fun fmt(v: Double) = String.format(java.util.Locale.US, "%.6f", v)
}

object Geo {
    private const val EARTH_RADIUS_M = 6_371_000.0

    /** Straight-line distance in metres. */
    fun haversineMeters(a: LatLng, b: LatLng): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h))
    }

    /**
     * Rough drive time in seconds when no road data is available:
     * roads are about 1.3 times longer than a straight line, at an average of 40 km/h.
     */
    fun estimateDriveSeconds(a: LatLng, b: LatLng): Double = haversineMeters(a, b) * 1.3 / (40_000.0 / 3600.0)

    /** Travel-time grid from straight-line estimates. Used offline or when the road service fails. */
    fun estimateMatrix(points: List<LatLng>): Array<DoubleArray> =
        Array(points.size) { i -> DoubleArray(points.size) { j -> if (i == j) 0.0 else estimateDriveSeconds(points[i], points[j]) } }

    private val PAIR = Regex("""(-?\d{1,2}(?:\.\d+)?)\s*,\s*(-?\d{1,3}(?:\.\d+)?)""")

    /**
     * Reads coordinates typed or pasted as text: "-33.86, 151.21", "geo:-33.86,151.21",
     * or a Google Maps link containing "q=-33.86,151.21", "ll=..." or "@-33.86,151.21".
     * Returns null when the text is an address rather than coordinates.
     */
    fun parseCoordinates(text: String): LatLng? {
        val t = text.trim()
        val candidates = listOfNotNull(
            Regex("""^geo:([^?;]+)""").find(t)?.groupValues?.get(1),
            Regex("""[?&](?:q|ll|daddr|destination)=([^&]+)""").find(t)?.groupValues?.get(1)?.replace("%2C", ","),
            Regex("""@(-?[\d.]+,-?[\d.]+)""").find(t)?.groupValues?.get(1),
            t,
        )
        for (c in candidates) {
            val m = PAIR.matchEntire(c.trim()) ?: continue
            val lat = m.groupValues[1].toDouble()
            val lng = m.groupValues[2].toDouble()
            if (lat in -90.0..90.0 && lng in -180.0..180.0) return LatLng(lat, lng)
        }
        return null
    }
}
