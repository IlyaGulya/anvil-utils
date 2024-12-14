plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.anvil)
    alias(libs.plugins.ksp)
}

anvil {
    useKsp(
        contributesAndFactoryGeneration = true,
        componentMerging = true,
    )
}

dependencies {
    implementation(projects.processorApi)
    implementation(projects.di)
    implementation(projects.library)
    implementation(libs.anvil.utils.annotations)
    implementation(libs.dagger)

    ksp(projects.processor)
    ksp(libs.anvil.utils.compiler)

    ksp(libs.dagger.compiler)
} 