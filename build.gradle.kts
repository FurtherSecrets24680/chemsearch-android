plugins {
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    id("com.android.application") version "9.1.0" apply false
}
