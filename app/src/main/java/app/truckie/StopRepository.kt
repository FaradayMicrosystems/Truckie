package app.truckie

import android.content.Context
import app.truckie.core.StopList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The one copy of the day's stops, shared by the phone screen and the Android Auto screen,
 * so a stop ticked off in the car is ticked off on the phone too (and the other way round).
 */
object StopRepository {
    private var store: Store? = null
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _stops = MutableStateFlow(StopList())
    val stops: StateFlow<StopList> = _stops

    @Synchronized
    fun init(context: Context): StopRepository {
        if (store == null) {
            val s = Store(context.applicationContext)
            store = s
            _stops.value = s.load()
        }
        return this
    }

    /** Applies a change, then saves it in the background. Returns the new list. */
    @Synchronized
    fun change(f: (StopList) -> StopList): StopList {
        val next = f(_stops.value)
        _stops.value = next
        val s = store
        if (s != null) io.launch { s.save(next) }
        return next
    }
}
