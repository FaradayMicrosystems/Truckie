package app.truckie

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * v0.12.0: Truckie listens in-app instead of opening the phone's voice typing pop-up.
 *
 * The pop-up gave up after a second or two of quiet and closed, which ended the whole voice session.
 * Here a quiet spell, a "didn't catch that" or a timeout just opens the mic again, so it keeps
 * listening until the driver says "done" / "stop", taps Stop, leaves Truckie, or says nothing at
 * all for [IDLE_LIMIT_MS]. Each pause is also allowed to be much longer before a stop counts as
 * finished (some phones ignore the silence hints; the restart covers those).
 *
 * Needs the microphone permission; MainActivity falls back to the old pop-up without it.
 */
class VoiceListener(
    private val context: Context,
    /** Words heard; return true to keep listening. */
    private val onHeard: (String) -> Boolean,
    /** Live words while talking ("" when quiet), for the listening card. */
    private val onPartial: (String) -> Unit,
    /** The session ended; [why] is a note for the driver, or null for a normal finish. */
    private val onEnded: (why: String?) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var running = false
    private var lastSpeechAt = 0L
    private var hardErrors = 0

    val isRunning get() = running

    fun start() {
        if (running) return
        running = true
        hardErrors = 0
        lastSpeechAt = SystemClock.elapsedRealtime()
        listenOnce()
    }

    /** Driver tapped Stop, or Truckie went to the background. */
    fun stop(why: String? = null) {
        if (!running) return
        running = false
        main.removeCallbacksAndMessages(null)
        recognizer?.run { cancel(); destroy() }
        recognizer = null
        onPartial("")
        onEnded(why)
    }

    private fun listenOnce() {
        if (!running) return
        if (SystemClock.elapsedRealtime() - lastSpeechAt > IDLE_LIMIT_MS) {
            stop("🎤 Mic off: nothing heard for ${IDLE_LIMIT_MS / 60_000} minutes. Tap 🎤 to start again.")
            return
        }
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            // Room to pause mid-address ("258 Leitchs Road … Brendale") before Truckie takes it.
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_MS)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_MS)
        try {
            r.startListening(intent)
        } catch (_: Exception) {
            retry(hard = true)
        }
    }

    /** Open the mic again shortly (a recogniser can't restart inside its own callback). */
    private fun retry(hard: Boolean, fresh: Boolean = false) {
        if (!running) return
        if (hard && ++hardErrors > 5) {
            stop("🎤 Voice stopped: the phone's speech service kept failing. Tap 🎤 to try again.")
            return
        }
        if (fresh) { recognizer?.destroy(); recognizer = null }
        main.postDelayed({ listenOnce() }, if (hard) 600L else 150L)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() { lastSpeechAt = SystemClock.elapsedRealtime() }
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            val words = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!words.isNullOrBlank()) { lastSpeechAt = SystemClock.elapsedRealtime(); onPartial(words) }
        }

        override fun onResults(results: Bundle?) {
            if (!running) return
            hardErrors = 0
            onPartial("")
            val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (heard.isNullOrBlank()) { retry(hard = false); return }
            lastSpeechAt = SystemClock.elapsedRealtime()
            if (onHeard(heard)) retry(hard = false) else stop()
        }

        override fun onError(error: Int) {
            if (!running) return
            onPartial("")
            when (error) {
                // Quiet, or nothing it could make out: just listen again.
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> retry(hard = false)
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> retry(hard = true, fresh = true)
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    stop("🎤 Truckie needs the microphone. Allow it in Settings > Apps > Truckie > Permissions.")
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
                    retry(hard = true, fresh = true)
                else -> retry(hard = true, fresh = true)
            }
        }
    }

    companion object {
        /** Quiet this long ends one stop (the phone may cap it lower). */
        const val SILENCE_MS = 6_000L
        /** No words at all for this long turns the mic off. */
        const val IDLE_LIMIT_MS = 5 * 60_000L

        fun available(context: Context) = SpeechRecognizer.isRecognitionAvailable(context)
    }
}
