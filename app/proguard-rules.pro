# Engines are looked up through the EngineFactory interface; keep implementations.
-keep class * implements io.github.jcastell7.modernt9.engine.EngineFactory { *; }
-keep interface io.github.jcastell7.modernt9.engine.** { *; }
