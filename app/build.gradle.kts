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

    // Pinned for reproducible builds. Left unset, AGP picks whichever build-tools happen
    // to be installed, so aapt2 and d8 differ between machines and the APK does not
    // reproduce. CI installs exactly this version.
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "io.github.jcastell7.modernt9"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "1.0.4"
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
            // AGP otherwise embeds git metadata (repo URL, branch, commit) in
            // META-INF/version-control-info.textproto. It differs between any two
            // checkouts, so a rebuild elsewhere can never match byte for byte.
            vcsInfo { include = false }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true   // DebugLog stamps the version into the log header
    }

    testOptions {
        // android.jar in unit tests is stubs; let them return defaults instead of throwing.
        unitTests.isReturnDefaultValues = true
    }

    // Dictionaries are already compact text; compressing them costs startup time.
    androidResources { noCompress += "txt" }

    // AGP otherwise writes a Play-Console-only proto of the dependency tree into the APK
    // signing block (5.7 KB under block ID 0x504b4453). It is useless outside Play, it is
    // opaque binary metadata in an otherwise auditable APK, and F-Droid's scanner rejects
    // any unexpected signing block.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
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
