package io.github.jcastell7.modernt9

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Opt-in diagnostic log, written to a file the developer can pull over adb.
 *
 * Off by default. When enabled from Settings it records lifecycle events, key actions,
 * engine timings and every exception the keyboard catches — **never what is typed**: no
 * digits, no composing text, no candidates, no clipboard. Counts and lengths only.
 *
 * The file lives in the app's external-files directory so `adb pull` can read it on a
 * release build (internal storage is not reachable without `run-as`, which needs a
 * debuggable APK):
 *
 *     adb pull /sdcard/Android/data/io.github.jcastell7.modernt9/files/logs/modern-t9.log
 *
 * Everything also goes to logcat under the [TAG] tag, so `adb logcat -s ModernT9` works
 * live. Writes are synchronous but tiny; the file rotates once at [MAX_BYTES].
 */
object DebugLog {
    const val TAG = "ModernT9"
    private const val DIR = "logs"
    private const val FILE = "modern-t9.log"
    private const val MAX_BYTES = 512 * 1024L

    @Volatile var enabled: Boolean = false
        private set

    private var file: File? = null
    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    /** Read the preference and point at the file. Safe to call repeatedly. */
    fun configure(context: Context) {
        enabled = Preferences.debugLogging(context)
        file = logFile(context)
        if (enabled) i("log", "enabled · ${BuildConfig.VERSION_NAME} · sdk ${android.os.Build.VERSION.SDK_INT} · ${android.os.Build.MODEL}")
    }

    fun setEnabled(context: Context, on: Boolean) {
        Preferences.setDebugLogging(context, on)
        configure(context)
        if (!on) Log.i(TAG, "log disabled")
    }

    /** Where the log is, whether or not it exists yet. */
    fun logFile(context: Context): File? =
        context.getExternalFilesDir(null)?.let { File(File(it, DIR), FILE) }

    /** The `adb pull` command for this device, shown in Settings. */
    fun pullCommand(context: Context): String =
        "adb pull ${logFile(context)?.absolutePath ?: "<external files dir unavailable>"}"

    fun clear(context: Context) {
        runCatching { logFile(context)?.delete(); rotated(context)?.delete() }
    }

    /** The last [lines] lines, newest last — for the in-app preview. */
    fun tail(context: Context, lines: Int = 40): List<String> =
        runCatching { logFile(context)?.takeIf { it.exists() }?.readLines()?.takeLast(lines) }
            .getOrNull().orEmpty()

    fun i(where: String, message: String) = write("I", where, message, null)
    fun w(where: String, message: String, error: Throwable? = null) = write("W", where, message, error)
    fun e(where: String, message: String, error: Throwable? = null) = write("E", where, message, error)

    /** Run [block], logging and swallowing anything it throws. For the IME's own handlers. */
    inline fun <T> guard(where: String, fallback: T, block: () -> T): T =
        try { block() } catch (t: Throwable) { e(where, "uncaught", t); fallback }

    private fun write(level: String, where: String, message: String, error: Throwable?) {
        when (level) {
            "E" -> Log.e(TAG, "$where: $message", error)
            "W" -> Log.w(TAG, "$where: $message", error)
            else -> Log.i(TAG, "$where: $message")
        }
        if (!enabled) return
        val f = file ?: return
        val line = buildString {
            append(stamp.format(Date())).append(' ').append(level).append(' ')
            append(where).append(": ").append(message)
            if (error != null) {
                append('\n')
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                append(sw.toString().trimEnd())
            }
            append('\n')
        }
        synchronized(this) {
            runCatching {
                f.parentFile?.mkdirs()
                if (f.length() > MAX_BYTES) {
                    rotated(f)?.let { old -> old.delete(); f.renameTo(old) }
                }
                f.appendText(line)
            }
        }
    }

    private fun rotated(context: Context): File? = logFile(context)?.let(::rotated)
    private fun rotated(f: File): File? = File(f.parentFile, f.nameWithoutExtension + ".1.log")
}
