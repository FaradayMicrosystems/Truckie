package app.truckie

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.truckie.core.ArrivalRules
import app.truckie.core.ArrivalState
import app.truckie.core.Geo
import app.truckie.core.NavApp
import app.truckie.core.OptimizeBy
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
    /** What Optimize compares: road distance or drive time (menu toggle, remembered). */
    val optimizeBy: OptimizeBy = OptimizeBy.ROAD_DISTANCE,
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
    /** Auto-complete on arrival (⋮ menu, remembered). */
    val autoComplete: Boolean = false,
    val arrivalRadiusM: Int = ArrivalRules.DEFAULT_RADIUS_M,
    val arrivalDwellSeconds: Int = ArrivalRules.DEFAULT_DWELL_SECONDS,
    /** What the arrival watcher sees right now, shown under the next stop. */
    val arrival: ArrivalState = ArrivalState.Idle,
    val arrivalRunning: Boolean = false,
    /** "Display over other apps" is on, so Truckie can open Maps to the next stop by itself. */
    val canOpenMaps: Boolean = false,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = StopRepository.init(app)
    private val prefs = Prefs(app)
    private val places = Places(app)

    private val _state = MutableStateFlow(
        UiState(
            stops = repo.stops.value, navApp = prefs.navApp, optimizeBy = prefs.optimizeBy,
            autoComplete = prefs.autoComplete, arrivalRadiusM = prefs.arrivalRadiusM, arrivalDwellSeconds = prefs.arrivalDwellSeconds,
            canOpenMaps = ArrivalService.canOpenNavigationFromBackground(app),
        )
    )
    val state: StateFlow<UiState> = _state

    init {
        // Changes made from the Android Auto screen show up here.
        viewModelScope.launch { repo.stops.collect { l -> _state.update { it.copy(stops = l) } } }
        viewModelScope.launch { Arrivals.status.collect { a -> _state.update { it.copy(arrival = a) } } }
        viewModelScope.launch { Arrivals.running.collect { r -> _state.update { it.copy(arrivalRunning = r) } } }
        // Auto-completed while the phone screen is up: same as the green button, so show the 5-second countdown here.
        viewModelScope.launch {
            Arrivals.completed.collect { id ->
                // In the car, the car screen shows the countdown instead (so navigation isn't opened twice).
                if (!Arrivals.phoneVisible || Arrivals.carConnected) return@collect
                val list = repo.stops.value
                val done = list.stops.firstOrNull { it.id == id } ?: return@collect
                val next = list.next
                _state.update {
                    it.copy(
                        askNavigateNext = next != null,
                        note = "Auto-completed ${done.address} (stopped there 2 min). Wrong? Tap Restore under Done." +
                            if (next == null) " All stops done. Good on ya!" else "",
                    )
                }
            }
        }
        viewModelScope.launch {
            // On launch, only speak up when there is something to install.
            val r = Updater.check()
            if (r is UpdateCheck.Newer) _state.update { it.copy(update = r.release) }
        }
    }

    private fun change(f: (StopList) -> StopList) {
        val next = repo.change(f)
        _state.update { it.copy(stops = next) }
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

    /** Drag and drop: [id] takes the place of [targetId]; stops in between shift by one. */
    fun dragTo(id: Long, targetId: Long) { if (Features.DRAG_REORDER) change { it.moveTo(id, targetId) } }
    fun setType(id: Long, type: StopType) = change { it.setType(id, type) }
    fun clearAll() = change { it.cleared() }

    /** "Undo optimize": back to the order the stops were added. */
    fun undoOptimize() {
        change { it.inAddedOrder() }
        _state.update { it.copy(note = "Stops are back in the order you added them.") }
    }

    fun setNavApp(app: NavApp) {
        prefs.navApp = app
        _state.update { it.copy(navApp = app) }
    }

    // --- Auto-complete on arrival (v0.5) ---

    fun setAutoComplete(on: Boolean) {
        val ctx = getApplication<Application>()
        prefs.autoComplete = on
        _state.update { it.copy(autoComplete = on) }
        if (!on) {
            ArrivalService.stop(ctx)
            _state.update { it.copy(note = "Auto-complete is off. Tap the green button at each stop.") }
            return
        }
        val started = ArrivalService.startIfWanted(ctx)
        val note = when {
            !ArrivalService.hasLocationPermission(ctx) -> "Auto-complete needs precise location. Allow it for Truckie in Android settings."
            !started && _state.value.stops.next == null -> "Auto-complete is on. It starts watching when you have a stop to do."
            !started -> "Auto-complete is on, but couldn't start. Close and reopen Truckie."
            else -> "Auto-complete is on: stop within ${prefs.arrivalRadiusM} m of the next stop for ${ArrivalRules.mmss(prefs.arrivalDwellSeconds)} and it's ticked off."
        }
        _state.update { it.copy(note = note) }
    }

    /** Stop timer: 1:00 → 1:30 → 2:00 → 2:30 → 3:00 → 1:00. */
    fun cycleArrivalDwell() {
        val choices = ArrivalRules.DWELL_CHOICES_SECONDS
        val s = choices[(choices.indexOf(prefs.arrivalDwellSeconds) + 1) % choices.size]
        prefs.arrivalDwellSeconds = s
        _state.update { it.copy(arrivalDwellSeconds = s, note = "Stop timer is now ${ArrivalRules.mmss(s)}: stay stopped in the zone that long and the stop is ticked off.") }
    }

    /** Arrival zone: 100 → 150 → 250 → 100 m. */
    fun cycleArrivalRadius() {
        val choices = ArrivalRules.RADIUS_CHOICES
        val r = choices[(choices.indexOf(prefs.arrivalRadiusM) + 1) % choices.size]
        prefs.arrivalRadiusM = r
        _state.update { it.copy(arrivalRadiusM = r, note = "Arrival zone is now $r m around each stop.") }
    }

    /** Re-reads settings changed outside the app (e.g. "Display over other apps", or Turn off from the notification). */
    fun refreshArrivalSettings() {
        val ctx = getApplication<Application>()
        _state.update { it.copy(autoComplete = prefs.autoComplete, canOpenMaps = ArrivalService.canOpenNavigationFromBackground(ctx)) }
    }

    fun setOptimizeBy(by: OptimizeBy) {
        prefs.optimizeBy = by
        _state.update { it.copy(optimizeBy = by) }
    }

    // --- Optimize (only when the driver asks) ---

    fun optimize() {
        if (!Features.OPTIMIZE) return
        if (Features.ROUTE_SOLVER) solveRoute() else chainNearestNext()
    }

    /**
     * Optimize (v0.4.1): from where you are, go to the nearest stop by road, then from that stop
     * to the nearest one left, and so on. "Nearest" is road distance or drive time, per the menu.
     */
    private fun chainNearestNext() {
        val open = _state.value.stops.open
        if (open.size < 2) {
            _state.update { it.copy(note = "Add at least 2 stops to optimize.") }
            return
        }
        val here = places.here()
        if (here == null) {
            _state.update { it.copy(note = "Truckie doesn't know where you are yet. Turn on location (and allow it for Truckie), then tap Optimize again.") }
            return
        }
        val by = _state.value.optimizeBy
        viewModelScope.launch {
            _state.update { it.copy(busy = "Optimizing ${open.size} stops ${by.short}…", note = null) }
            val points = listOf(here) + open.map { it.location }
            // One OSRM call for the whole road grid; straight-line estimates if there's no signal.
            val road = runCatching { Osrm.parseGrid(Http.get(Osrm.gridUrl(points, by)), points, by) }.getOrNull()
            val grid = road ?: Geo.estimateGrid(points, by)
            val order = withContext(Dispatchers.Default) { Planner.nearestNextOrder(open, grid) }
            if (!applyOrder(order)) return@launch
            val basis = if (road != null) by.short else "by straight-line distance (no road data, check signal)"
            _state.update { it.copy(busy = null, note = "Optimized ${open.size} stops, nearest next $basis.") }
        }
    }

    /** Kept for later (Features.ROUTE_SOLVER): the shortest drive through every stop. */
    private fun solveRoute() {
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
            if (!applyOrder(order)) return@launch
            val idx = order.map { id -> open.indexOfFirst { it.id == id } + if (here != null) 1 else 0 }
            val path = (if (here != null) listOf(0) else emptyList()) + idx
            val minutes = (RouteOptimizer.cost(path, matrix) / 60).toInt()
            val from = if (here != null) "from your position" else "from the next stop"
            val basis = if (road != null) "road times" else "straight-line estimate, no road data"
            _state.update { it.copy(busy = null, note = "Optimized ${open.size} stops $from: about ${formatMinutes(minutes)} driving ($basis).") }
        }
    }

    /** Applies a new order unless the open stops changed while it was being worked out. */
    private fun applyOrder(order: List<Long>): Boolean {
        if (_state.value.stops.open.map { it.id }.toSet() != order.toSet()) {
            _state.update { it.copy(busy = null, note = "Stops changed while optimizing. Tap Optimize again.") }
            return false
        }
        change { it.withOpenOrder(order) }
        return true
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
            _state.update { it.copy(note = "Checking GitHub for updates…") }
            when (val r = Updater.check()) {
                is UpdateCheck.Newer -> _state.update { it.copy(update = r.release, note = null) }
                is UpdateCheck.UpToDate -> _state.update {
                    it.copy(note = "You're up to date: Truckie ${BuildConfig.VERSION_NAME}" + (r.latestTag?.let { t -> " (latest release is $t)." } ?: "."))
                }
                is UpdateCheck.Failed -> _state.update { it.copy(note = "Update check failed. ${r.reason}") }
            }
        }
    }

    private fun formatMinutes(m: Int) = if (m < 60) "$m min" else "${m / 60} h ${m % 60} min"
}
