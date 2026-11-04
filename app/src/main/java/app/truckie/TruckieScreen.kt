@file:OptIn(ExperimentalMaterial3Api::class)

package app.truckie

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.truckie.core.NavApp
import app.truckie.core.Stop
import app.truckie.core.StopType
import kotlinx.coroutines.delay
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private val Navy = Color(0xFF1F4E79)
private val Amber = Color(0xFFF2A900)

@Composable
fun TruckieTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Navy, onPrimary = Color.White, secondary = Amber, primaryContainer = Color(0xFFDCE8F5)),
        content = content,
    )
}

@Composable
fun TruckieScreen(state: UiState, vm: MainViewModel, navigate: (Stop) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var showDone by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Truckie") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy, titleContentColor = Color.White, actionIconContentColor = Color.White),
                actions = {
                    if (Features.OPTIMIZE) {
                        TextButton(onClick = vm::optimize, enabled = state.busy == null) { Text("Optimize", color = Color.White) }
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
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Check for updates") }, onClick = { vm.checkForUpdates(); menuOpen = false })
                            DropdownMenuItem(text = { Text("Clear all stops…") }, onClick = { confirmClear = true; menuOpen = false })
                            DropdownMenuItem(text = { Text("Version ${BuildConfig.VERSION_NAME}") }, onClick = { menuOpen = false })
                        }
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding()) {
            if (state.busy != null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(state.busy, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
            }
            NextStopCard(state, onComplete = vm::completeNext, navigate = navigate)
            state.note?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodyMedium) }
            AddStopRow(enabled = state.busy == null, onAdd = vm::addStop)
            val listState = rememberLazyListState()
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
            LazyColumn(Modifier.weight(1f), state = listState) {
                val open = state.stops.open
                item(key = "todo-header") {
                    SectionLabel(if (Features.DRAG_REORDER && open.size > 1) "To do (${open.size}) · hold a stop to drag it" else "To do (${open.size})")
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
            text = { Text(if (state.updateBusy) "Downloading…" else r.notes.ifBlank { "A newer version of Truckie is ready." }.take(600)) },
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
private fun NextStopCard(state: UiState, onComplete: () -> Unit, navigate: (Stop) -> Unit) {
    val next = state.stops.next
    Card(
        Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (next == null) {
                Text("No stops to do", style = MaterialTheme.typography.titleLarge)
                Text("Type an address below. New stops go on the end of the list.", style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            val left = state.stops.open.size
            Text(
                "NEXT · ${if (next.type == StopType.PICKUP) "PICKUP" else "DELIVERY"} · $left to go",
                style = MaterialTheme.typography.labelLarge, color = Navy,
            )
            Text(next.address, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onComplete, modifier = Modifier.fillMaxWidth().height(72.dp)) {
                Text(if (next.type == StopType.PICKUP) "Picked up?" else "Delivery completed?", fontSize = 22.sp)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { navigate(next) }, modifier = Modifier.fillMaxWidth()) {
                Text("Navigate with ${state.navApp.label}")
            }
        }
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
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text("Add stop: address or lat,lng") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            enabled = enabled,
        )
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FilterChip(selected = pickup, onClick = { pickup = !pickup }, label = { Text("Pickup") })
            Button(onClick = submit, enabled = enabled && text.isNotBlank()) { Text("Add") }
        }
    }
}

/** Same as the phone's settings, except a stop must be held for [Features.DRAG_HOLD_MILLIS] before it lifts. */
private class HoldToDragConfiguration(base: ViewConfiguration) : ViewConfiguration by base {
    override val longPressTimeoutMillis: Long = Features.DRAG_HOLD_MILLIS
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp), style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun OpenStopRow(number: Int, stop: Stop, vm: MainViewModel, navigate: (Stop) -> Unit, dragModifier: Modifier) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().then(dragModifier).clickable { menu = true }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("$number", style = MaterialTheme.typography.titleMedium, color = Navy, modifier = Modifier.width(28.dp))
            Column(Modifier.weight(1f)) {
                Text(stop.address, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (stop.type == StopType.PICKUP) Text("Pickup", style = MaterialTheme.typography.labelSmall, color = Amber)
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
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stop.address,
            Modifier.weight(1f).padding(vertical = 8.dp),
            textDecoration = TextDecoration.LineThrough,
            color = Color.Gray,
            maxLines = 2,
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
