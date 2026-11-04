package app.truckie

import android.content.Context
import app.truckie.core.NavApp
import app.truckie.core.OptimizeBy

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("truckie", Context.MODE_PRIVATE)

    var navApp: NavApp
        get() = runCatching { NavApp.valueOf(sp.getString("navApp", null)!!) }.getOrDefault(NavApp.GOOGLE_MAPS)
        set(v) = sp.edit().putString("navApp", v.name).apply()

    var optimizeBy: OptimizeBy
        get() = runCatching { OptimizeBy.valueOf(sp.getString("optimizeBy", null)!!) }.getOrDefault(OptimizeBy.DRIVE_TIME)
        set(v) = sp.edit().putString("optimizeBy", v.name).apply()
}
