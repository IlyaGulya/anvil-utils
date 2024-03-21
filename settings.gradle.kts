@file:Suppress("UnstableApiUsage")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic/conventions")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}

rootProject.name = "anvil-utils"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(":compiler")
include(":annotations")
include(":samples")