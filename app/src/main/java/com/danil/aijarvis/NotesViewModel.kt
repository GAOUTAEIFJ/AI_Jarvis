package com.danil.aijarvis

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.danil.aijarvis.data.AppDatabase
import com.danil.aijarvis.data.Note
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotesViewModel(app: Application) : AndroidViewModel(app) {
    private val appContext = app
    private val dao = AppDatabase.get(app).noteDao()

    val notes = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var processingCount by mutableStateOf(0)
        private set
    var lastError by mutableStateOf<String?>(null)

    var apiKey by mutableStateOf(ApiKeyStore.get(app))
        private set

    fun saveApiKey(value: String) {
        ApiKeyStore.set(appContext, value)
        apiKey = value.trim()
    }

    private val _speech = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val speechEvents = _speech.asSharedFlow()

    private fun buildSpeech(r: StructuredNote): String = buildString {
        append("Заметка сохранена. ")
        append(r.title).append(". ")
        if (r.summary.isNotBlank()) append(r.summary).append(" ")
        if (r.tasks.isNotEmpty()) {
            append("Задачи: ")
            append(r.tasks.take(3).joinToString(", "))
            if (r.tasks.size > 3) append(" и ещё ${r.tasks.size - 3}")
            append(".")
        }
    }

    fun addNote(text: String) {
        viewModelScope.launch {
            val id = dao.insert(Note(rawText = text))

            val key = apiKey
            if (key.isBlank()) {
                lastError = "Добавьте ключ Gemini в настройках (⚙), заметка сохранена без обработки"
                _speech.tryEmit("Заметка сохранена. Добавьте ключ Gemini в настройках")
                return@launch
            }

            processingCount++
            try {
                var result = GeminiClient.structure(text, key)
                var attempt = 0
                while (result.exceptionOrNull() is RateLimitException && attempt < 2) {
                    attempt++
                    delay(3000L * attempt)
                    result = GeminiClient.structure(text, key)
                }
                result
                    .onSuccess { r ->
                        dao.applyAi(
                            id = id,
                            title = r.title,
                            summary = r.summary,
                            tasks = r.tasks.joinToString("\n"),
                            tags = r.tags.joinToString(",")
                        )
                        _speech.tryEmit(buildSpeech(r))
                    }
                    .onFailure { e ->
                        lastError = when (e) {
                            is java.net.SocketTimeoutException ->
                                "Gemini долго не отвечает, заметка сохранена без обработки"
                            is java.io.IOException ->
                                "Нет интернета, заметка сохранена без обработки"
                            else -> e.message ?: "Не удалось обработать заметку"
                        }
                        _speech.tryEmit("Заметка сохранена без обработки")
                    }
            } finally {
                processingCount--
            }
        }
    }

    fun delete(note: Note) {
        viewModelScope.launch { dao.delete(note) }
    }
}