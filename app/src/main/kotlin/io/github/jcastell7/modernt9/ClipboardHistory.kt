package io.github.jcastell7.modernt9

import android.content.ClipboardManager
import android.content.Context

/**
 * Recent clipboard entries, for the Clipboard key in the editing pane.
 *
 * Android only exposes the *current* clip, so history has to be accumulated. An IME may
 * read the clipboard while it is the active input method, which is when this listener is
 * registered; nothing is captured while the keyboard is not in use.
 *
 * Kept in memory only — clipboard contents are frequently sensitive, and persisting them
 * would outlive the reason to hold them.
 */
object ClipboardHistory {

    private const val MAX_ENTRIES = 25
    private const val MAX_LENGTH = 2_000

    private val entries = ArrayDeque<String>()

    private var listener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private var manager: ClipboardManager? = null

    fun items(): List<String> = entries.toList()

    fun clear() = entries.clear()

    fun remove(text: String) {
        entries.remove(text)
    }

    fun start(context: Context) {
        if (listener != null) return
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        manager = cm
        capture(cm)
        listener = ClipboardManager.OnPrimaryClipChangedListener { capture(cm) }
            .also(cm::addPrimaryClipChangedListener)
    }

    fun stop() {
        listener?.let { manager?.removePrimaryClipChangedListener(it) }
        listener = null
        manager = null
    }

    /** Visible for tests: the same insertion path the clipboard listener takes. */
    internal fun pushForTest(text: String) = record(text)

    /** Record the current clip, most recent first, without duplicates. */
    fun capture(cm: ClipboardManager?) {
        val clip = cm?.primaryClip ?: return
        if (clip.itemCount == 0) return
        record(clip.getItemAt(0).coerceToText(null)?.toString().orEmpty())
    }

    private fun record(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || text.length > MAX_LENGTH) return
        entries.remove(text)
        entries.addFirst(text)
        while (entries.size > MAX_ENTRIES) entries.removeLast()
    }
}
