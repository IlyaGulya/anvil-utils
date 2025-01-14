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

dependencies {
    implementation(projects.processorApi)

    implementation(projects.di)
    implementation(libs.anvil.utils.annotations)
    implementation(libs.dagger)

    ksp(libs.anvil.utils.compiler)
    ksp(projects.processor)
}