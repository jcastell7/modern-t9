package io.github.jcastell7.modernt9

import android.content.Context
import io.github.jcastell7.modernt9.engine.EngineFactory
import io.github.jcastell7.modernt9.engine.EngineResources
import io.github.jcastell7.modernt9.engine.InputEngine
import io.github.jcastell7.modernt9.engine.trie.TrieEngineFactory
import java.io.File
import java.io.InputStream

/**
 * ## The engine registry — the one place to add a prediction backend.
 *
 * To plug in a new engine:
 *
 *  1. Create a module (e.g. `engine-touchpal`) that depends on `:engine-api`.
 *  2. Implement [io.github.jcastell7.modernt9.engine.InputEngine] and [EngineFactory] in it.
 *  3. `include(":engine-touchpal")` in `settings.gradle.kts`, and add
 *     `implementation(project(":engine-touchpal"))` to `app/build.gradle.kts`.
 *  4. Add its factory to [factories] below.
 *
 * That is the whole integration. Nothing else in the app refers to a concrete engine.
 */
object Engines {

    /** Every engine compiled into this build, in preference order. */
    val factories: List<EngineFactory> = listOf(
        TrieEngineFactory(),
        // TouchPalEngineFactory(),   // once the native engine is reverse engineered
    )

    val default: EngineFactory get() = factories.first()

    fun byId(id: String?): EngineFactory =
        factories.firstOrNull { it.descriptor.id == id } ?: default

    /**
     * The engine built for this process, keyed by id.
     *
     * Parsing 160k dictionary words takes a noticeable moment, and the IME service can be
     * torn down and recreated while the process lives on. Holding the engine here means
     * that cost is paid once per process rather than once per keyboard.
     */
    private var cached: Pair<String, InputEngine>? = null

    /** Drop the cache — used when the chosen engine changes. */
    fun invalidate() {
        cached?.second?.close()
        cached = null
    }

    /**
     * Build an engine, falling back to [default] if it cannot initialise — a
     * half-finished experimental backend must never make the keyboard unusable.
     */
    fun create(context: Context, id: String?): InputEngine {
        cached?.let { (cachedId, engine) -> if (cachedId == id.orEmpty()) return engine }
        val factory = byId(id)
        val resources = AndroidEngineResources(context, factory.descriptor.id)
        val built = runCatching {
            factory.create(resources).apply { initialize() }
        }.getOrElse { failure ->
            android.util.Log.e(TAG, "engine '${factory.descriptor.id}' failed; falling back", failure)
            val fallback = default
            fallback.create(AndroidEngineResources(context, fallback.descriptor.id))
                .apply { initialize() }
        }
        cached = id.orEmpty() to built
        return built
    }

    private const val TAG = "Engines"
}

/**
 * Bridges [EngineResources] onto Android. Engines stay free of Android imports, which is
 * what lets them be unit-tested on the JVM and driven from a desktop RE harness.
 *
 * Each engine gets its own data directory, so learned data from one backend never
 * confuses another.
 */
class AndroidEngineResources(
    private val context: Context,
    private val engineId: String,
) : EngineResources {

    override fun openAsset(path: String): InputStream? =
        runCatching { context.assets.open(path) }.getOrNull()

    override fun dataDir(): File =
        File(context.filesDir, "engines/$engineId").apply { mkdirs() }

    override val languageTag: String
        get() = context.resources.configuration.locales[0].language ?: "en"
}
