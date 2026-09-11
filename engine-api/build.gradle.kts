plugins { alias(libs.plugins.kotlin.jvm) }

// Deliberately a pure-Kotlin module with NO Android dependencies.
// An engine can therefore be developed and unit-tested on the JVM, and a native
// engine can be driven from a desktop harness without an emulator.
kotlin { jvmToolchain(17) }

dependencies { testImplementation(libs.junit) }
