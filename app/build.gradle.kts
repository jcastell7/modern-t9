import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

android {
    namespace = "io.github.jcastell7.modernt9"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.jcastell7.modernt9"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    // Release signing. The keystore and its passwords live in keystore.properties, which
    // is gitignored and points at a key stored outside the repository. When the file is
    // absent — a fresh clone, or CI without secrets — the release build still configures;
    // it just produces an unsigned APK rather than failing.
    val keystoreProps = Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) file.inputStream().use(::load)
    }
    val hasSigning = keystoreProps.getProperty("storeFile")?.let { file(it).exists() } == true &&
        keystoreProps.getProperty("storePassword") != "CHANGE_ME"

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                // v1 is unnecessary on minSdk 26 and would only weaken the signature.
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    testOptions {
        // android.jar in unit tests is stubs; let them return defaults instead of throwing.
        unitTests.isReturnDefaultValues = true
    }

    // Dictionaries are already compact text; compressing them costs startup time.
    androidResources { noCompress += "txt" }
}

dependencies {
    implementation(project(":engine-api"))
    implementation(project(":engine-trie"))
    // Add further engines here, e.g.:
    // implementation(project(":engine-touchpal"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.emoji2.emojipicker)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
