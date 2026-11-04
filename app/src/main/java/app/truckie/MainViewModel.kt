package app.truckie

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.truckie.core.Geo
import app.truckie.core.NavApp
import app.truckie.core.Osrm
import app.truckie.core.Place
import app.truckie.core.Planner
import app.truckie.core.Release
import app.truckie.core.RouteOptimizer
import app.truckie.core.StopList
import app.truckie.core.StopType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val stops: StopList = StopList(),
    val navApp: NavApp = NavApp.GOOGLE_MAPS,
    /** Shown while something slow runs, e.g. "Finding address…". */
    val busy: String? = null,
    /** One-line note under the next stop, e.g. the last optimize result or an error. */
    val note: String? = null,
    /** Several matches for a typed address: the driver picks one. */
    val choices: List<Place> = emptyList(),
    val choiceType: StopType = StopType.DELIVERY,
    /** True right after "Delivery completed?": shows "Navigate to next stop?" with the countdown. */
    val askNavigateNext: Boolean = false,
    val update: Release? = null,
    val updateBusy: Boolean = false,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private val prefs = Prefs(app)
    private val places = Places(app)

    private val _state = MutableStateFlow(UiState(stops = store.load(), navApp = prefs.navApp))
    val state: StateFlow<UiState> = _state

    init {
        viewModelScope.launch {
            val r = Updater.check()
            if (r != null) _state.update { it.copy(update = r) }
        }
    }

    private fun change(f: (StopList) -> StopList) {
        val next = f(_state.value.stops)
        _state.update { it.copy(stops = next) }
        viewModelScope.launch(Dispatchers.IO) { store.save(next) }
    }

    // --- Adding stops (always appended to the end) ---

    fun addStop(text: String, type: StopType) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = "Finding address…", note = null) }
            val found = runCatching { places.search(text, places.here()) }
            _state.update { it.copy(busy = null) }
            found.onFailure { e -> _state.update { it.copy(note = "Couldn't look that up (${e.message}). Check signal, or paste lat,lng.") } }
            val list = found.getOrDefault(emptyList())
            when {
                found.isFailure -> Unit
                list.isEmpty() -> _state.update { it.copy(note = "No match for \"${text.trim()}\". Try adding the suburb.") }
                list.size == 1 -> addPlace(list[0], type)
                else -> _state.update { it.copy(choices = list, choiceType = type) }
            }
        }
    }

    fun addPlace(place: Place, type: StopType) {
        change { it.add(place.label, place.location, type) }
        _state.update { it.copy(choices = emptyList(), note = "Added to the end: ${place.label}") }
    }

    fun dismissChoices() = _state.update { it.copy(choices = emptyList()) }

    // --- The driving loop ---

    /** "Delivery completed?": strike the stop out, then ask "Navigate to next stop?". */
    fun completeNext() {
        val next = _state.value.stops.next ?: return
        change { it.complete(next.id, System.currentTimeMillis()) }
        _state.update { it.copy(askNavigateNext = it.stops.next != null, note = if (it.stops.next == null) "All stops done. Good on ya!" else null) }
    }

    fun dismissNavigateNext() = _state.update { it.copy(askNavigateNext = false) }

    fun restore(id: Long) = change { it.restore(id) }
    fun remove(id: Long) = change { it.remove(id) }
    fun makeNext(id: Long) { if (Features.MANUAL_REORDER) change { it.makeNext(id) } }
    fun move(id: Long, by: Int) { if (Features.MANUAL_REORDER) change { it.move(id, by) } }
    fun setType(id: Long, type: StopType) = change { it.setType(id, type) }
    fun clearAll() = change { it.cleared() }

    fun setNavApp(app: NavApp) {
        prefs.navApp = app
        _state.update { it.copy(navApp = app) }
    }

    // --- Optimize (only when the driver asks) ---

    fun optimize() {
        if (!Features.OPTIMIZE) return
        val open = _state.value.stops.open
        if (open.size < 2) {
            _state.update { it.copy(note = "Add at least 2 stops to optimize.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = "Optimizing ${open.size} stops…", note = null) }
            val here = places.here()
            val points = Planner.gridPoints(open, here)
            // Road times from OSRM; straight-line estimates if there's no signal or it fails.
            val road = runCatching { Osrm.parseTable(Http.get(Osrm.tableUrl(points)), points) }.getOrNull()
            val matrix = road ?: Geo.estimateMatrix(points)
            val order = withContext(Dispatchers.Default) { Planner.optimizedOrder(open, here, matrix) }
            // The list may have changed while waiting; only apply if the open stops are the same.
            val current = _state.value.stops
            if (current.open.map { it.id }.toSet() != order.toSet()) {
                _state.update { it.copy(busy = null, note = "Stops changed while optimizing. Tap Optimize again.") }
                return@launch
            }
            change { it.withOpenOrder(order) }
            val idx = order.map { id -> open.indexOfFirst { it.id == id } + if (here != null) 1 else 0 }
            val path = (if (here != null) listOf(0) else emptyList()) + idx
            val minutes = (RouteOptimizer.cost(path, matrix) / 60).toInt()
            val from = if (here != null) "from your position" else "from the next stop"
            val basis = if (road != null) "road times" else "straight-line estimate, no road data"
            _state.update { it.copy(busy = null, note = "Optimized ${open.size} stops $from: about ${formatMinutes(minutes)} driving ($basis).") }
        }
    }

    // --- Updates ---

    fun dismissUpdate() = _state.update { it.copy(update = null) }

    fun downloadAndInstall() {
        val r = _state.value.update ?: return
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            _state.update { it.copy(updateBusy = true) }
            val apk = runCatching { Updater.download(ctx, r) }
            _state.update { it.copy(updateBusy = false) }
            apk.onFailure { e -> _state.update { it.copy(update = null, note = "Update download failed (${e.message}). It'll ask again next launch.") } }
            apk.onSuccess { file ->
                if (Updater.install(ctx, file)) _state.update { it.copy(update = null) }
                else _state.update { it.copy(note = "Allow Truckie to install apps, then tap Update now again.") }
            }
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            val r = Updater.check()
            _state.update { if (r != null) it.copy(update = r) else it.copy(note = "Truckie ${BuildConfig.VERSION_NAME} is the newest version (or GitHub couldn't be reached).") }
        }
    }

    private fun formatMinutes(m: Int) = if (m < 60) "$m min" else "${m / 60} h ${m % 60} min"
}
