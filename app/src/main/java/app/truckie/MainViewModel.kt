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
import app.truckie.core.SpokenStop
import app.truckie.core.StopType
import app.truckie.core.VoiceStops
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One stop from a voice list (v0.9.0), shown on the check list before it is added. */
data class VoiceItem(
    val id: Int,
    val heard: String,
    val type: StopType,
    /** Places found for [heard]; [pick] is the one that gets added. */
    val matches: List<Place> = emptyList(),
    val pick: Int = 0,
    val looking: Boolean = true,
    /** Set when the lookup itself failed (no signal), not when it found nothing. */
    val error: String? = null,
) {
    val place: Place? get() = matches.getOrNull(pick)
}

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
    /** Voice list check list (v0.9.0); null when it isn't showing. */
    val voice: List<VoiceItem>? = null,
    /** v0.10.0: show the check list (⋮), or add each spoken stop straight away (default). */
    val voiceCheckList: Boolean = false,
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
            voiceCheckList = prefs.voiceCheckList,
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
            // On launch, only speak up when there is something to install. Play copies update
            // through Google Play only (Play policy bans self-updating), so they skip GitHub.
            if (BuildConfig.PLAY_BUILD) return@launch
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

    // --- Adding several stops by voice (v0.9.0) ---

    private var voiceIds = 0

    /**
     * What voice typing heard (v0.10.0). Returns true to open the mic again (keep listening),
     * false when the driver said "done" / "stop" or the check list is showing.
     *
     * Hands-free (default): each stop is looked up and added to the end straight away. Lookups run
     * one at a time behind [voiceQueue], so stops land in the order spoken even while the mic is open.
     * Check list (⋮ option): split into stops and shown for checking first, as in v0.9.0.
     */
    fun voiceHeard(transcript: String, allPickups: Boolean): Boolean {
        val heard = VoiceStops.hear(transcript)
        val spoken = heard.stops.map { if (allPickups) it.copy(type = StopType.PICKUP) else it }
        if (_state.value.voiceCheckList) {
            if (spoken.isEmpty()) {
                if (!heard.finished) _state.update { it.copy(note = "Didn't catch an address in \"${transcript.trim()}\". Say \"next\" between stops.") }
                return false
            }
            val items = spoken.map { VoiceItem(++voiceIds, it.text, it.type) }
            _state.update { it.copy(voice = it.voice.orEmpty() + items, note = null) }
            viewModelScope.launch { items.forEach { lookUp(it.id, it.heard) } }
            return false
        }
        val session = voiceSession
        spoken.forEach { stop ->
            session.queued++
            viewModelScope.launch { voiceQueue.withLock { autoAdd(stop, session) } }
        }
        if (spoken.isEmpty() && !heard.finished) {
            _state.update { it.copy(note = "Didn't catch an address in \"${transcript.trim()}\". Say it again, or \"done\" to finish.") }
        }
        if (heard.finished) voiceFinished()
        return !heard.finished
    }

    /** Hands-free voice: counts for the note when the driver finishes. */
    private class VoiceSession { var queued = 0; var added = 0; val missed = mutableListOf<String>() }
    private var voiceSession = VoiceSession()
    private val voiceQueue = Mutex()

    private suspend fun autoAdd(stop: SpokenStop, session: VoiceSession) {
        val found = runCatching { places.search(stop.text, places.here()) }.getOrDefault(emptyList())
        val place = found.firstOrNull()
        if (place != null) {
            change { it.add(place.label, place.location, stop.type) }
            session.added++
        } else {
            session.missed += stop.text
        }
        _state.update { it.copy(note = voiceNote(session, done = false) + if (place != null) " Last: ${place.label}" else " Couldn't find \"${stop.text}\".") }
    }

    private fun voiceNote(s: VoiceSession, done: Boolean): String = buildString {
        append(if (done) "Voice: added ${s.added} stop${if (s.added == 1) "" else "s"} in the order you said them." else "🎤 Added ${s.added}.")
        if (done && s.missed.isNotEmpty()) append(" Not found, type these in: ${s.missed.joinToString("; ")}.")
    }

    /** "done", or the mic was closed: say what got added once every lookup has finished. */
    fun voiceFinished() {
        val session = voiceSession
        voiceSession = VoiceSession()
        if (session.queued == 0) return
        viewModelScope.launch { voiceQueue.withLock { _state.update { it.copy(note = voiceNote(session, done = true)) } } }
    }

    fun setVoiceCheckList(on: Boolean) {
        prefs.voiceCheckList = on
        _state.update {
            it.copy(
                voiceCheckList = on,
                note = if (on) "Voice: Truckie shows a check list before adding." else "Voice: each stop you say is added straight away; say \"done\" to finish.",
            )
        }
    }

    private suspend fun lookUp(id: Int, text: String) {
        val found = runCatching { places.search(text, places.here()) }
        updateVoice(id) {
            it.copy(
                heard = text, looking = false, pick = 0,
                matches = found.getOrDefault(emptyList()),
                error = found.exceptionOrNull()?.let { e -> "Couldn't look up (${e.message ?: "no signal"})" },
            )
        }
    }

    private fun updateVoice(id: Int, f: (VoiceItem) -> VoiceItem) =
        _state.update { s -> s.copy(voice = s.voice?.map { if (it.id == id) f(it) else it }) }

    /** Tap a row: use its next match. */
    fun voiceNextMatch(id: Int) = updateVoice(id) { if (it.matches.size > 1) it.copy(pick = (it.pick + 1) % it.matches.size) else it }
    fun voiceToggleType(id: Int) = updateVoice(id) { it.copy(type = if (it.type == StopType.PICKUP) StopType.DELIVERY else StopType.PICKUP) }
    fun voiceRemove(id: Int) = _state.update { s -> s.copy(voice = s.voice?.filterNot { it.id == id }) }

    /** Fix a misheard stop by typing, then look it up again. */
    fun voiceRetype(id: Int, text: String) {
        if (text.isBlank()) return
        updateVoice(id) { it.copy(heard = text.trim(), looking = true, error = null, matches = emptyList()) }
        viewModelScope.launch { lookUp(id, text.trim()) }
    }

    fun voiceCancel() = _state.update { it.copy(voice = null) }

    /** "Add all": every found stop goes on the end, in the order spoken. */
    fun voiceAddAll() {
        val items = _state.value.voice ?: return
        val ready = items.filter { it.place != null }
        change { l -> ready.fold(l) { acc, v -> acc.add(v.place!!.label, v.place!!.location, v.type) } }
        val missed = items.filter { it.place == null }.map { it.heard }
        val note = buildString {
            append("Added ${ready.size} stop${if (ready.size == 1) "" else "s"} to the end, in the order you said them.")
            if (missed.isNotEmpty()) append(" Not found, so not added: ${missed.joinToString("; ")}.")
        }
        _state.update { it.copy(voice = null, note = note) }
    }

    /** Voice typing isn't on this phone, or it gave up. */
    fun voiceUnavailable(why: String) = _state.update { it.copy(note = why) }

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
                val aaReady = Updater.androidAutoReady(ctx)
                when (Updater.install(ctx, file)) {
                    InstallRoute.INSTALL_WITH_OPTIONS -> _state.update {
                        it.copy(update = null, note = "Opened in Install with Options. Turn on its installer option, set it to ${Updater.PLAY_STORE}, then Install, so Android Auto keeps showing Truckie.")
                    }
                    InstallRoute.ANDROID_INSTALLER -> _state.update {
                        it.copy(update = null, note = if (aaReady) "Heads up: installing this way can hide Truckie in Android Auto again. Install with Options isn't on this phone, so redo the Play-installer install afterwards (⋮ > Android Auto)." else null)
                    }
                    InstallRoute.NEEDS_PERMISSION -> _state.update { it.copy(note = "Allow Truckie to install apps, then tap Update now again.") }
                }
            }
        }
    }

    /** ⋮ > Android Auto: says whether Android Auto will show Truckie, and how to fix it if not. */
    fun androidAutoStatus() {
        val ctx = getApplication<Application>()
        val by = Updater.installer(ctx)
        val note = if (by == Updater.PLAY_STORE) {
            "Android Auto: ready. Truckie is installed with Google Play as its installer, so Android Auto can list it. " +
                "If it still doesn't show: Android Auto settings > tap Version 10 times > ⋮ Developer settings > tick Unknown sources, then reconnect the car."
        } else {
            "Android Auto: hidden. Truckie was installed by ${by ?: "an unknown app"}, and Android Auto only lists apps installed by Google Play. " +
                "Fix: with Shizuku running, open the Truckie APK in Install with Options, set the installer to ${Updater.PLAY_STORE}, and install. " +
                "Or from a PC: adb install -r -i ${Updater.PLAY_STORE} truckie.apk"
        }
        _state.update { it.copy(note = note) }
    }

    fun checkForUpdates() {
        if (BuildConfig.PLAY_BUILD) {
            _state.update { it.copy(note = "This copy of Truckie ${BuildConfig.VERSION_NAME} comes from Google Play: updates arrive through the Play Store.") }
            return
        }
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
