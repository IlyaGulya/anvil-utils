package me.gulya.anvil

import org.gradle.testkit.runner.GradleRunner
import org.junit.Test
import java.io.File

class KspMultiRoundFunctionalTest {
    @Test
    fun `should compile when factory depends on generated class`() {
        // Run the build
        GradleRunner.create()
            .withProjectDir(File("src/test/fixtures/ksp-multi-round"))
            .withArguments("clean", ":app:compileKotlin", "--stacktrace")
            .build()
    }
}