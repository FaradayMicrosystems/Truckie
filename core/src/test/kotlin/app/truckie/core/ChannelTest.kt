package app.truckie.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChannelTest {
    private fun rel(tag: String, pre: Boolean, apk: Boolean = true) = """
        {"tag_name":"$tag","name":"Truckie $tag","body":"notes","draft":false,"prerelease":$pre,
         "assets":[${if (apk) """{"name":"truckie-$tag.apk","url":"u/$tag","browser_download_url":"b/$tag"}""" else ""}]}
    """.trimIndent()

    private fun list(vararg r: String) = "[" + r.joinToString(",") + "]"

    @Test fun versionNames() {
        assertTrue(Updates.isExperimental("0.5.3a+27"))
        assertTrue(Updates.isExperimental("v0.5.3c"))
        assertFalse(Updates.isExperimental("0.5.2"))
        assertEquals(27, Updates.expBuild("0.5.3a+27"))
        assertNull(Updates.expBuild("0.5.2"))
        assertEquals(0, Updates.compareVersions("0.5.3a+27", "v0.5.3"))
    }

    @Test fun fullOrdering() {
        val sorted = listOf("0.5.4a+40", "0.5.3", "0.5.3b+31", "0.5.3a+30", "0.5.3a+27", "0.5.2")
            .sortedWith { a, b -> Updates.compareFull(a, b) }
        assertEquals(listOf("0.5.2", "0.5.3a+27", "0.5.3a+30", "0.5.3b+31", "0.5.3", "0.5.4a+40"), sorted)
    }

    @Test fun experimentalGetsNewerBuild() {
        val json = list(rel("v0.5.3a+30", true), rel("v0.5.3a+27", true), rel("v0.5.2", false), rel("latest", true))
        assertEquals("v0.5.3a+30", Updates.newestForExperimental(json, "0.5.3a+27")?.tag)
        assertNull(Updates.newestForExperimental(json, "0.5.3a+30"))
    }

    @Test fun experimentalGetsBetaAndStable() {
        val beta = list(rel("v0.5.3b+31", true), rel("v0.5.3a+30", true))
        assertEquals("v0.5.3b+31", Updates.newestForExperimental(beta, "0.5.3a+30")?.tag)
        val json = list(rel("v0.5.3", false), rel("v0.5.3c+35", true), rel("v0.5.2", false))
        assertEquals("v0.5.3", Updates.newestForExperimental(json, "0.5.3c+35")?.tag)
        // Already moved on to 0.5.4 alpha: the 0.5.3 stable release is older.
        assertNull(Updates.newestForExperimental(json, "0.5.4a+36"))
    }

    @Test fun legacyExpBuildStillUpdates() {
        // v0.6-exp.27 shipped before the X.Y.Z rule (it was really 0.5.3 alpha); it must still
        // find the 0.5.3a builds, and never be offered v0.5.2 (an older build Android won't install).
        assertTrue(Updates.isExperimental("0.6-exp.27"))
        val json = list(rel("v0.5.3a+28", true), rel("v0.6-exp.27", true), rel("v0.5.2", false))
        assertEquals("v0.5.3a+28", Updates.newestForExperimental(json, "0.6-exp.27")?.tag)
        assertNull(Updates.newestForExperimental(json, "0.5.3a+28"))
        assertNull(Updates.newestForExperimental(list(rel("v0.5.2", false)), "0.6-exp.27"))
        assertEquals("v0.5.3", Updates.newestForExperimental(list(rel("v0.5.3", false)), "0.6-exp.27")?.tag)
    }

    @Test fun skipsReleasesWithoutApk() {
        val json = list(rel("v0.5.3a+31", true, apk = false), rel("v0.5.3a+30", true))
        assertEquals("v0.5.3a+30", Updates.newestForExperimental(json, "0.5.3a+27")?.tag)
    }

    @Test fun stableChannelIgnoresPreReleases() {
        // /releases/latest never returns a pre-release, and latest() rejects one anyway.
        assertNull(Updates.latest(rel("v0.5.3a+30", true)))
    }

    @Test
    fun experimentalLetterE() {
        assertTrue(Updates.isExperimental("0.5.3e+40"))
        assertEquals(40, Updates.expBuild("v0.5.3e+40"))
        // An old alpha install is offered the new e build; e sits below the stable release.
        val json = list(rel("v0.5.3e+40", true), rel("v0.5.3a+39", true), rel("v0.5.2", false))
        assertEquals("v0.5.3e+40", Updates.newestForExperimental(json, "0.5.3a+39")?.tag)
        assertTrue(Updates.compareFull("0.5.3e+40", "0.5.3") < 0)
        assertTrue(Updates.compareFull("0.5.3a+39", "0.5.3e+40") < 0)
    }
}
