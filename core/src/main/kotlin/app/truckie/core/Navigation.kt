package app.truckie.core

/** The app that drives you to a stop. Truckie hands over one stop at a time. */
enum class NavApp(val label: String, val packageName: String) {
    GOOGLE_MAPS("Google Maps", "com.google.android.apps.maps"),
    WAZE("Waze", "com.waze");

    /** Link that opens the app already navigating to the point. */
    fun navigationUri(to: LatLng): String = when (this) {
        GOOGLE_MAPS -> "google.navigation:q=${to.toQuery()}&mode=d"
        WAZE -> "https://waze.com/ul?ll=${to.toQuery()}&navigate=yes"
    }

    /** Fallback when the app is not installed: opens directions in a browser. */
    fun webUri(to: LatLng): String = when (this) {
        GOOGLE_MAPS -> "https://www.google.com/maps/dir/?api=1&destination=${to.toQuery()}&travelmode=driving"
        WAZE -> "https://waze.com/ul?ll=${to.toQuery()}&navigate=yes"
    }
}
