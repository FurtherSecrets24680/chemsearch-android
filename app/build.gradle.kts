import java.util.Properties

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

val versionPropertiesFile = rootProject.file("version.properties")
val versionProperties = Properties()
if (versionPropertiesFile.exists()) {
    versionProperties.load(versionPropertiesFile.inputStream())
}

fun localVersionNameOverride(): String? {
    val gradleOverride = findProperty("chemsearch.versionName") as? String
    val envOverride = System.getenv("CHEMSEARCH_VERSION_NAME")
    return listOf(gradleOverride, envOverride)
        .firstOrNull { !it.isNullOrBlank() }
        ?.trim()
}

fun localVersionCodeOverride(): Int? {
    val gradleOverride = findProperty("chemsearch.versionCode") as? String
    val envOverride = System.getenv("CHEMSEARCH_VERSION_CODE")
    return listOf(gradleOverride, envOverride)
        .firstOrNull { !it.isNullOrBlank() }
        ?.trim()
        ?.toIntOrNull()
}

fun appVersionName(): String =
    localVersionNameOverride()
        ?: versionProperties.getProperty("VERSION_NAME")?.trim()?.takeIf { it.isNotBlank() }
        ?: "1.0.0"

fun appVersionCode(): Int =
    localVersionCodeOverride()
        ?: versionProperties.getProperty("VERSION_CODE")?.trim()?.toIntOrNull()
        ?: 1

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
        versionCode = appVersionCode()
        versionName = appVersionName()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
            buildConfigField("boolean", "FDROID_BUILD", "false")
            buildConfigField("boolean", "GITHUB_UPDATES_ENABLED", "true")
        }
        create("fdroid") {
            dimension = "distribution"
            buildConfigField("boolean", "FDROID_BUILD", "true")
            buildConfigField("boolean", "GITHUB_UPDATES_ENABLED", "false")
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                // Local compilation path
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            } else if (!System.getenv("CM_KEYSTORE_PATH").isNullOrBlank()) {
                // Codemagic CI compilation path
                storeFile = file(System.getenv("CM_KEYSTORE_PATH"))
                storePassword = System.getenv("CM_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CM_KEY_ALIAS")
                keyPassword = System.getenv("CM_KEY_PASSWORD")
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

            signingConfigs.findByName("release")?.let { config ->
                if (config.storeFile != null) {
                    signingConfig = config
                }
            }
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
