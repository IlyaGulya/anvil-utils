plugins {
    kotlin("jvm")
    kotlin("kapt")
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        optIn.add("com.squareup.anvil.annotations.ExperimentalAnvilApi")
    }
}