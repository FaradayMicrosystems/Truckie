package app.truckie.car

import android.os.Handler
import android.os.Looper
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import app.truckie.core.Stop

/**
 * "Navigate to next stop?" in the car. No tap means Yes after 5 seconds.
 * The text doesn't count down each second: Android Auto limits how often a screen may change.
 */
class NavigateNextScreen(carContext: CarContext, private val next: Stop) : Screen(carContext) {
    private val handler = Handler(Looper.getMainLooper())
    private val autoYes = Runnable { yes() }
    private var answered = false

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { handler.postDelayed(autoYes, 5_000) }
            override fun onStop(owner: LifecycleOwner) { handler.removeCallbacks(autoYes) }
        })
    }

    override fun onGetTemplate(): Template =
        MessageTemplate.Builder("${next.address}\n\nOpening navigation in 5 seconds.")
            .setTitle("Navigate to next stop?")
            .setHeaderAction(Action.BACK)
            .addAction(
                Action.Builder().setTitle("Yes")
                    .setBackgroundColor(CarColor.PRIMARY)
                    .setOnClickListener { yes() }
                    .build()
            )
            .addAction(Action.Builder().setTitle("No").setOnClickListener { no() }.build())
            .build()

    private fun yes() {
        if (answered) return
        answered = true
        handler.removeCallbacks(autoYes)
        screenManager.pop()
        carContext.navigateTo(next)
    }

    private fun no() {
        if (answered) return
        answered = true
        handler.removeCallbacks(autoYes)
        screenManager.pop()
    }
}
