package app.truckie.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class Release(val tag: String, val name: String, val notes: String, val apkName: String, val apkApiUrl: String, val apkBrowserUrl: String)

/** Checks the repo's latest GitHub Release against the installed version. */
object Updates {
    fun latestReleaseUrl(repo: String) = "https://api.github.com/repos/$repo/releases/latest"

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

    /** Compares "v0.1", "0.1.2", "v1.0-beta" by their numbers: negative, zero or positive. */
    fun compareVersions(a: String, b: String): Int {
        val x = numbers(a)
        val y = numbers(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val c = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    private fun numbers(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }

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
