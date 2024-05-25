import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.anvil)
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs = listOf("-Xextended-compiler-checks")
        languageVersion = KotlinVersion.KOTLIN_1_9
    }
}

dependencies {
    anvil(projects.compiler)
    implementation(projects.annotations)
    implementation(projects.samples.library.api)
    implementation(projects.samples.library.impl.embedded)

    implementation(libs.dagger)
    kapt(libs.dagger.compiler)
}