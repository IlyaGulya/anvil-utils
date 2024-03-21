package me.gulya.convention

import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.the
import javax.inject.Inject

open class PublishConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.extensions.create("publish", PublishExtension::class.java)

        target.plugins.apply(target.libs.plugins.mavenPublish.get().pluginId)
        target.plugins.apply(target.libs.plugins.kotlin.dokka.get().pluginId)

        val mavenPublishing = target.extensions
            .getByType(MavenPublishBaseExtension::class.java)

        @Suppress("UnstableApiUsage")
        mavenPublishing.pomFromGradleProperties()
        mavenPublishing.signAllPublications()
        mavenPublishing.publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)

        target.plugins.withId("org.jetbrains.kotlin.jvm") {
            mavenPublishing.configure(
                platform = KotlinJvm(
                    javadocJar = JavadocJar.Dokka(DOKKA_HTML),
                    sourcesJar = true,
                ),
            )
        }

//    // Fixes issues like:
//    // Task 'generateMetadataFileForMavenPublication' uses this output of task 'dokkaJavadocJar'
//    // without declaring an explicit or implicit dependency.
//    target.tasks.withType(GenerateModuleMetadata::class.java).configureEach {
//      it.mustRunAfter(target.tasks.withType(Jar::class.java))
//    }
    }

    companion object {
        internal const val DOKKA_HTML = "dokkaHtml"
    }
}

open class PublishExtension @Inject constructor(
    private val target: Project,
) {
    fun configurePom(
        artifactId: String,
        pomName: String,
        pomDescription: String,
    ) {
        target.the<PublishingExtension>()
            .publications.withType(MavenPublication::class.java)
            .configureEach {
                this.artifactId = artifactId

                pom {
                    name.set(pomName)
                    description.set(pomDescription)
                }
            }
    }
}
