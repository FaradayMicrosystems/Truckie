package app.truckie.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.truckie.StopRepository
import app.truckie.core.StopType
import kotlinx.coroutines.launch

/** The stops still to do, in order. Tap one to navigate there. Re-ordering is done on the phone. */
class StopListScreen(carContext: CarContext) : Screen(carContext) {
    private val repo = StopRepository.init(carContext)

    init {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repo.stops.collect { invalidate() }
            }
        }
    }

    override fun onGetTemplate(): Template {
        val open = repo.stops.value.open
        val items = ItemList.Builder()
        if (open.isEmpty()) items.setNoItemsMessage("No stops to do")
        open.forEachIndexed { i, stop ->
            items.addItem(
                Row.Builder()
                    .setTitle("${i + 1}. ${stop.address}")
                    .addText(if (stop.type == StopType.PICKUP) "Pickup · tap to navigate" else "Tap to navigate")
                    .setOnClickListener { carContext.navigateTo(stop) }
                    .build()
            )
        }
        return ListTemplate.Builder()
            .setTitle("To do (${open.size})")
            .setHeaderAction(Action.BACK)
            .setSingleList(items.build())
            .build()
    }
}
