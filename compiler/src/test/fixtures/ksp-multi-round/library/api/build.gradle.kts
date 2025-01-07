plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.anvil)
    alias(libs.plugins.ksp)
}

anvil {
    useKsp(
        contributesAndFactoryGeneration = true,
    )
}

dependencies {
    implementation(projects.processorApi)
    implementation(projects.di)
    implementation(libs.dagger)

    ksp(projects.processor)
}