package io.github.jcastell7.modernt9

import android.content.Context

/**
 * Minimal persisted settings. Deliberately SharedPreferences rather than DataStore: the
 * IME reads the engine id synchronously during onCreate, before any coroutine scope is
 * useful, and there is exactly one value.
 */
object Preferences {
    private const val FILE = "settings"
    private const val KEY_ENGINE = "engine_id"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun engineId(context: Context): String =
        prefs(context).getString(KEY_ENGINE, null) ?: Engines.default.descriptor.id

    fun setEngineId(context: Context, id: String) {
        prefs(context).edit().putString(KEY_ENGINE, id).apply()
    }

    /** Last language the user selected, so it survives a restart. */
    fun language(context: Context): String? =
        prefs(context).getString(KEY_LANGUAGE, null)

    fun setLanguage(context: Context, tag: String) {
        prefs(context).edit().putString(KEY_LANGUAGE, tag).apply()
    }

    private const val KEY_LANGUAGE = "language_tag"

    /** Keyboard height multiplier, adjusted live from the resize bar. */
    fun keyboardScale(context: Context): Float =
        prefs(context).getFloat(KEY_SCALE, 1f)

    fun setKeyboardScale(context: Context, scale: Float) {
        prefs(context).edit().putFloat(KEY_SCALE, scale).apply()
    }

    /** Extra clearance above the system navigation bar, in dp. */
    fun bottomInset(context: Context): Float =
        prefs(context).getFloat(KEY_INSET, 0f)

    fun setBottomInset(context: Context, dp: Float) {
        prefs(context).edit().putFloat(KEY_INSET, dp).apply()
    }

    private const val KEY_SCALE = "keyboard_scale"
    private const val KEY_INSET = "keyboard_bottom_inset"
}
