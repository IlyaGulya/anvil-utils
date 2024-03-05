plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kapt)
}

group = "me.gulya.anvil"
version = "0.1.0"

kotlin {
    jvmToolchain(17)
    compilerOptions {
        optIn.add("com.squareup.anvil.annotations.ExperimentalAnvilApi")
    }
}

dependencies {
    implementation(project(":anvil-utils-api"))

    api(libs.anvil.compiler.api)

    implementation(libs.anvil.compiler.utils)
    implementation(libs.kotlinpoet)
    implementation(libs.dagger)

    compileOnly(libs.google.autoservice.annotations)
    kapt(libs.google.autoservice.compiler)

    testImplementation(libs.junit)
    testImplementation(testFixtures(libs.anvil.compiler.utils))
    testImplementation(libs.google.truth)
}