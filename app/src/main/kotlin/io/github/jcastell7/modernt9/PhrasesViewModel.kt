package io.github.jcastell7.modernt9

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.jcastell7.modernt9.engine.InputEngine
import io.github.jcastell7.modernt9.engine.Keypad
import io.github.jcastell7.modernt9.engine.UserWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Backs the phrase manager.
 *
 * Phrases are things no baseline dictionary will ever hold — email addresses, URLs,
 * handles, product codes. They live in the engine's user dictionary, so this owns a
 * short-lived engine instance rather than reaching into storage directly.
 */
class PhrasesViewModel(app: Application) : AndroidViewModel(app) {

    data class PhraseRow(val text: String, val digits: String, val weight: Int)

    private val _phrases = MutableStateFlow<List<PhraseRow>>(emptyList())
    val phrases: StateFlow<List<PhraseRow>> = _phrases.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private var engine: InputEngine? = null

    init {
        viewModelScope.launch {
            engine = withContext(Dispatchers.IO) {
                Engines.create(getApplication(), Preferences.engineId(getApplication()))
            }
            refresh()
            _loading.value = false
        }
    }

    fun add(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { engine?.userDictionary?.addPhrase(trimmed) }
            refresh()
        }
    }

    fun remove(text: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { engine?.userDictionary?.removePhrase(text) }
            refresh()
        }
    }

    private fun refresh() {
        _phrases.value = engine?.userDictionary?.phrases().orEmpty().map(::toRow)
    }

    private fun toRow(w: UserWord) =
        PhraseRow(w.word, Keypad.encodeExtended(w.word), w.weight)

    override fun onCleared() {
        engine?.close()
        super.onCleared()
    }
}
