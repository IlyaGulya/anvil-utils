import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.internal.KaptGenerateStubsTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.anvil)
}

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


tasks.withType<KaptGenerateStubsTask>().configureEach {
    // TODO necessary until anvil supports something for K2 contribution merging
    compilerOptions {
        progressiveMode.set(false)
        languageVersion.set(KotlinVersion.KOTLIN_1_9)
    }
}

tasks.withType<KotlinCompile>().configureEach {
    // TODO necessary until anvil supports something for K2 contribution merging
    compilerOptions {
        progressiveMode.set(false)
        languageVersion.set(KotlinVersion.KOTLIN_1_9)
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