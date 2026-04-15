package org.outreach.feature.map

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import java.util.Locale

@Composable
fun rememberSpokenNavigationHost(): SpokenNavigationHost {
    val host = remember { SpokenNavigationHost() }
    val context = androidx.compose.ui.platform.LocalContext.current
    DisposableEffect(host, context) {
        host.attach(context.applicationContext)
        onDispose { host.shutdown() }
    }
    return host
}

class SpokenNavigationHost : TextToSpeech.OnInitListener {
    private var textToSpeech: TextToSpeech? = null
    private var isReady = false
    private var pendingUtterances: List<String> = emptyList()

    fun attach(context: Context) {
        if (textToSpeech != null) return
        textToSpeech = TextToSpeech(context, this).apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
        }
    }

    override fun onInit(status: Int) {
        val tts = textToSpeech ?: return
        if (status != TextToSpeech.SUCCESS) return
        tts.language = Locale.US
        isReady = true
        if (pendingUtterances.isNotEmpty()) {
            speakQueue(pendingUtterances)
            pendingUtterances = emptyList()
        }
    }

    fun speakRouteStart(destinationLabel: String, instructions: List<String>) {
        val cleaned = instructions
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(10)
        val spokenList = buildList {
            add("Starting navigation to $destinationLabel.")
            if (cleaned.isEmpty()) {
                add("Route loaded. Continue toward your destination.")
            } else {
                addAll(cleaned)
                add("You are on route to $destinationLabel.")
            }
        }
        if (!isReady) {
            pendingUtterances = spokenList
            return
        }
        speakQueue(spokenList)
    }

    fun stop() {
        textToSpeech?.stop()
        pendingUtterances = emptyList()
    }

    fun shutdown() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isReady = false
        pendingUtterances = emptyList()
    }

    private fun speakQueue(utterances: List<String>) {
        val tts = textToSpeech ?: return
        utterances.forEachIndexed { idx, text ->
            val queueMode = if (idx == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts.speak(text, queueMode, null, "route_step_$idx")
        }
    }
}
