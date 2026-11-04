package app.truckie

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Location lets Optimize start from where you are. Truckie still works without it.
        askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        setContent {
            val state by vm.state.collectAsState()
            TruckieTheme {
                TruckieScreen(
                    state = state,
                    vm = vm,
                    navigate = { stop -> Navigator.navigate(this@MainActivity, state.navApp, stop) },
                )
            }
        }
    }
}
