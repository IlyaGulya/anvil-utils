import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs = listOf("-Xextended-compiler-checks")
        languageVersion = KotlinVersion.KOTLIN_1_9
    }
}

dependencies {
    implementation(projects.annotations)
}