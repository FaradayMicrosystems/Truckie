package app.truckie

import android.content.Context
import app.truckie.core.StopList
import java.io.File

/** Keeps the day's stops in one small JSON file on the phone. No account, works without signal. */
class Store(context: Context) {
    private val file = File(context.filesDir, "stops.json")
    private val tmp = File(context.filesDir, "stops.json.tmp")

    fun load(): StopList =
        runCatching { StopList.fromJson(file.readText()) }.getOrDefault(StopList())

    /** Writes a temp file first, so a crash mid-write never loses the list. */
    @Synchronized
    fun save(list: StopList) {
        tmp.writeText(list.toJson())
        tmp.renameTo(file)
    }
}
