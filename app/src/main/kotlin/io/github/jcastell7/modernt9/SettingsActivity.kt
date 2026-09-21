package io.github.jcastell7.modernt9

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.jcastell7.modernt9.engine.EngineDescriptor

/**
 * Setup and engine selection.
 *
 * The engine picker is the user-facing half of the pluggable backend: every entry in
 * [Engines.factories] shows up here automatically.
 */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { SettingsScreen() }
            }
        }
    }
}

@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(Preferences.engineId(context)) }

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
        ) {
            Text("Modern T9", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                "A T9 keyboard with a pluggable prediction engine. No network access.",
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )

            Button(onClick = {
                context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }) { Text("Enable keyboard in system settings") }

            Button(
                onClick = { context.showImePicker() },
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("Switch to Modern T9") }

            HorizontalDivider(Modifier.padding(vertical = 20.dp))

            Text("Prediction engine", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Restart the keyboard (switch away and back) after changing this.",
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
            )

            Engines.factories.map { it.descriptor }.forEach { descriptor ->
                EngineRow(
                    descriptor = descriptor,
                    selected = descriptor.id == selected,
                    onSelect = {
                        selected = descriptor.id
                        Preferences.setEngineId(context, descriptor.id)
                    },
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 20.dp))
            PhraseManager()

            HorizontalDivider(Modifier.padding(vertical = 20.dp))
            DiagnosticsSection()

            HorizontalDivider(Modifier.padding(vertical = 20.dp))
            AboutSection()
        }
    }
}

/**
 * The user's own vocabulary: phrases added here or saved from the keyboard's offer,
 * and words learned from a space press that the dictionary did not know.
 *
 * The digit sequence is shown next to each entry so it is obvious how to type it: a few
 * taps of the prefix surfaces the whole entry as a candidate. Remove forgets it.
 */
@Composable
private fun PhraseManager(vm: PhrasesViewModel = viewModel()) {
    val phrases by vm.phrases.collectAsState()
    val loading by vm.loading.collectAsState()
    val sort by vm.sort.collectAsState()
    val lastImport by vm.lastImport.collectAsState()
    var draft by remember { mutableStateOf("") }
    val context = LocalContext.current
    var fileError by remember { mutableStateOf<String?>(null) }

    // Storage Access Framework: the user picks where the file goes / comes from, and
    // the app needs no storage permission for either.
    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        fileError = runCatching {
            context.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(vm.exportText()) }
        }.exceptionOrNull()?.let { "Export failed: ${it.message}" }
    }
    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        fileError = runCatching {
            val text = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            vm.import(text)
        }.exceptionOrNull()?.let { "Import failed: ${it.message}" }
    }

    Text("My words", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    Text(
        "Words you typed that the dictionary did not know, and phrases you saved — " +
            "email addresses, URLs, handles. Type the first few keys of the digit code " +
            "to bring one up. Remove takes a word out of the predictions. Export writes " +
            "them all to a text file you can edit and import on another phone.",
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            singleLine = true,
            label = { Text("e.g. user@gmail.com") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = { vm.add(draft); draft = "" },
            enabled = draft.isNotBlank(),
        ) { Text("Add") }
    }

    Row(Modifier.padding(top = 2.dp)) {
        TextButton(onClick = {
            exporter.launch("modern-t9-words-${java.time.LocalDate.now()}.txt")
        }) { Text("Export…") }
        TextButton(onClick = {
            importer.launch(arrayOf("text/plain", "text/*", "application/octet-stream"))
        }) { Text("Import…") }
    }
    (fileError ?: lastImport?.let { r ->
        "Imported: ${r.added} new, ${r.unchanged} already present" +
            if (r.skipped > 0) ", ${r.skipped} lines skipped" else ""
    })?.let { Text(it, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp)) }

    // Sort order. A scrolling row of chips: six options do not fit a phone width.
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PhrasesViewModel.Sort.entries.forEach { option ->
            FilterChip(
                selected = option == sort,
                onClick = { vm.setSort(option) },
                label = { Text(option.label, fontSize = 12.sp) },
            )
        }
    }

    Spacer(Modifier.height(4.dp))

    when {
        loading -> Text("Loading…", fontSize = 13.sp)
        phrases.isEmpty() -> Text("Nothing yet.", fontSize = 13.sp)
        else -> Column {
            phrases.forEach { row ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(row.text, fontSize = 15.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        Text(
                            buildString {
                                append("keys: ").append(row.digits)
                                append(if (row.isPhrase) " · saved" else " · learned")
                                append(", used ").append(row.uses).append('×')
                                if (row.addedAt > 0) append(" · ").append(dateFormat.format(java.util.Date(row.addedAt)))
                            },
                            fontSize = 11.sp,
                        )
                    }
                    TextButton(onClick = { vm.remove(row.text) }) { Text("Remove") }
                }
            }
        }
    }
}

/**
 * Opt-in diagnostic log. Records events and errors — never text — to a file that
 * `adb pull` can read on a release build. See [DebugLog].
 */
@Composable
private fun DiagnosticsSection() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(Preferences.debugLogging(context)) }
    var tail by remember { mutableStateOf(DebugLog.tail(context)) }

    Text("Diagnostics", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Debug log", fontSize = 16.sp)
            Text(
                "Writes keyboard events, timings and any errors to a file. Never records " +
                    "what you type. Off by default.",
                fontSize = 12.sp,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = {
                enabled = it
                DebugLog.setEnabled(context, it)
                tail = DebugLog.tail(context)
            },
        )
    }

    if (enabled) {
        Text("Pull it with USB debugging on:", fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        Text(
            DebugLog.pullCommand(context),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 2.dp),
        )
        Row(Modifier.padding(top = 4.dp)) {
            TextButton(onClick = { tail = DebugLog.tail(context) }) { Text("Refresh") }
            TextButton(onClick = { DebugLog.clear(context); tail = emptyList() }) { Text("Clear") }
        }
        if (tail.isEmpty()) {
            Text("Nothing logged yet.", fontSize = 12.sp)
        } else {
            Text(
                tail.joinToString("\n"),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 13.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private val dateFormat = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())

@Composable
private fun EngineRow(
    descriptor: EngineDescriptor,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(Modifier.padding(start = 8.dp)) {
            Text(descriptor.displayName, fontSize = 16.sp)
            Text(
                buildString {
                    append("v${descriptor.version} · ")
                    append(descriptor.supportedLanguages.joinToString("/"))
                    if (descriptor.supportsLearning) append(" · learns")
                    if (descriptor.supportsNextWordPrediction) append(" · next-word")
                },
                fontSize = 12.sp,
            )
        }
    }
}

private fun Context.showImePicker() {
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE)
        as android.view.inputmethod.InputMethodManager
    imm.showInputMethodPicker()
}
