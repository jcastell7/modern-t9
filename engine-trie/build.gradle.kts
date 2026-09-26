plugins { alias(libs.plugins.kotlin.jvm) }

// Bytecode 17, without requiring a JDK 17 installation — see engine-api/build.gradle.kts.
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api(project(":engine-api"))
    testImplementation(libs.junit)
}
