package app.truckie.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Road travel times from OSRM (router.project-osrm.org). No key needed; the public server is
 * for testing, so v0.1 falls back to straight-line estimates whenever it fails.
 */
object Osrm {
    fun tableUrl(points: List<LatLng>, base: String = "https://router.project-osrm.org"): String =
        "$base/table/v1/driving/" + points.joinToString(";") { p ->
            String.format(java.util.Locale.US, "%.6f,%.6f", p.lng, p.lat)
        } + "?annotations=duration"

    /** Parses a table response into a seconds grid. Missing cells are filled with straight-line estimates. */
    fun parseTable(json: String, points: List<LatLng>): Array<DoubleArray> {
        val res = JSON.decodeFromString(Table.serializer(), json)
        require(res.code == "Ok") { "OSRM answered ${res.code}" }
        val d = res.durations ?: error("OSRM sent no durations")
        require(d.size == points.size && d.all { it.size == points.size }) { "OSRM grid has the wrong size" }
        return Array(points.size) { i ->
            DoubleArray(points.size) { j -> d[i][j] ?: if (i == j) 0.0 else Geo.estimateDriveSeconds(points[i], points[j]) }
        }
    }

    @Serializable
    private data class Table(val code: String = "", val durations: List<List<Double?>>? = null)

    private val JSON = Json { ignoreUnknownKeys = true }
}
