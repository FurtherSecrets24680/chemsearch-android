import java.util.Properties

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

fun gitVersionCode(): Int {
    return runGit("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 1
}

fun gitVersionName(): String {
    val headTag = runGit("tag", "--points-at", "HEAD", "--sort=-v:refname")
        ?.lineSequence()
        ?.firstOrNull { it.isNotBlank() }
    if (headTag != null) return headTag

    val tag = runGit("describe", "--tags", "--abbrev=0")
    if (tag != null) {
        val commitsSinceTag = runGit("rev-list", "$tag..HEAD", "--count")?.toIntOrNull() ?: 0
        return if (commitsSinceTag == 0) tag else "$tag+$commitsSinceTag"
    }
    return runGit("rev-parse", "--short", "HEAD") ?: "1.1.0"
}

fun runGit(vararg args: String): String? {
    return try {
        val process = ProcessBuilder("git", *args)
            .directory(rootProject.projectDir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        if (process.waitFor() != 0 || output.isBlank()) null else output
    } catch (_: Exception) {
        null
    }
}

plugins {
    id("com.android.application")
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.furthersecrets.chemsearch"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.furthersecrets.chemsearch"
        minSdk = 26
        targetSdk = 34
        versionCode = gitVersionCode()
        versionName = gitVersionName()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    signingConfigs {
        create("release") {
            storeFile = file(keystoreProperties["storeFile"] as String)
            storePassword = keystoreProperties["storePassword"] as String
            keyAlias = keystoreProperties["keyAlias"] as String
            keyPassword = keystoreProperties["keyPassword"] as String
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")

    implementation(libs.androidx.activity.compose)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.core.ktx)

    // Networking
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.okhttp3:okhttp:5.3.2")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
 
