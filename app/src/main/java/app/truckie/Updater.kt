package app.truckie

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import app.truckie.core.Release
import app.truckie.core.Updates
import java.io.File

/** What a check for updates found. */
sealed interface UpdateCheck {
    data class Newer(val release: Release) : UpdateCheck
    data class UpToDate(val latestTag: String?) : UpdateCheck
    data class Failed(val reason: String) : UpdateCheck
}

/** On launch: is there a newer GitHub Release? "Update now" downloads its APK and opens Android's installer. */
object Updater {
    private val token get() = BuildConfig.UPDATE_TOKEN

    private fun headers(accept: String) = buildMap {
        put("Accept", accept)
        put("X-GitHub-Api-Version", "2022-11-28")
        if (token.isNotBlank()) put("Authorization", "Bearer $token")
    }

    /** Asks GitHub for the latest release and says plainly what happened. */
    suspend fun check(): UpdateCheck = try {
        val json = Http.get(Updates.latestReleaseUrl(BuildConfig.UPDATE_REPO), headers("application/vnd.github+json"))
        val latest = Updates.latest(json)
        if (latest != null && Updates.compareVersions(latest.tag, BuildConfig.VERSION_NAME) > 0) UpdateCheck.Newer(latest)
        else UpdateCheck.UpToDate(latest?.tag)
    } catch (e: HttpStatusException) {
        UpdateCheck.Failed(Updates.failureReason(e.code, token.isNotBlank()))
    } catch (e: java.io.IOException) {
        UpdateCheck.Failed(Updates.failureReason(null, token.isNotBlank()))
    } catch (e: Exception) {
        UpdateCheck.Failed("Couldn't read GitHub's answer (${e.message}).")
    }

    suspend fun download(context: Context, release: Release): File {
        val dest = File(context.cacheDir, "updates/truckie-update.apk")
        // With a token, the asset API works for private repos; without one, the public download link does.
        if (token.isNotBlank()) Http.download(release.apkApiUrl, dest, headers("application/octet-stream"))
        else Http.download(release.apkBrowserUrl, dest)
        return dest
    }

    /** False when Android first needs "Allow from this source"; that settings screen is opened instead. */
    fun install(context: Context, apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }
}
