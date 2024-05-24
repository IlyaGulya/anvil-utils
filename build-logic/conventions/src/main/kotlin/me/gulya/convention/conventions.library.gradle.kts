plugins {
    kotlin("jvm")
    kotlin("kapt")
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        optIn.addAll(
            "com.squareup.anvil.annotations.ExperimentalAnvilApi",
            "me.gulya.anvil.api.ExperimentalAnvilUtilsApi",
            "org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi"
        )
    }
}