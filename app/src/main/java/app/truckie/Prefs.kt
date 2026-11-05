package app.truckie

import android.content.Context
import app.truckie.core.ArrivalRules
import app.truckie.core.NavApp
import app.truckie.core.OptimizeBy

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("truckie", Context.MODE_PRIVATE)

    var navApp: NavApp
        get() = runCatching { NavApp.valueOf(sp.getString("navApp", null)!!) }.getOrDefault(NavApp.GOOGLE_MAPS)
        set(v) = sp.edit().putString("navApp", v.name).apply()

    var optimizeBy: OptimizeBy
        get() = if (!Features.OPTIMIZE_BY_DRIVE_TIME) OptimizeBy.ROAD_DISTANCE
            else runCatching { OptimizeBy.valueOf(sp.getString("optimizeBy", null)!!) }.getOrDefault(OptimizeBy.DRIVE_TIME)
        set(v) = sp.edit().putString("optimizeBy", v.name).apply()

    /** Auto-complete a stop after 2 minutes stopped at it (v0.5). Off until the driver turns it on. */
    var autoComplete: Boolean
        get() = sp.getBoolean("autoComplete", false)
        set(v) = sp.edit().putBoolean("autoComplete", v).apply()

    /** Arrival zone radius in metres: 50, 75 or 100. */
    var arrivalRadiusM: Int
        get() = sp.getInt("arrivalRadiusM", ArrivalRules.DEFAULT_RADIUS_M).takeIf { it in ArrivalRules.RADIUS_CHOICES } ?: ArrivalRules.DEFAULT_RADIUS_M
        set(v) = sp.edit().putInt("arrivalRadiusM", v).apply()
}
