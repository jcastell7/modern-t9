package io.github.jcastell7.modernt9

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.jcastell7.modernt9.engine.InputEngine
import io.github.jcastell7.modernt9.engine.Keypad
import io.github.jcastell7.modernt9.engine.UserWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Backs the "my words" list: saved phrases and learned words the dictionary lacks.
 *
 * Phrases are things no baseline dictionary will ever hold — email addresses, URLs,
 * handles, product codes. Learned words are whatever the user typed and accepted with
 * space that the dictionary did not know. Both live in the engine's user dictionary,
 * so this owns an engine instance rather than reaching into storage directly.
 */
class PhrasesViewModel(app: Application) : AndroidViewModel(app) {

    data class PhraseRow(
        val text: String,
        val digits: String,
        /** Times chosen — comparable between words and phrases. */
        val uses: Int,
        val isPhrase: Boolean,
        /** Epoch millis; 0 for entries older than the timestamp column. */
        val addedAt: Long,
    )

    /** How the list is ordered. [NEWEST] is the default: what you just taught it is on top. */
    enum class Sort(val label: String) {
        NEWEST("Newest"), OLDEST("Oldest"),
        AZ("A–Z"), ZA("Z–A"),
        MOST_USED("Most used"), LEAST_USED("Least used"),
    }

    private val rows = MutableStateFlow<List<PhraseRow>>(emptyList())

    private val _sort = MutableStateFlow(Sort.NEWEST)
    val sort: StateFlow<Sort> = _sort.asStateFlow()

    val phrases: StateFlow<List<PhraseRow>> =
        combine(rows, _sort) { list, sort -> sorted(list, sort) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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

    /** Removes the phrase and forgets the word — one entry may be both. */
    fun remove(text: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                engine?.userDictionary?.let { it.removePhrase(text); it.remove(text) }
            }
            refresh()
        }
    }

    private fun refresh() {
        val dict = engine?.userDictionary ?: return
        val phrases = dict.phrases().map { toRow(it, isPhrase = true) }
        val seen = phrases.map { it.text.lowercase() }.toSet()
        val words = dict.novelWords()
            .filter { it.word !in seen }
            .map { toRow(it, isPhrase = false) }
        rows.value = phrases + words
    }

    fun setSort(sort: Sort) { _sort.value = sort }

    /** The file contents for export: every saved phrase and learned word. */
    fun exportText(): String {
        val dict = engine?.userDictionary ?: return ""
        return WordsFile.write(dict.novelWords(), dict.phrases())
    }

    data class ImportResult(val added: Int, val unchanged: Int, val skipped: Int)

    private val _lastImport = MutableStateFlow<ImportResult?>(null)
    val lastImport: StateFlow<ImportResult?> = _lastImport.asStateFlow()

    /** Merge a file into the dictionary. Nothing is removed; re-importing is harmless. */
    fun import(text: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val dict = engine?.userDictionary ?: return@withContext ImportResult(0, 0, 0)
                val parsed = WordsFile.parse(text)
                var added = 0
                var unchanged = 0
                for (entry in parsed.entries) {
                    val changed = if (entry.isPhrase) dict.importPhrase(entry.word) else dict.importWord(entry.word)
                    if (changed) added++ else unchanged++
                }
                ImportResult(added, unchanged, parsed.skippedLines)
            }
            _lastImport.value = result
            refresh()
        }
    }

    private fun sorted(list: List<PhraseRow>, sort: Sort): List<PhraseRow> {
        // Case-insensitive, accent-aware, so "ñerda" sorts with the n's and "Ombe" with the o's.
        val alpha = compareBy(collator) { r: PhraseRow -> r.text }
        return when (sort) {
            Sort.NEWEST -> list.sortedWith(compareByDescending<PhraseRow> { it.addedAt }.then(alpha))
            Sort.OLDEST -> list.sortedWith(compareBy<PhraseRow> { it.addedAt }.then(alpha))
            Sort.AZ -> list.sortedWith(alpha)
            Sort.ZA -> list.sortedWith(alpha.reversed())
            Sort.MOST_USED -> list.sortedWith(compareByDescending<PhraseRow> { it.uses }.then(alpha))
            Sort.LEAST_USED -> list.sortedWith(compareBy<PhraseRow> { it.uses }.then(alpha))
        }
    }

    private val collator: java.text.Collator =
        java.text.Collator.getInstance().apply { strength = java.text.Collator.PRIMARY }

    private fun toRow(w: UserWord, isPhrase: Boolean) =
        PhraseRow(w.word, Keypad.encodeExtended(w.word), w.uses, isPhrase, w.addedAt)

    override fun onCleared() {
        engine?.close()
        super.onCleared()
    }
}
