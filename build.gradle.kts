plugins {
    alias(libs.plugins.android).apply(false)
    alias(libs.plugins.ksp).apply(false)
    alias(libs.plugins.detekt).apply(false)
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.10" apply false
}
