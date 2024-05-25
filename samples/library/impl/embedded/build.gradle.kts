import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.anvil)
}

anvil {
    generateDaggerFactories = true
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

    implementation(libs.dagger)
}