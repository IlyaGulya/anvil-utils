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

    sourceSets {
        main {
            kotlin {
                srcDir("build/anvil/main/generated")
            }
        }
    }
}

dependencies {
    anvil(projects.compiler)
    implementation(projects.annotations)

    implementation(libs.dagger)
    kapt(libs.dagger.compiler)
}