plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(libs.ksp.api)
    implementation(libs.kotlinpoet.ksp)
    implementation("javax.inject:javax.inject:1")
} 