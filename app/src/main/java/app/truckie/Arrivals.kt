package app.truckie

import app.truckie.core.ArrivalState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * What the arrival watcher is doing, shared by the phone screen, the car screen and ArrivalService.
 * Lives in memory only.
 */
object Arrivals {
    /** Latest watcher state, shown under the next stop ("Stopped here: done in 1:40"). */
    val status = MutableStateFlow<ArrivalState>(ArrivalState.Idle)

    /** True while ArrivalService is running. */
    val running = MutableStateFlow(false)

    private val _completed = MutableSharedFlow<Long>(extraBufferCapacity = 4)
    /** Ids of stops just auto-completed. Whoever is on screen (phone or car) shows "Navigate to next stop?". */
    val completed: SharedFlow<Long> = _completed.asSharedFlow()
    fun emitCompleted(id: Long) { _completed.tryEmit(id) }

    private val _undone = MutableSharedFlow<Long>(extraBufferCapacity = 4)
    /** Ids of auto-completed stops the driver put back with Undo: cancels the pending auto-navigate. */
    val undone: SharedFlow<Long> = _undone.asSharedFlow()
    fun emitUndone(id: Long) { _undone.tryEmit(id) }

    /** True while the phone screen is visible: it shows its own 5-second countdown. */
    @Volatile var phoneVisible = false

    /** True while Android Auto is connected and showing Truckie's car screens. */
    @Volatile var carConnected = false
}
