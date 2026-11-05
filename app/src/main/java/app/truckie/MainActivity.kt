package app.truckie

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    // Once location is allowed, auto-complete (if turned on) can start watching.
    private val askPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        ArrivalService.startIfWanted(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Location lets Optimize start from where you are, and auto-complete see you arrive.
        // Notifications show auto-complete's Undo button. Truckie still works without either.
        val wanted = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) wanted += Manifest.permission.POST_NOTIFICATIONS
        askPermissions.launch(wanted.toTypedArray())
        setContent {
            val state by vm.state.collectAsState()
            TruckieTheme {
                TruckieScreen(
                    state = state,
                    vm = vm,
                    navigate = { stop ->
                        // Start watching before Maps takes over the screen (Android won't allow it after).
                        ArrivalService.startIfWanted(this@MainActivity)
                        Navigator.navigate(this@MainActivity, state.navApp, stop)
                    },
                    allowOpenMaps = {
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Arrivals.phoneVisible = true
        vm.refreshArrivalSettings()
        ArrivalService.startIfWanted(this)
    }

    override fun onStop() {
        Arrivals.phoneVisible = false
        super.onStop()
    }
}
