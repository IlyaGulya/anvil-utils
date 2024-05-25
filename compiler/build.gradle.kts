plugins {
    id("conventions.library")
    id("conventions.publish")
}

publish {
    configurePom(
        artifactId = "compiler",
        pomName = "Anvil Utils Compiler",
        pomDescription = "Code generator for anvil-utils",
    )
}

dependencies {
    implementation(projects.annotations)

    api(libs.anvil.compiler.api)

    implementation(libs.anvil.compiler.utils)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
    implementation(libs.dagger)

    implementation(libs.ksp.api)

    compileOnly(libs.google.autoservice.annotations)
    kapt(libs.google.autoservice.compiler)

    testImplementation(libs.junit)
    testImplementation(testFixtures(libs.anvil.compiler.utils))
    testImplementation(libs.google.truth)
}