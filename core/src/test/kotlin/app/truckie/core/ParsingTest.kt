package app.truckie.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParsingTest {
    private fun res(name: String) = javaClass.classLoader.getResource(name)!!.readText()

    @Test fun coordinatesFromText() {
        assertEquals(LatLng(-33.86, 151.21), Geo.parseCoordinates("-33.86, 151.21"))
        assertEquals(LatLng(-33.86, 151.21), Geo.parseCoordinates("geo:-33.86,151.21?q=x"))
        assertEquals(LatLng(-33.86, 151.21), Geo.parseCoordinates("https://maps.google.com/?q=-33.86,151.21"))
        assertEquals(LatLng(-33.86, 151.21), Geo.parseCoordinates("https://www.google.com/maps/place/X/@-33.86,151.21,17z"))
        assertNull(Geo.parseCoordinates("12 Smith St, Newtown"))
        assertNull(Geo.parseCoordinates("123, 456"))
    }

    @Test fun navigationLinksUseDotDecimals() {
        java.util.Locale.setDefault(java.util.Locale.GERMANY)
        try {
            val p = LatLng(-33.8688, 151.2093)
            assertEquals("google.navigation:q=-33.868800,151.209300&mode=d", NavApp.GOOGLE_MAPS.navigationUri(p))
            assertEquals("https://waze.com/ul?ll=-33.868800,151.209300&navigate=yes", NavApp.WAZE.navigationUri(p))
            assertEquals(
                "https://router.project-osrm.org/table/v1/driving/151.209300,-33.868800?annotations=duration",
                Osrm.tableUrl(listOf(p)),
            )
        } finally {
            java.util.Locale.setDefault(java.util.Locale.US)
        }
    }

    @Test fun navigateByAddressNotCoordinates() {
        // Coordinates from the lookup point at the suburb centre; the address must win.
        val stop = Stop(1, "12 King Street, Newtown NSW 2042", LatLng(-33.89, 151.18))
        assertEquals("google.navigation:q=12%20King%20Street%2C%20Newtown%20NSW%202042&mode=d", NavApp.GOOGLE_MAPS.navigationUri(stop))
        assertEquals("https://waze.com/ul?q=12%20King%20Street%2C%20Newtown%20NSW%202042&navigate=yes", NavApp.WAZE.navigationUri(stop))
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=12%20King%20Street%2C%20Newtown%20NSW%202042&travelmode=driving",
            NavApp.GOOGLE_MAPS.webUri(stop),
        )
        assertEquals("geo:0,0?q=12%20King%20Street%2C%20Newtown%20NSW%202042", NavApp.carGeoUri(stop))
    }

    @Test fun stopsTypedAsCoordinatesStillUseCoordinates() {
        val stop = Stop(1, "-33.8688, 151.2093", LatLng(-33.8688, 151.2093))
        assertEquals("google.navigation:q=-33.868800,151.209300&mode=d", NavApp.GOOGLE_MAPS.navigationUri(stop))
        assertEquals("geo:-33.868800,151.209300", NavApp.carGeoUri(stop))
    }

    @Test fun osrmTable() {
        val pts = listOf(LatLng(-33.0, 151.0), LatLng(-33.0, 151.1), LatLng(-33.1, 151.0))
        val m = Osrm.parseTable(res("osrm_table.json"), pts)
        assertEquals(600.5, m[0][1])
        assertEquals(0.0, m[1][1])
        assertTrue(m[2][1] > 0) // null cell filled with an estimate
    }

    @Test fun osrmDistancesFromFirst() {
        val pts = listOf(LatLng(-33.0, 151.0), LatLng(-33.0, 151.1), LatLng(-33.1, 151.0))
        assertTrue(Osrm.distancesFromFirstUrl(pts).endsWith("?sources=0&annotations=distance"))
        val d = Osrm.parseDistancesFromFirst(res("osrm_distances.json"), pts)
        assertEquals(0.0, d[0])
        assertEquals(12000.5, d[1])
        assertTrue(d[2] > 10000) // null cell filled from straight line
    }

    @Test fun updateCheck() {
        val json = res("github_release.json")
        val r = assertNotNull(Updates.newerRelease(json, "0.1"))
        assertEquals("v0.2", r.tag)
        assertEquals("truckie-v0.2.apk", r.apkName)
        assertNull(Updates.newerRelease(json, "0.2"))
        assertNull(Updates.newerRelease(json, "0.10"))
        assertTrue(Updates.compareVersions("v0.1.1", "0.1") > 0)
        assertEquals(0, Updates.compareVersions("v0.1", "0.1.0"))
        assertTrue(Updates.compareVersions("0.9", "0.10") < 0)
    }

    @Test fun photon() {
        val places = Photon.parse(res("photon.json"))
        assertEquals(1, places.size)
        assertEquals("12 King Street, Newtown 2042, New South Wales", places[0].label)
        assertEquals(LatLng(-33.8968, 151.1793), places[0].location)
    }
}
