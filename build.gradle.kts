plugins {
    alias(libs.plugins.android.application) apply false
    // AGP provides Kotlin support itself. Declaring the Kotlin plugin without
    // applying it pins the Kotlin version to match the Compose compiler.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
