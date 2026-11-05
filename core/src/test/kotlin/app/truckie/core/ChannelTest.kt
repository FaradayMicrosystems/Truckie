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
        assertTrue(Updates.isExperimental("0.6-exp.27"))
        assertFalse(Updates.isExperimental("0.5.2"))
        assertEquals(27, Updates.expBuild("0.6-exp.27"))
        assertEquals(31, Updates.expBuild("v0.6-exp.31"))
        assertNull(Updates.expBuild("0.5.2"))
    }

    @Test fun experimentalGetsNewerBuild() {
        val json = list(rel("v0.6-exp.30", true), rel("v0.6-exp.27", true), rel("v0.5.2", false), rel("latest", true))
        assertEquals("v0.6-exp.30", Updates.newestForExperimental(json, "0.6-exp.27")?.tag)
        assertNull(Updates.newestForExperimental(json, "0.6-exp.30"))
    }

    @Test fun experimentalGetsStableOfSameVersion() {
        val json = list(rel("v0.6", false), rel("v0.6-exp.30", true), rel("v0.5.2", false))
        assertEquals("v0.6", Updates.newestForExperimental(json, "0.6-exp.30")?.tag)
        // Already moved on to 0.7 experimental: the 0.6 stable release is older.
        assertNull(Updates.newestForExperimental(json, "0.7-exp.31"))
    }

    @Test fun skipsReleasesWithoutApk() {
        val json = list(rel("v0.6-exp.31", true, apk = false), rel("v0.6-exp.30", true))
        assertEquals("v0.6-exp.30", Updates.newestForExperimental(json, "0.6-exp.27")?.tag)
    }

    @Test fun stableChannelIgnoresPreReleases() {
        // /releases/latest never returns a pre-release, and latest() rejects one anyway.
        assertNull(Updates.latest(rel("v0.6-exp.30", true)))
    }
}
