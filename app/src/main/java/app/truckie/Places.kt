package app.truckie

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import app.truckie.core.Geo
import app.truckie.core.LatLng
import app.truckie.core.Photon
import app.truckie.core.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Turns typed text into places, and finds where the phone is. */
class Places(private val context: Context) {

    /**
     * Coordinates are used as typed. Otherwise the phone's own geocoder (Google's on most phones,
     * free, no key) is asked first and Photon (OpenStreetMap) second.
     */
    suspend fun search(text: String, near: LatLng?): List<Place> {
        Geo.parseCoordinates(text)?.let { return listOf(Place(text.trim(), it)) }
        val fromPhone = runCatching { phoneGeocoder(text) }.getOrDefault(emptyList())
        if (fromPhone.isNotEmpty()) return fromPhone
        return Photon.parse(Http.get(Photon.searchUrl(text, near)))
    }

    @Suppress("DEPRECATION") // The listener version needs API 33; this runs off the main thread.
    private suspend fun phoneGeocoder(text: String): List<Place> = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext emptyList()
        Geocoder(context).getFromLocationName(text, 5).orEmpty().map { a ->
            val label = a.getAddressLine(0) ?: listOfNotNull(a.featureName, a.locality).joinToString(", ")
            Place(label, LatLng(a.latitude, a.longitude))
        }.distinctBy { it.label }
    }

    /** Newest last-known position from any provider, or null without permission or a fix. */
    fun here(): LatLng? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val best: Location? = runCatching {
            lm.getProviders(true).mapNotNull { p ->
                try { lm.getLastKnownLocation(p) } catch (_: SecurityException) { null }
            }.maxByOrNull { it.time }
        }.getOrNull()
        return best?.let { LatLng(it.latitude, it.longitude) }
    }
}
