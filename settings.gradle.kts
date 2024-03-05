@file:Suppress("UnstableApiUsage")

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}

rootProject.name = "anvil-utils"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(":anvil-utils")
include(":anvil-utils-api")
include(":samples")