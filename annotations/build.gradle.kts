plugins {
    alias(libs.plugins.kotlinx.binaryCompatibility)
    id("conventions.library")
    id("conventions.publish")
}

publish {
    configurePom(
        artifactId = "annotations",
        pomName = "Anvil Utils Annotations",
        pomDescription = "Utility annotations used to generate code using anvil-utils",
    )
}

kotlin {
    explicitApi()
}