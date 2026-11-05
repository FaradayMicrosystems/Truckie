package app.truckie

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

/** The buttons on Truckie's arrival notifications: Undo (put the stop back) and Turn off. */
class ArrivalActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ArrivalService.ACTION_UNDO -> {
                val id = intent.getLongExtra(ArrivalService.EXTRA_STOP_ID, -1)
                if (id >= 0) StopRepository.init(context).change { it.restore(id) }
                Arrivals.emitUndone(id)
                NotificationManagerCompat.from(context).cancel(ArrivalService.NOTE_ALERT)
            }
            ArrivalService.ACTION_TURN_OFF -> {
                Prefs(context).autoComplete = false
                ArrivalService.stop(context)
            }
        }
    }
}
