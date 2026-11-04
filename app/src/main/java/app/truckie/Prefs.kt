package app.truckie

import android.content.Context
import app.truckie.core.NavApp

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("truckie", Context.MODE_PRIVATE)

    var navApp: NavApp
        get() = runCatching { NavApp.valueOf(sp.getString("navApp", null)!!) }.getOrDefault(NavApp.GOOGLE_MAPS)
        set(v) = sp.edit().putString("navApp", v.name).apply()
}
