package app.truckie.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder

data class Place(val label: String, val location: LatLng)

/**
 * Address search through Photon (photon.komoot.io, OpenStreetMap data, no key).
 * Truckie uses the phone's built-in geocoder first and Photon only when that finds nothing.
 */
object Photon {
    fun searchUrl(query: String, near: LatLng? = null, limit: Int = 5): String =
        "https://photon.komoot.io/api/?limit=$limit&q=" + URLEncoder.encode(query, "UTF-8") +
            (near?.let { String.format(java.util.Locale.US, "&lat=%.5f&lon=%.5f", it.lat, it.lng) } ?: "")

    fun parse(json: String): List<Place> =
        JSON.decodeFromString(Response.serializer(), json).features.mapNotNull { f ->
            val c = f.geometry.coordinates
            if (c.size < 2) return@mapNotNull null
            val label = f.properties.label() ?: return@mapNotNull null
            Place(label, LatLng(c[1], c[0]))
        }.distinctBy { it.label }

    @Serializable
    private data class Response(val features: List<Feature> = emptyList())

    @Serializable
    private data class Feature(val geometry: Geometry, val properties: Properties = Properties())

    @Serializable
    private data class Geometry(val coordinates: List<Double> = emptyList())

    @Serializable
    private data class Properties(
        val name: String? = null,
        val housenumber: String? = null,
        val street: String? = null,
        val city: String? = null,
        val postcode: String? = null,
        val state: String? = null,
    ) {
        /** "12 Smith St, Suburb 2000" style label, blanks and repeats left out. */
        fun label(): String? {
            val streetLine = listOfNotNull(housenumber, street).joinToString(" ").ifBlank { null }
            val cityLine = listOfNotNull(city, postcode).joinToString(" ").ifBlank { null }
            return listOfNotNull(name?.takeIf { it != street }, streetLine, cityLine, state)
                .filter { it.isNotBlank() }.distinct().joinToString(", ").ifBlank { null }
        }
    }

    private val JSON = Json { ignoreUnknownKeys = true }
}
