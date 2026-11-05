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
        assertTrue(Updates.isExperimental("0.6.0a+27"))
        assertTrue(Updates.isExperimental("v0.6.0c"))
        assertFalse(Updates.isExperimental("0.5.2"))
        assertEquals(27, Updates.expBuild("0.6.0a+27"))
        assertNull(Updates.expBuild("0.5.2"))
        assertEquals(0, Updates.compareVersions("0.6.0a+27", "v0.6.0"))
    }

    @Test fun fullOrdering() {
        val sorted = listOf("0.6.1a+40", "0.6.0", "0.6.0b+31", "0.6.0a+30", "0.6.0a+27", "0.5.2")
            .sortedWith { a, b -> Updates.compareFull(a, b) }
        assertEquals(listOf("0.5.2", "0.6.0a+27", "0.6.0a+30", "0.6.0b+31", "0.6.0", "0.6.1a+40"), sorted)
    }

    @Test fun experimentalGetsNewerBuild() {
        val json = list(rel("v0.6.0a+30", true), rel("v0.6.0a+27", true), rel("v0.5.2", false), rel("latest", true))
        assertEquals("v0.6.0a+30", Updates.newestForExperimental(json, "0.6.0a+27")?.tag)
        assertNull(Updates.newestForExperimental(json, "0.6.0a+30"))
    }

    @Test fun experimentalGetsBetaAndStable() {
        val beta = list(rel("v0.6.0b+31", true), rel("v0.6.0a+30", true))
        assertEquals("v0.6.0b+31", Updates.newestForExperimental(beta, "0.6.0a+30")?.tag)
        val json = list(rel("v0.6.0", false), rel("v0.6.0c+35", true), rel("v0.5.2", false))
        assertEquals("v0.6.0", Updates.newestForExperimental(json, "0.6.0c+35")?.tag)
        // Already moved on to 0.7.0 alpha: the 0.6.0 stable release is older.
        assertNull(Updates.newestForExperimental(json, "0.7.0a+36"))
    }

    @Test fun skipsReleasesWithoutApk() {
        val json = list(rel("v0.6.0a+31", true, apk = false), rel("v0.6.0a+30", true))
        assertEquals("v0.6.0a+30", Updates.newestForExperimental(json, "0.6.0a+27")?.tag)
    }

    @Test fun stableChannelIgnoresPreReleases() {
        // /releases/latest never returns a pre-release, and latest() rejects one anyway.
        assertNull(Updates.latest(rel("v0.6.0a+30", true)))
    }
}
