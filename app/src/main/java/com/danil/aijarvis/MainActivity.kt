package com.danil.aijarvis

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.danil.aijarvis.data.Note
import com.danil.aijarvis.ui.theme.AIJarvisTheme
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AIJarvisTheme {
                NotesScreen()
            }
        }
    }
}

private val dayFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru"))
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(vm: NotesViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val notes by vm.notes.collectAsState()
    val grouped = remember(notes) {
        notes.groupBy {
            Instant.ofEpochMilli(it.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
        }
    }
    LaunchedEffect(vm.lastError) {
        vm.lastError?.let {
            snackbarHostState.showSnackbar(it)
            vm.lastError = null
        }
    }



    val speech = remember {
        SpeechController(
            context = context,
            onResult = { text -> vm.addNote(text) },
            onError = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
        )
    }
    DisposableEffect(Unit) { onDispose { speech.destroy() } }

    val tts = remember {
        TtsController(context) { msg ->
            scope.launch { snackbarHostState.showSnackbar(msg) }
        }
    }

    var showKeyDialog by remember { mutableStateOf(vm.apiKey.isBlank()) }

    if (showKeyDialog) {
        ApiKeyDialog(
            currentKey = vm.apiKey,
            onSave = {
                vm.saveApiKey(it)
                showKeyDialog = false
                scope.launch { snackbarHostState.showSnackbar("Ключ сохранён") }
            },
            onDismiss = { showKeyDialog = false }
        )
    }

    DisposableEffect(Unit) { onDispose { tts.shutdown() } }
    LaunchedEffect(Unit) {
        vm.speechEvents.collect { tts.speak(it) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) speech.start()
        else scope.launch { snackbarHostState.showSnackbar("Нужен доступ к микрофону") }
    }

    fun onMicClick() {
        tts.stop()
        if (speech.isListening) {
            speech.stop()
        } else {
            val hasPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) speech.start()
            else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Jarvis") },
                actions = {
                    TextButton(onClick = { showKeyDialog = true }) { Text("⚙") }
                    TextButton(onClick = {
                        tts.enabled = !tts.enabled
                        if (!tts.enabled) tts.stop()
                    }) {
                        Text(if (tts.enabled) "🔊" else "🔇")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onMicClick() }) {
                Text(
                    text = if (speech.isListening) "■" else "🎤",
                    style = MaterialTheme.typography.headlineMedium
                )
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (speech.isListening) {
                Text(
                    text = "Слушаю… " + speech.partialText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            if (vm.processingCount > 0) {
                Text(
                    text = "ИИ обрабатывает заметку…",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                grouped.forEach { (date, dayNotes) ->
                    item(key = "day-$date") {
                        Text(
                            text = date.format(dayFormatter),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    items(dayNotes, key = { it.id }) { note ->
                        NoteCard(note, onDelete = { vm.delete(note) })
                    }
                }
            }
        }
    }
}

@Composable
fun NoteCard(note: Note, onDelete: () -> Unit) {
    val time = Instant.ofEpochMilli(note.createdAt)
        .atZone(ZoneId.systemDefault()).format(timeFormatter)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(time, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))

            if (note.title != null) {
                Text(note.title, style = MaterialTheme.typography.titleMedium)
                if (!note.summary.isNullOrBlank()) {
                    Text(note.summary, style = MaterialTheme.typography.bodyMedium)
                }
                note.tasks?.lines()?.filter { it.isNotBlank() }?.forEach {
                    Text("☐ $it", style = MaterialTheme.typography.bodyMedium)
                }
                if (!note.tags.isNullOrBlank()) {
                    Text(
                        text = note.tags.split(",").joinToString(" ") { "#" + it.trim() },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Оригинал: ${note.rawText}",
                    style = MaterialTheme.typography.labelSmall
                )
            } else {
                Text(note.rawText, style = MaterialTheme.typography.bodyLarge)
            }
            TextButton(onClick = onDelete) { Text("Удалить") }
        }
    }
}
@Composable
fun ApiKeyDialog(
    currentKey: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(currentKey) }
    var visible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("API-ключ Gemini") },
        text = {
            Column {
                Text(
                    "Получить ключ можно на aistudio.google.com/apikey. " +
                            "Он хранится только на этом устройстве.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("Ключ (начинается с AIza)") },
                    visualTransformation =
                        if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                TextButton(onClick = { visible = !visible }) {
                    Text(if (visible) "Скрыть" else "Показать")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}