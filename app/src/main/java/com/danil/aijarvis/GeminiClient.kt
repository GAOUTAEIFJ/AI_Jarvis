package com.danil.aijarvis

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class StructuredNote(
    val title: String,
    val summary: String,
    val tasks: List<String>,
    val tags: List<String>
)

class RateLimitException : Exception("Превышен лимит запросов Gemini")

object GeminiClient {
    // Алиас всегда указывает на актуальную быструю модель Flash
    private const val MODEL = "gemini-3.5-flash-lite"
    private const val ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

    private const val SYSTEM_PROMPT = """
Ты ассистент, который превращает расшифровку голосовой заметки в структурированную заметку.
Отвечай ТОЛЬКО JSON-объектом с полями:
"title" - короткий заголовок, до 6 слов;
"summary" - суть в 1-2 предложениях;
"tasks" - массив задач из заметки (пустой массив, если задач нет);
"tags" - массив из 1-3 коротких тегов.
Не выдумывай факты, которых нет в заметке. Пиши на языке заметки.
"""

    suspend fun structure(text: String, apiKey: String): Result<StructuredNote> = withContext(Dispatchers.IO) {
        runCatching {
            val key = apiKey.trim()
            if (key.isBlank()) error("Не задан API-ключ Gemini")

            val body = JSONObject()
                .put(
                    "systemInstruction",
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT)))
                )
                .put(
                    "contents",
                    JSONArray().put(
                        JSONObject()
                            .put("role", "user")
                            .put("parts", JSONArray().put(JSONObject().put("text", text)))
                    )
                )
                .put(
                    "generationConfig",
                    JSONObject()
                        .put("temperature", 0.2)
                        .put("responseMimeType", "application/json")
                )

            val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 15_000
                conn.readTimeout = 60_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("x-goog-api-key", key)
                conn.outputStream.use { it.write(body.toString().toByteArray()) }

                val code = conn.responseCode
                if (code !in 200..299) {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    Log.e("GeminiClient", "HTTP $code: $err")
                    if (code == 429) throw RateLimitException()
                    error(
                        when (code) {
                            400, 401, 403 -> "Gemini отклонил запрос: проверьте API-ключ ($code)"
                            404 -> "Модель Gemini не найдена (404)"
                            else -> "Gemini вернул ошибку $code"
                        }
                    )
                }

                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val parts = JSONObject(response)
                    .getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                val content = buildString {
                    for (i in 0 until parts.length()) {
                        append(parts.getJSONObject(i).optString("text"))
                    }
                }
                val json = JSONObject(content)

                StructuredNote(
                    title = json.optString("title").ifBlank { "Заметка" },
                    summary = json.optString("summary"),
                    tasks = json.optJSONArray("tasks").strings(),
                    tags = json.optJSONArray("tags").strings()
                )
            } finally {
                conn.disconnect()
            }
        }.onFailure { Log.e("GeminiClient", "Ошибка обработки", it) }
    }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList()
        else (0 until length()).map { optString(it) }.filter { it.isNotBlank() }
}