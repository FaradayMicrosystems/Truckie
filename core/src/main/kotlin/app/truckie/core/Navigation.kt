package app.truckie.core

import java.net.URLEncoder

/** The app that drives you to a stop. Truckie hands over one stop at a time. */
enum class NavApp(val label: String, val packageName: String) {
    GOOGLE_MAPS("Google Maps", "com.google.android.apps.maps"),
    WAZE("Waze", "com.waze");

    /**
     * Link that opens the app already navigating to the stop. It sends the stop's address text,
     * so Google Maps / Waze find the address themselves. The coordinates Truckie looked up can
     * land on the middle of a suburb when the lookup misses the house number, so they are only
     * used for stops that were entered as coordinates.
     */
    fun navigationUri(stop: Stop): String {
        val q = destinationQuery(stop)
        return when (this) {
            GOOGLE_MAPS -> "google.navigation:q=$q&mode=d"
            WAZE -> "https://waze.com/ul?q=$q&navigate=yes"
        }
    }

    /** Fallback when the app is not installed: opens directions in a browser. */
    fun webUri(stop: Stop): String {
        val q = destinationQuery(stop)
        return when (this) {
            GOOGLE_MAPS -> "https://www.google.com/maps/dir/?api=1&destination=$q&travelmode=driving"
            WAZE -> "https://waze.com/ul?q=$q&navigate=yes"
        }
    }

    /** Link that opens the app navigating to a point (used when only coordinates are known). */
    fun navigationUri(to: LatLng): String = when (this) {
        GOOGLE_MAPS -> "google.navigation:q=${to.toQuery()}&mode=d"
        WAZE -> "https://waze.com/ul?ll=${to.toQuery()}&navigate=yes"
    }

    companion object {
        /**
         * URL-encoded destination: the address text, or "lat,lng" when the stop was typed or
         * pasted as coordinates (its address text then parses as coordinates).
         */
        fun destinationQuery(stop: Stop): String =
            if (Geo.parseCoordinates(stop.address) != null || stop.address.isBlank()) stop.location.toQuery()
            else encode(stop.address)

        /** Percent-encoding with %20 for spaces (some nav apps show a literal '+'). */
        fun encode(text: String): String = URLEncoder.encode(text.trim(), "UTF-8").replace("+", "%20")

        /** Android Auto: "geo:0,0?q=<address>" makes the car's nav app search the address. */
        fun carGeoUri(stop: Stop): String =
            if (Geo.parseCoordinates(stop.address) != null || stop.address.isBlank()) "geo:${stop.location.toQuery()}"
            else "geo:0,0?q=${encode(stop.address)}"
    }
}
