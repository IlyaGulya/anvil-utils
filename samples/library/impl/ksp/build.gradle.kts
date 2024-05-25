plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.anvil)
    alias(libs.plugins.ksp)
}

anvil {
    useKsp(
        contributesAndFactoryGeneration = true,
    )
    generateDaggerFactories = true
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs = listOf("-Xextended-compiler-checks")
    }
}

dependencies {
    ksp(projects.compiler)
    implementation(projects.annotations)
    implementation(projects.samples.library.api)

    implementation(libs.dagger)
}