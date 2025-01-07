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
    implementation(projects.di)
    implementation(projects.library.api)
    implementation(libs.anvil.utils.annotations)
    implementation(libs.dagger)

    ksp(libs.anvil.utils.compiler)
}