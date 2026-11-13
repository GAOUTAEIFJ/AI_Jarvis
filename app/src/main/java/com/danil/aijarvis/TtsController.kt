package com.danil.aijarvis

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

class TtsController(
    context: Context,
    private val onProblem: (String) -> Unit
) {
    private var tts: TextToSpeech? = null
    private var ready = false

    var enabled by mutableStateOf(true)

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("ru", "RU"))
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    onProblem("Русский голос не установлен (Настройки → Синтез речи)")
                } else {
                    ready = true
                }
            } else {
                onProblem("Синтез речи недоступен на этом устройстве")
            }
        }
    }

    fun speak(text: String) {
        if (!enabled || !ready || text.isBlank()) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "note")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}