package io.github.jcastell7.modernt9

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
            AboutSection()
        }
    }
}

/**
 * Add email addresses, URLs and other tokens containing digits or symbols — the things
 * no baseline dictionary will ever contain.
 *
 * The digit sequence is shown next to each entry so it is obvious how to type it: a few
 * taps of the prefix surfaces the whole phrase as a candidate.
 */
@Composable
private fun PhraseManager(vm: PhrasesViewModel = viewModel()) {
    val phrases by vm.phrases.collectAsState()
    val loading by vm.loading.collectAsState()
    var draft by remember { mutableStateOf("") }

    Text("My phrases", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    Text(
        "Email addresses, URLs, handles — anything with digits or symbols. " +
            "Type the first few keys of the digit code to bring one up.",
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

    Spacer(Modifier.height(8.dp))

    when {
        loading -> Text("Loading…", fontSize = 13.sp)
        phrases.isEmpty() -> Text("No phrases yet.", fontSize = 13.sp)
        else -> Column {
            phrases.forEach { row ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(row.text, fontSize = 15.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        Text("keys: ${row.digits}", fontSize = 11.sp)
                    }
                    TextButton(onClick = { vm.remove(row.text) }) { Text("Remove") }
                }
            }
        }
    }
}

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
