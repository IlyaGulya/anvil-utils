plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.binaryCompatibility)
}

group = "me.gulya.anvil"
version = "0.1.0"

kotlin {
    jvmToolchain(17)
    explicitApi()
}