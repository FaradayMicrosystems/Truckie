package app.truckie.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class Release(val tag: String, val name: String, val notes: String, val apkName: String, val apkApiUrl: String, val apkBrowserUrl: String)

/** Checks the repo's latest GitHub Release against the installed version. */
object Updates {
    fun latestReleaseUrl(repo: String) = "https://api.github.com/repos/$repo/releases/latest"

    /** Newest releases first, pre-releases included (the experimental channel reads this). */
    fun releasesUrl(repo: String) = "https://api.github.com/repos/$repo/releases?per_page=30"

    /**
     * Aurora's versions: X.Y.Z (X primary releases, Y user-visible changes and features, Z minor
     * fixes and looks) plus an optional letter: a alpha, b beta, c release candidate. Builds from the
     * experimental branch carry a letter and a build counter after "+", like "0.6.0a+27".
     */
    fun isExperimental(version: String) = PRE.containsMatchIn(version.trim()) || version.contains("-exp")

    /** The build counter after "+" ("0.6.0a+27" is 27), or null. */
    fun expBuild(version: String): Int? = BUILD.find(version.trim())?.groupValues?.get(1)?.toIntOrNull()

    /**
     * Full ordering: numbers first, then a < b < c < stable at the same numbers, then the build counter.
     * So 0.6.0a+27 < 0.6.0a+30 < 0.6.0b+31 < 0.6.0 < 0.6.1a+40.
     */
    fun compareFull(a: String, b: String): Int {
        val byNumbers = compareVersions(a, b)
        if (byNumbers != 0) return byNumbers
        val byStage = stage(a).compareTo(stage(b))
        if (byStage != 0) return byStage
        return (expBuild(a) ?: 0).compareTo(expBuild(b) ?: 0)
    }

    /**
     * Experimental channel: the newest release worth offering to [currentVersion], or null. Newer
     * a/b/c builds count, and so does the stable release once it ships (0.6.0 beats 0.6.0c+35).
     * Stable installs never call this, so they never see experimental builds.
     */
    fun newestForExperimental(json: String, currentVersion: String): Release? =
        JSON.decodeFromString(ListSerializer(GhRelease.serializer()), json).mapNotNull { r ->
            if (r.draft) return@mapNotNull null
            if (r.prerelease && !isExperimental(r.tagName)) return@mapNotNull null // e.g. the old rolling "latest" build
            val apk = r.assets.firstOrNull { it.name.endsWith(".apk") } ?: return@mapNotNull null
            if (compareFull(r.tagName, currentVersion) <= 0) return@mapNotNull null
            Release(r.tagName, r.name ?: r.tagName, r.body.orEmpty(), apk.name, apk.url, apk.browserDownloadUrl)
        }.maxWithOrNull { a, b -> compareFull(a.tag, b.tag) }

    /** Legacy "0.6-exp.27" (the first experimental build) = 0, a = 1, b = 2, c = 3, no letter (stable) = 4. */
    private fun stage(v: String): Int = when (PRE.find(v.trim())?.groupValues?.get(1)) {
        "a" -> 1; "b" -> 2; "c" -> 3
        else -> if (v.contains("-exp")) 0 else 4
    }

    private val PRE = Regex("""^v?\d+(?:\.\d+)*([abc])(?:\+\d+)?$""")
    private val BUILD = Regex("""\+(\d+)$""")

    /** Returns the release when it is newer than [currentVersion] and carries an APK, else null. */
    fun newerRelease(json: String, currentVersion: String): Release? =
        latest(json)?.takeIf { compareVersions(it.tag, currentVersion) > 0 }

    /** The published release in a /releases/latest response, or null if it's a draft, pre-release or has no APK. */
    fun latest(json: String): Release? {
        val r = JSON.decodeFromString(GhRelease.serializer(), json)
        if (r.draft || r.prerelease) return null
        val apk = r.assets.firstOrNull { it.name.endsWith(".apk") } ?: return null
        return Release(r.tagName, r.name ?: r.tagName, r.body.orEmpty(), apk.name, apk.url, apk.browserDownloadUrl)
    }

    /** Plain-words reason for a failed check, from the HTTP status GitHub answered (null = no answer at all). */
    fun failureReason(httpStatus: Int?, hasToken: Boolean): String = when (httpStatus) {
        null -> "Couldn't reach GitHub. Check your signal and try again."
        401 -> "GitHub rejected the update token (expired or revoked). Make a new one and save it as the UPDATE_TOKEN secret; the next build picks it up."
        404 -> if (hasToken) "GitHub won't show Truckie's releases to this build's token. Check the token has Contents: Read-only access to the truckie repo."
               else "This build has no update token, and the repo is private, so GitHub hides its releases."
        403, 429 -> "GitHub is limiting requests right now. Try again in a few minutes."
        else -> "GitHub answered with error $httpStatus. Try again later."
    }

    /** Compares "v0.1", "0.1.2", "v1.0-beta", "0.6.0a+27" by their numbers: negative, zero or positive. */
    fun compareVersions(a: String, b: String): Int {
        val x = numbers(a)
        val y = numbers(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val c = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    /** The leading "1.2.3" numbers; letters, "-beta" or "+27" after them are ignored here. */
    private fun numbers(v: String): List<Int> =
        Regex("""^[vV]?(\d+(?:\.\d+)*)""").find(v.trim())?.groupValues?.get(1)?.split('.')?.map { it.toInt() } ?: listOf(0)

    @Serializable
    private data class GhRelease(
        @SerialName("tag_name") val tagName: String,
        val name: String? = null,
        val body: String? = null,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<Asset> = emptyList(),
    )

    @Serializable
    private data class Asset(val name: String, val url: String, @SerialName("browser_download_url") val browserDownloadUrl: String)

    private val JSON = Json { ignoreUnknownKeys = true }
}
