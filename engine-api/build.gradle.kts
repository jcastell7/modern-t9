plugins { alias(libs.plugins.kotlin.jvm) }

// Deliberately a pure-Kotlin module with NO Android dependencies.
// An engine can therefore be developed and unit-tested on the JVM, and a native
// engine can be driven from a desktop harness without an emulator.
//
// Target bytecode 17 without demanding a JDK 17 *installation*. `jvmToolchain(17)`
// would do the latter, which fails on any build machine that has a different JDK and
// no toolchain provisioning — F-Droid's build server, or a contributor on JDK 21.
// The app module has always been configured this way; these modules now match it.
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies { testImplementation(libs.junit) }
