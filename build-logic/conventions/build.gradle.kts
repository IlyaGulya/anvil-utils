plugins {
    `embedded-kotlin`
    `kotlin-dsl`
    `java-gradle-plugin`
}

dependencies {
    implementation(libs.plugin.kotlin.dokka)
    implementation(libs.plugin.kotlin.jvm)
    implementation(libs.plugin.mavenPublish)
    implementation(files(libs::class.java.superclass.protectionDomain.codeSource.location))
}

gradlePlugin {
    plugins {
        register("publish") {
            id = "conventions.publish"
            implementationClass = "me.gulya.convention.PublishConventionPlugin"
        }
    }
}