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

/** How "Update now" handed the APK over. */
enum class InstallRoute { ANDROID_INSTALLER, INSTALL_WITH_OPTIONS, NEEDS_PERMISSION }

/** On launch: is there a newer GitHub Release? "Update now" downloads its APK and opens Android's installer. */
object Updater {
    private val token get() = BuildConfig.UPDATE_TOKEN

    private fun headers(accept: String) = buildMap {
        put("Accept", accept)
        put("X-GitHub-Api-Version", "2022-11-28")
        if (token.isNotBlank()) put("Authorization", "Bearer $token")
    }

    /**
     * Asks GitHub for the newest release on this build's channel and says plainly what happened.
     * Stable builds only look at full releases. Experimental builds ("e" letter) also see newer experimental pre-releases.
     */
    suspend fun check(): UpdateCheck = try {
        val version = BuildConfig.VERSION_NAME
        if (Updates.isExperimental(version)) {
            val json = Http.get(Updates.releasesUrl(BuildConfig.UPDATE_REPO), headers("application/vnd.github+json"))
            Updates.newestForExperimental(json, version)?.let { UpdateCheck.Newer(it) } ?: UpdateCheck.UpToDate("v$version")
        } else {
            val json = Http.get(Updates.latestReleaseUrl(BuildConfig.UPDATE_REPO), headers("application/vnd.github+json"))
            val latest = Updates.latest(json)
            if (latest != null && Updates.compareVersions(latest.tag, version) > 0) UpdateCheck.Newer(latest)
            else UpdateCheck.UpToDate(latest?.tag)
        }
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

    const val PLAY_STORE = "com.android.vending"
    const val INSTALL_WITH_OPTIONS = "dev.zwander.installwithoptions"

    /** The package Android records as Truckie's installer, or null if it can't tell. */
    fun installer(context: Context): String? = runCatching {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) pm.getInstallSourceInfo(context.packageName).installingPackageName
        else @Suppress("DEPRECATION") pm.getInstallerPackageName(context.packageName)
    }.getOrNull()

    /**
     * Android Auto 17+ hides template apps that weren't installed by Google Play. Truckie shows up there
     * only when it was installed with the installer set to Play (Install with Options + Shizuku, or
     * `adb install -i com.android.vending`).
     */
    fun androidAutoReady(context: Context) = installer(context) == PLAY_STORE

    fun hasInstallWithOptions(context: Context) = runCatching {
        @Suppress("DEPRECATION") context.packageManager.getPackageInfo(INSTALL_WITH_OPTIONS, 0)
    }.isSuccess

    /**
     * Opens the downloaded APK for installing. On an Android Auto ready install (Play as installer),
     * Android's own installer would make Truckie the installer and Android Auto would hide it again,
     * so the APK goes to Install with Options instead when it is on the phone.
     */
    fun install(context: Context, apk: File): InstallRoute {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        if (androidAutoReady(context) && hasInstallWithOptions(context)) {
            val opened = runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "application/vnd.android.package-archive")
                        .setPackage(INSTALL_WITH_OPTIONS)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.isSuccess
            if (opened) return InstallRoute.INSTALL_WITH_OPTIONS
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return InstallRoute.NEEDS_PERMISSION
        }
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return InstallRoute.ANDROID_INSTALLER
    }
}
