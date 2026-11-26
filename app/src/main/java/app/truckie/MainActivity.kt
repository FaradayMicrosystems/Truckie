package app.truckie

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
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

    // v0.9.0: say several stops in one go. The phone's own voice typing (Google's on most phones)
    // does the listening, so Truckie needs no microphone permission and no API key.
    // v0.10.0: hands-free. After each answer the mic opens again by itself, until the driver says
    // "done" / "stop" or closes the mic (back, or tap outside it).
    private var voicePickups = false
    private val voice = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val heard = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (r.resultCode == RESULT_OK && !heard.isNullOrBlank()) {
            if (vm.voiceHeard(heard, voicePickups)) listen(voicePickups)
        } else {
            vm.voiceFinished()
        }
    }

    private fun listen(pickups: Boolean) {
        voicePickups = pickups
        val prompt = if (vm.state.value.voiceCheckList) "Say your stops, with \"next\" between them"
            else "Say a stop (or a few, with \"next\"). Say \"done\" to finish"
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            // Room to breathe between addresses (some phones ignore these).
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        try {
            voice.launch(intent)
        } catch (_: ActivityNotFoundException) {
            vm.voiceFinished()
            vm.voiceUnavailable("No voice typing on this phone. Install or turn on the Google app, then tap 🎤 again.")
        }
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
                    listen = ::listen,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Arrivals.phoneVisible = true
        Arrivals.inMultiWindow = isInMultiWindowMode
        vm.refreshArrivalSettings()
        ArrivalService.startIfWanted(this)
    }

    override fun onStop() {
        Arrivals.inMultiWindow = isInMultiWindowMode
        Arrivals.phoneVisible = false
        super.onStop()
    }
}
