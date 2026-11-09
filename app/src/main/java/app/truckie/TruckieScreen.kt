@file:OptIn(ExperimentalMaterial3Api::class)

package app.truckie

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.truckie.core.ArrivalRules
import app.truckie.core.ArrivalState
import app.truckie.core.NavApp
import app.truckie.core.OptimizeBy
import app.truckie.core.Stop
import app.truckie.core.StopType
import kotlinx.coroutines.delay
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private val Navy = Color(0xFF1F4E79)
private val Amber = Color(0xFFF2A900)
private val NavBlue = Color(0xFF1565C0)
private val DoneGreen = Color(0xFF2E7D32)

@Composable
fun TruckieTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Navy, onPrimary = Color.White, secondary = Amber, primaryContainer = Color(0xFFDCE8F5)),
        content = content,
    )
}

@Composable
fun TruckieScreen(state: UiState, vm: MainViewModel, navigate: (Stop) -> Unit, allowOpenMaps: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var showDone by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    // Folded next-stop card. Starts folded in a short window (split screen) the first time.
    val shortWindow = LocalConfiguration.current.screenHeightDp < 500
    var collapsed by rememberSaveable { mutableStateOf(prefs.headerCollapsed || shortWindow) }
    val listState = rememberLazyListState()
    // The title bar slides away as you scroll down the run and comes back as soon as you scroll up.
    val barScroll = TopAppBarDefaults.enterAlwaysScrollBehavior(
        canScroll = { listState.canScrollForward || listState.canScrollBackward },
    )

    Scaffold(
        modifier = Modifier.nestedScroll(barScroll.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Truckie") },
                scrollBehavior = barScroll,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Navy, scrolledContainerColor = Navy,
                    titleContentColor = Color.White, actionIconContentColor = Color.White,
                ),
                actions = {
                    if (Features.OPTIMIZE) {
                        // Wider, two lines: shows which mode Optimize will use before you tap.
                        TextButton(
                            onClick = vm::optimize,
                            enabled = state.busy == null,
                            modifier = Modifier.widthIn(min = 132.dp),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Optimize", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 18.sp)
                                Text(state.optimizeBy.short, color = Color.White, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 1)
                            }
                        }
                    }
                    Box {
                        TextButton(onClick = { menuOpen = true }) { Text("⋮", color = Color.White, fontSize = 22.sp) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            NavApp.entries.forEach { app ->
                                DropdownMenuItem(
                                    text = { Text((if (state.navApp == app) "● " else "○ ") + "Navigate with ${app.label}") },
                                    onClick = { vm.setNavApp(app); menuOpen = false },
                                )
                            }
                            if (Features.OPTIMIZE_BY_DRIVE_TIME) {
                                HorizontalDivider()
                                OptimizeBy.entries.forEach { by ->
                                    DropdownMenuItem(
                                        text = { Text((if (state.optimizeBy == by) "● " else "○ ") + by.label) },
                                        onClick = { vm.setOptimizeBy(by); menuOpen = false },
                                    )
                                }
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text((if (state.autoComplete) "☑ " else "☐ ") + "Auto-complete on arrival") },
                                onClick = { vm.setAutoComplete(!state.autoComplete); menuOpen = false },
                            )
                            if (state.autoComplete) {
                                DropdownMenuItem(
                                    text = { Text("    Arrival zone: ${state.arrivalRadiusM} m (tap to change)") },
                                    onClick = { vm.cycleArrivalRadius() },
                                )
                                DropdownMenuItem(
                                    text = { Text("    Stop timer: ${ArrivalRules.mmss(state.arrivalDwellSeconds)} (tap to change)") },
                                    onClick = { vm.cycleArrivalDwell() },
                                )
                                DropdownMenuItem(
                                    text = { Text((if (state.canOpenMaps) "    ☑ " else "    ☐ ") + "Open Maps by itself") },
                                    onClick = { allowOpenMaps(); menuOpen = false },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Undo optimize (order added)") }, onClick = { vm.undoOptimize(); menuOpen = false })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Android Auto: check setup") }, onClick = { vm.androidAutoStatus(); menuOpen = false })
                            DropdownMenuItem(text = { Text("Check for updates") }, onClick = { vm.checkForUpdates(); menuOpen = false })
                            DropdownMenuItem(text = { Text("Clear all stops…") }, onClick = { confirmClear = true; menuOpen = false })
                            DropdownMenuItem(text = { Text("Version ${BuildConfig.VERSION_NAME}") }, onClick = { menuOpen = false })
                        }
                    }
                },
            )
        },
    ) { pad ->
      Box(Modifier.padding(pad).fillMaxSize()) {
            // v0.7.0: the whole screen is one scrolling list (next-stop card, add row and every stop),
            // so it works at any window size, including split screen.
            val haptics = LocalHapticFeedback.current
            val reorderState = rememberReorderableLazyListState(listState) { from, to ->
                // Only open stops are drop targets; their keys are stop ids.
                val fromId = from.key as? Long ?: return@rememberReorderableLazyListState
                val toId = to.key as? Long ?: return@rememberReorderableLazyListState
                vm.dragTo(fromId, toId)
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            val baseConfig = LocalViewConfiguration.current
            val holdConfig = remember(baseConfig) { HoldToDragConfiguration(baseConfig) }
            LazyColumn(
                Modifier.fillMaxSize().imePadding(),
                state = listState,
                // Room at the bottom so the GPS badge never covers the last stop.
                contentPadding = PaddingValues(bottom = if (state.arrivalRunning) 64.dp else 16.dp),
            ) {
                state.busy?.let { busy -> item(key = "busy") {
                    Column {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(busy, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                    }
                } }
                item(key = "next-card") {
                    NextStopCard(
                        state,
                        collapsed = collapsed,
                        onToggle = { collapsed = !collapsed; prefs.headerCollapsed = collapsed },
                        onComplete = vm::completeNext,
                        navigate = navigate,
                    )
                }
                item(key = "arrival-line") { ArrivalLine(state) }
                state.note?.let { note ->
                    item(key = "note") { Text(note, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodyMedium) }
                }
                item(key = "add-row") { AddStopRow(enabled = state.busy == null, onAdd = vm::addStop) }
                val open = state.stops.open
                item(key = "todo-header") {
                    RunHeader(open = open.size, done = state.stops.done.size, canDrag = Features.DRAG_REORDER && open.size > 1)
                }
                itemsIndexed(open, key = { _, stop -> stop.id }) { index, stop ->
                    ReorderableItem(reorderState, key = stop.id, enabled = Features.DRAG_REORDER) { isDragging ->
                        val lift by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "lift")
                        val scale by animateFloatAsState(if (isDragging) 1.03f else 1f, label = "scale")
                        Surface(
                            shadowElevation = lift,
                            color = if (isDragging) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
                        ) {
                            CompositionLocalProvider(LocalViewConfiguration provides holdConfig) {
                                OpenStopRow(
                                    number = index + 1,
                                    stop = stop,
                                    vm = vm,
                                    navigate = navigate,
                                    dragModifier = Modifier.longPressDraggableHandle(
                                        enabled = Features.DRAG_REORDER,
                                        onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                                    ),
                                )
                            }
                        }
                    }
                }
                val done = state.stops.done
                if (done.isNotEmpty()) {
                    item {
                        Text(
                            (if (showDone) "▾ " else "▸ ") + "Done (${done.size})",
                            Modifier.fillMaxWidth().clickable { showDone = !showDone }.padding(16.dp, 12.dp),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    if (showDone) items(done, key = { "done-${it.id}" }) { stop -> DoneStopRow(stop, onRestore = { vm.restore(stop.id) }) }
                }
            }
        if (state.arrivalRunning) GpsBadge(state.arrival, Modifier.align(Alignment.BottomStart).padding(12.dp))
      }
    }

    if (state.askNavigateNext) state.stops.next?.let { next ->
        NavigateNextDialog(next, state.navApp, onYes = { vm.dismissNavigateNext(); navigate(next) }, onNo = vm::dismissNavigateNext)
    }
    if (state.choices.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = vm::dismissChoices,
            title = { Text("Which one?") },
            text = {
                Column {
                    state.choices.forEach { p ->
                        Text(p.label, Modifier.fillMaxWidth().clickable { vm.addPlace(p, state.choiceType) }.padding(vertical = 12.dp))
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = vm::dismissChoices) { Text("Cancel") } },
        )
    }
    state.update?.let { r ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("${r.name} is available") },
            text = {
                val notes = r.notes.ifBlank { "A newer version of Truckie is ready." }.take(600)
                // Until the shared signing key is set up, Android refuses to install over the old app.
                val signing = if (BuildConfig.SHARED_SIGNING_KEY) "" else
                    "\n\nIf Android says \"App not installed\", uninstall Truckie first, then install the downloaded update. (Your stops list is lost when you uninstall.)"
                Text(if (state.updateBusy) "Downloading…" else notes + signing)
            },
            confirmButton = { Button(onClick = vm::downloadAndInstall, enabled = !state.updateBusy) { Text("Update now") } },
            dismissButton = { TextButton(onClick = vm::dismissUpdate, enabled = !state.updateBusy) { Text("Later") } },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all stops?") },
            text = { Text("Removes every stop, done or not. Use this to start a new day.") },
            confirmButton = { TextButton(onClick = { vm.clearAll(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun NextStopCard(
    state: UiState,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onComplete: () -> Unit,
    navigate: (Stop) -> Unit,
) {
    val next = state.stops.next
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        if (next == null) {
            Column(Modifier.padding(12.dp)) {
                Text("No stops to do", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Type an address below. New stops go on the end of the list.", style = MaterialTheme.typography.bodyMedium)
            }
            return@Card
        }
        val left = state.stops.open.size
        val kind = if (next.type == StopType.PICKUP) "PICKUP" else "DELIVERY"
        if (collapsed) {
            // One slim row: tap the address to unfold; both buttons stay in reach.
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f).clickable(onClick = onToggle)) {
                    Text("NEXT · $kind · $left to go  ▾", style = MaterialTheme.typography.labelMedium, color = Navy, maxLines = 1)
                    Text(next.address, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                PillButton("Navigate", NavBlue, Modifier.height(48.dp)) { navigate(next) }
                PillButton("Done ✓", DoneGreen, Modifier.height(48.dp), onClick = onComplete)
            }
            return@Card
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
                Text("NEXT · $kind · $left to go", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = Navy)
                Text("▴ Fold", style = MaterialTheme.typography.labelMedium, color = Navy, modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp))
            }
            Text(next.address, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            // Two pill buttons, colour-coded so they can't be mixed up at a glance:
            // blue Navigate on the left, green done on the right.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { navigate(next) },
                    modifier = Modifier.weight(1f).height(64.dp),
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(containerColor = NavBlue, contentColor = Color.White),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Navigate", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(state.navApp.label, fontSize = 12.sp)
                    }
                }
                Button(
                    onClick = onComplete,
                    modifier = Modifier.weight(1f).height(64.dp),
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(containerColor = DoneGreen, contentColor = Color.White),
                ) {
                    Text(
                        DONE_LABEL,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun PillButton(label: String, colour: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        colors = ButtonDefaults.buttonColors(containerColor = colour, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 14.dp),
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun AddStopRow(enabled: Boolean, onAdd: (String, StopType) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var pickup by rememberSaveable { mutableStateOf(false) }
    val submit = {
        if (text.isNotBlank()) {
            onAdd(text, if (pickup) StopType.PICKUP else StopType.DELIVERY)
            text = ""
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text("Add stop: address or lat,lng") },
            // Pickup toggle sits inside the box so the add row is one line high.
            trailingIcon = {
                FilterChip(
                    selected = pickup,
                    onClick = { pickup = !pickup },
                    label = { Text("Pickup") },
                    modifier = Modifier.padding(end = 6.dp),
                )
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            enabled = enabled,
        )
        Spacer(Modifier.width(8.dp))
        Button(onClick = submit, enabled = enabled && text.isNotBlank()) { Text("Add") }
    }
}

/** Same as the phone's settings, except a stop must be held for [Features.DRAG_HOLD_MILLIS] before it lifts. */
private class HoldToDragConfiguration(base: ViewConfiguration) : ViewConfiguration by base {
    override val longPressTimeoutMillis: Long = Features.DRAG_HOLD_MILLIS
}

/** "To do" heading with a progress bar: how far through the run you are. */
@Composable
private fun RunHeader(open: Int, done: Int, canDrag: Boolean) {
    val total = open + done
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("To do ($open)", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (total > 0) Text("$done of $total done", style = MaterialTheme.typography.labelLarge, color = DoneGreen)
        }
        if (total > 0) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { done.toFloat() / total },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = DoneGreen,
            )
        }
        if (canDrag) Text("Tap a stop for options · hold to drag it", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun OpenStopRow(number: Int, stop: Stop, vm: MainViewModel, navigate: (Stop) -> Unit, dragModifier: Modifier) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().then(dragModifier).clickable { menu = true }.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Numbered badge: navy for deliveries, amber for pickups, so pickups stand out down the list.
            val pickup = stop.type == StopType.PICKUP
            Box(
                Modifier.size(32.dp).background(if (pickup) Amber else Navy, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("$number", color = if (pickup) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stop.address,
                    fontSize = 17.sp,
                    lineHeight = 21.sp,
                    fontWeight = if (number == 1) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (pickup) Text("PICKUP", style = MaterialTheme.typography.labelSmall, color = Color(0xFF9A6B00), fontWeight = FontWeight.Bold)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Navigate here") }, onClick = { menu = false; navigate(stop) })
            if (Features.MANUAL_REORDER) {
                DropdownMenuItem(text = { Text("Make next stop") }, onClick = { menu = false; vm.makeNext(stop.id) })
                DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; vm.move(stop.id, -1) })
                DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; vm.move(stop.id, +1) })
            }
            val other = if (stop.type == StopType.PICKUP) StopType.DELIVERY else StopType.PICKUP
            DropdownMenuItem(
                text = { Text(if (other == StopType.PICKUP) "Mark as pickup" else "Mark as delivery") },
                onClick = { menu = false; vm.setType(stop.id, other) },
            )
            DropdownMenuItem(text = { Text("Remove") }, onClick = { menu = false; vm.remove(stop.id) })
        }
    }
    HorizontalDivider()
}

@Composable
private fun DoneStopRow(stop: Stop, onRestore: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("✓", color = DoneGreen, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
        Text(
            stop.address,
            Modifier.weight(1f).padding(vertical = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            textDecoration = TextDecoration.LineThrough,
            color = Color.Gray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = onRestore) { Text("Restore") }
    }
}

/** "Navigate to next stop?" with a 5-second countdown. No tap means Yes. */
@Composable
private fun NavigateNextDialog(next: Stop, app: NavApp, onYes: () -> Unit, onNo: () -> Unit) {
    var secondsLeft by remember(next.id) { mutableIntStateOf(5) }
    LaunchedEffect(next.id) {
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
        onYes()
    }
    AlertDialog(
        onDismissRequest = onNo,
        title = { Text("Navigate to next stop?") },
        text = { Text("${next.address}\n\nOpening ${app.label} in $secondsLeft…") },
        confirmButton = { Button(onClick = onYes) { Text("Yes ($secondsLeft)") } },
        dismissButton = { TextButton(onClick = onNo) { Text("No") } },
    )
}

/** One line under the next stop while auto-complete is on, so you can see what it's thinking. */
@Composable
private fun ArrivalLine(state: UiState) {
    if (!state.autoComplete || state.stops.next == null) return
    val a = state.arrival
    val text = if (!state.arrivalRunning) "Auto-complete: starts when you tap Navigate." else when (a) {
        is ArrivalState.Waiting -> "Auto-complete: stopped here, done in %d:%02d unless you move.".format(a.secondsLeft / 60, a.secondsLeft % 60)
        is ArrivalState.Heading -> "Auto-complete: watching (${if (a.metres < 1000) "${a.metres} m" else "%.1f km".format(a.metres / 1000.0)} away)."
        is ArrivalState.Arrived -> "Auto-complete: done."
        ArrivalState.Idle -> "Auto-complete: waiting for a good GPS fix…"
    }
    Text(text, Modifier.padding(horizontal = 16.dp, vertical = 2.dp), style = MaterialTheme.typography.bodySmall, color = DoneGreen)
}

/**
 * v0.5.3: lower-left GPS indicator for testing auto-complete. Yellow = outside the next stop's
 * arrival zone, green = inside it. While stopped in the zone it counts down the stop timer.
 */
@Composable
private fun GpsBadge(a: ArrivalState, modifier: Modifier = Modifier) {
    val inside = when (a) {
        is ArrivalState.Heading -> a.inside
        is ArrivalState.Waiting, is ArrivalState.Arrived -> true
        ArrivalState.Idle -> false
    }
    val tint = if (inside) GpsGreen else GpsYellow
    val label = when (a) {
        is ArrivalState.Waiting -> "%d:%02d".format(a.secondsLeft / 60, a.secondsLeft % 60)
        is ArrivalState.Arrived -> "Done"
        is ArrivalState.Heading -> if (a.inside) "In zone" else if (a.metres < 1000) "${a.metres} m" else "%.1f km".format(a.metres / 1000.0)
        ArrivalState.Idle -> "No fix"
    }
    Row(
        modifier.background(Color(0xCC1B1B1B), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // "My location" crosshair, not a pin: ring, centre dot and four ticks.
        Canvas(Modifier.size(22.dp)) {
            val c = center
            val r = size.minDimension * 0.30f
            val w = size.minDimension * 0.09f
            drawCircle(tint, radius = r, center = c, style = Stroke(width = w))
            drawCircle(tint, radius = r * 0.45f, center = c)
            val tick = size.minDimension * 0.5f
            drawLine(tint, Offset(c.x, c.y - r), Offset(c.x, c.y - tick), w)
            drawLine(tint, Offset(c.x, c.y + r), Offset(c.x, c.y + tick), w)
            drawLine(tint, Offset(c.x - r, c.y), Offset(c.x - tick, c.y), w)
            drawLine(tint, Offset(c.x + r, c.y), Offset(c.x + tick, c.y), w)
        }
        Spacer(Modifier.width(6.dp))
        Text(label, color = tint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

private val GpsYellow = Color(0xFFFFC400)
private val GpsGreen = Color(0xFF4CDB5C)
