plugins {
    id("conventions.library")
    id("conventions.publish")
    alias(libs.plugins.ksp)
}

publish {
    configurePom(
        artifactId = "compiler",
        pomName = "Anvil Utils Compiler",
        pomDescription = "Code generator for anvil-utils",
    )
}

tasks.withType<Test> {
    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
    }
}

dependencies {
    implementation(projects.annotations)

    api(libs.anvil.compiler.api)

    implementation(libs.anvil.compiler.utils)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
    implementation(libs.dagger)

    implementation(libs.ksp.api)

    implementation(libs.google.autoservice.annotations)
    ksp(libs.google.autoservice.ksp)

    testImplementation(libs.junit)
    testImplementation(testFixtures(libs.anvil.compiler.utils))
    testImplementation(libs.google.truth)

    testImplementation(gradleTestKit())
    testImplementation(kotlin("test"))
}