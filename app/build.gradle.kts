import java.util.Properties

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

fun gitVersionCode(): Int {
    val baseCode = runGit("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 1
    return if (gitHasWorkingTreeChanges()) baseCode + 1 else baseCode
}

fun gitVersionName(): String {
    localVersionNameOverride()?.let { return it }

    val isDirty = gitHasWorkingTreeChanges()
    val headTag = runGit("tag", "--points-at", "HEAD", "--sort=-v:refname")
        ?.lineSequence()
        ?.firstOrNull { it.isNotBlank() }
    if (headTag != null) return if (isDirty) "$headTag-dev" else headTag

    val tag = runGit("describe", "--tags", "--abbrev=0")
    if (tag != null) {
        val commitsSinceTag = runGit("rev-list", "$tag..HEAD", "--count")?.toIntOrNull() ?: 0
        val version = if (commitsSinceTag == 0) tag else "$tag+$commitsSinceTag"
        return if (isDirty) "$version-dev" else version
    }
    val version = runGit("rev-parse", "--short", "HEAD") ?: "1.1.0"
    return if (isDirty) "$version-dev" else version
}

fun localVersionNameOverride(): String? {
    val gradleOverride = findProperty("chemsearch.versionName") as? String
    val envOverride = System.getenv("CHEMSEARCH_VERSION_NAME")
    return listOf(gradleOverride, envOverride)
        .firstOrNull { !it.isNullOrBlank() }
        ?.trim()
}

fun gitHasWorkingTreeChanges(): Boolean {
    return runGitAllowBlank("status", "--porcelain")?.isNotBlank() == true
}

fun runGit(vararg args: String): String? {
    val output = runGitAllowBlank(*args) ?: return null
    return output.takeIf { it.isNotBlank() }
}

fun runGitAllowBlank(vararg args: String): String? {
    return try {
        val process = ProcessBuilder("git", *args)
            .directory(rootProject.projectDir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        if (process.waitFor() != 0) null else output
    } catch (_: Exception) {
        null
    }
}

plugins {
    id("com.android.application")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
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

    if (keystorePropertiesFile.exists()) {
        signingConfigs {
            create("release") {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }
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
    implementation(libs.phosphor.icons)

    implementation(libs.androidx.activity.compose)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation(libs.androidx.core.ktx)

    // App architecture
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    ksp(libs.androidx.room.compiler)

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
    testImplementation(libs.junit)
}
