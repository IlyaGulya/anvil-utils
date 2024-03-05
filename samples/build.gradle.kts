plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.anvil)
}

group = "me.gulya.anvil"
version = "0.1.0"

kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs = listOf("-Xextended-compiler-checks")
    }
}

dependencies {
    anvil(project(":anvil-utils"))
    implementation(project(":anvil-utils-api"))

    implementation(libs.dagger)
    kapt(libs.dagger.compiler)
}