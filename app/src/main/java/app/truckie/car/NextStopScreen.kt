package app.truckie.car

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.truckie.DONE_LABEL
import app.truckie.StopRepository
import app.truckie.core.StopType
import kotlinx.coroutines.launch

/** Car home screen: the next stop, one big green "That one's done! Let's ride!" button, and Navigate. */
class NextStopScreen(carContext: CarContext) : Screen(carContext) {
    private val repo = StopRepository.init(carContext)
    /** The stop just completed from the car, so Undo can restore it. */
    private var lastDoneId: Long? = null

    init {
        // Redraw whenever the list changes, including changes made on the phone.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repo.stops.collect { invalidate() }
            }
        }
    }

    override fun onGetTemplate(): Template {
        val list = repo.stops.value
        val next = list.next
        val strip = ActionStrip.Builder()
            .addAction(Action.Builder().setTitle("Stops").setOnClickListener { screenManager.push(StopListScreen(carContext)) }.build())
        lastDoneId?.let { id ->
            strip.addAction(Action.Builder().setTitle("Undo").setOnClickListener { undo(id) }.build())
        }

        if (next == null) {
            return MessageTemplate.Builder(if (list.stops.isEmpty()) "No stops yet. Add them on your phone." else "All stops done. Good on ya!")
                .setTitle("Truckie")
                .setHeaderAction(Action.APP_ICON)
                .setActionStrip(strip.build())
                .build()
        }

        val pickup = next.type == StopType.PICKUP
        val pane = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle(next.address)
                    .addText("Next · ${if (pickup) "Pickup" else "Delivery"} · ${list.open.size} to go")
                    .build()
            )
            // Same colours as the phone: blue Navigate first (left), green done second (right).
            .addAction(
                Action.Builder()
                    .setTitle("Navigate")
                    .setBackgroundColor(CarColor.BLUE)
                    .setOnClickListener { carContext.navigateTo(next) }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle(DONE_LABEL)
                    .setBackgroundColor(CarColor.GREEN)
                    .setOnClickListener { complete(next.id) }
                    .build()
            )
            .build()

        return PaneTemplate.Builder(pane)
            .setTitle("Truckie")
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(strip.build())
            .build()
    }

    private fun complete(id: Long) {
        val after = repo.change { it.complete(id, System.currentTimeMillis()) }
        lastDoneId = id
        after.next?.let { screenManager.push(NavigateNextScreen(carContext, it)) }
        invalidate()
    }

    private fun undo(id: Long) {
        repo.change { it.restore(id) }
        lastDoneId = null
        CarToast.makeText(carContext, "Stop restored", CarToast.LENGTH_SHORT).show()
        invalidate()
    }
}
