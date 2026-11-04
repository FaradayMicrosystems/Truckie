package app.truckie

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object Http {
    // OSRM, Photon and GitHub all ask for an identifying User-Agent.
    private const val USER_AGENT = "Truckie/${BuildConfig.VERSION_NAME} (Android)"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }
        .build()

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        client.newCall(request(url, headers)).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw IOException("HTTP ${res.code} from ${res.request.url.host}")
            text
        }
    }

    /** Downloads to [dest]. OkHttp drops the Authorization header if GitHub redirects to its file host. */
    suspend fun download(url: String, dest: File, headers: Map<String, String> = emptyMap()) = withContext(Dispatchers.IO) {
        client.newCall(request(url, headers)).execute().use { res ->
            if (!res.isSuccessful) throw IOException("HTTP ${res.code} from ${res.request.url.host}")
            dest.parentFile?.mkdirs()
            res.body!!.byteStream().use { input -> dest.outputStream().use { input.copyTo(it) } }
        }
    }

    private fun request(url: String, headers: Map<String, String>) =
        Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
}
