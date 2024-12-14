package me.gulya.anvil.ksp

import com.google.common.truth.Truth.assertThat
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.writeTo
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspWithCompilation
import com.tschuchort.compiletesting.symbolProcessorProviders
import me.gulya.anvil.utils.ksp.ContributesAssistedFactorySymbolProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Test
import kotlin.test.assertNotNull

@OptIn(ExperimentalCompilerApi::class)
class MultipleRoundProcessingTest {

    @Test
    fun `should handle multiple KSP rounds correctly`() {
        val compilation = KotlinCompilation().apply {
            sources = listOf(
                SourceFile.kotlin(
                    "SampleComponent.kt",
                    """
                package me.gulya.anvil.sample
                
                import dagger.assisted.Assisted
                import dagger.assisted.AssistedInject
                import me.gulya.anvil.assisted.ContributesAssistedFactory

                annotation class GenerateClass
                
                @GenerateClass
                interface SampleComponent {
                    fun interface Factory {
                        operator fun invoke(generated: SampleComponentGenerated): SampleComponent
                    }
                }
                
                @ContributesAssistedFactory(SampleScope::class, SampleComponent.Factory::class)
                class DefaultSampleComponent @AssistedInject constructor(
                    @Assisted val generated: SampleComponentGenerated
                ) : SampleComponent
                """
                ),
                SourceFile.kotlin(
                    "SampleScope.kt",
                    """
                package me.gulya.anvil.sample
                
                class SampleScope
                """
                )
            )
            symbolProcessorProviders = listOf(
                GenerateClassProcessorProvider(),
                ContributesAssistedFactorySymbolProcessor.Provider()
            )
            kspWithCompilation = true
            inheritClassPath = true
            verbose = true // Enable to see KSP processing rounds
        }

        val result = compilation.compile()
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)

        // Verify both generated classes exist after a single compilation with multiple KSP rounds
        val generatedComponent = runCatching {
            result.classLoader.loadClass("me.gulya.anvil.sample.SampleComponentGenerated")
        }

        assertNotNull(
            generatedComponent.getOrNull(),
            "SampleComponentGenerated should be created in the first KSP round"
        )

        val generatedFactory = kotlin.runCatching {
            result.classLoader.loadClass("me.gulya.anvil.sample.DefaultSampleComponent_AssistedFactory")
        }
        assertNotNull(
            generatedFactory.getOrNull(),
            "DefaultSampleComponent_AssistedFactory should be created in the second KSP round"
        )
    }

    @Test
    fun `should defer processing when types are not yet available`() {
        val compilation = KotlinCompilation().apply {
            sources = listOf(
                SourceFile.kotlin(
                    "SampleComponent.kt",
                    """
                    package me.gulya.anvil.sample
                    
                    import dagger.assisted.Assisted
                    import dagger.assisted.AssistedInject
                    import me.gulya.anvil.assisted.ContributesAssistedFactory

                    annotation class GenerateClass
                    
                    // This interface depends on two generated types
                    interface SampleComponent {
                        fun interface Factory {
                            // Requires both Generated1 and Generated2 to be available
                            operator fun invoke(
                                generated1: SampleComponentGenerated1,
                                generated2: SampleComponentGenerated2
                            ): SampleComponent
                        }
                    }
                    
                    @ContributesAssistedFactory(SampleScope::class, SampleComponent.Factory::class)
                    class DefaultSampleComponent @AssistedInject constructor(
                        @Assisted val generated1: SampleComponentGenerated1,
                        @Assisted val generated2: SampleComponentGenerated2
                    ) : SampleComponent
                    """
                ),
                SourceFile.kotlin(
                    "SampleScope.kt",
                    """
                    package me.gulya.anvil.sample
                    
                    class SampleScope
                    """
                )
            )
            symbolProcessorProviders = listOf(
                // First processor generates Generated1
                object : SymbolProcessorProvider {
                    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
                        return object : SymbolProcessor {
                            private var generated = false

                            override fun process(resolver: Resolver): List<KSAnnotated> {
                                if (!generated) {
                                    FileSpec.builder("me.gulya.anvil.sample", "SampleComponentGenerated1")
                                        .addType(
                                            TypeSpec.classBuilder("SampleComponentGenerated1")
                                                .build()
                                        )
                                        .build()
                                        .writeTo(environment.codeGenerator, aggregating = false)
                                    generated = true
                                }
                                return emptyList()
                            }
                        }
                    }
                },
                // Second processor generates Generated2 in later round
                object : SymbolProcessorProvider {
                    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
                        return object : SymbolProcessor {
                            private var roundCount = 0

                            override fun process(resolver: Resolver): List<KSAnnotated> {
                                roundCount++
                                // Generate in second round to ensure deferral
                                if (roundCount == 2) {
                                    FileSpec.builder("me.gulya.anvil.sample", "SampleComponentGenerated2")
                                        .addType(
                                            TypeSpec.classBuilder("SampleComponentGenerated2")
                                                .build()
                                        )
                                        .build()
                                        .writeTo(environment.codeGenerator, aggregating = false)
                                }
                                return emptyList()
                            }
                        }
                    }
                },
                ContributesAssistedFactorySymbolProcessor.Provider()
            )
            kspWithCompilation = true
            inheritClassPath = true
        }

        val result = compilation.compile()

        // Verify successful compilation
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)

        // Verify all classes were generated
        listOf(
            "SampleComponentGenerated1",
            "SampleComponentGenerated2",
            "DefaultSampleComponent_AssistedFactory"
        ).forEach { className ->
            assertNotNull(
                result.classLoader.loadClass("me.gulya.anvil.sample.$className"),
                "$className should be created"
            )
        }
    }

    @Test
    fun `should defer processing when any referenced type is unresolved`() {
        val compilation = KotlinCompilation().apply {
            sources = listOf(
                SourceFile.kotlin(
                    "SampleComponent.kt",
                    """
                    package me.gulya.anvil.sample
                    
                    import dagger.assisted.Assisted
                    import dagger.assisted.AssistedInject
                    import me.gulya.anvil.assisted.ContributesAssistedFactory
    
                    interface UnresolvedBoundType {
                        // Factory method parameter must match constructor parameter
                        fun create(value: UnresolvedConstructorType): UnresolvedSuperType
                    }
                    
                    @ContributesAssistedFactory(
                        scope = UnresolvedScopeType::class,
                        boundType = UnresolvedBoundType::class
                    )
                    class DefaultSampleComponent @AssistedInject constructor(
                        @Assisted val value: UnresolvedConstructorType
                    ) : UnresolvedSuperType
                    """
                )
            )
            symbolProcessorProviders = listOf(
                // Helper processor to generate types in sequence
                object : SymbolProcessorProvider {
                    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
                        return object : SymbolProcessor {
                            private var round = 0

                            override fun process(resolver: Resolver): List<KSAnnotated> {
                                round++
                                when (round) {
                                    1 -> generateClass("UnresolvedScopeType")
                                    2 -> generateClass("UnresolvedConstructorType")
                                    3 -> generateInterface("UnresolvedSuperType")
                                }
                                return emptyList()
                            }

                            private fun generateClass(name: String) {
                                FileSpec.builder("me.gulya.anvil.sample", name)
                                    .addType(TypeSpec.classBuilder(name).build())
                                    .build()
                                    .writeTo(environment.codeGenerator, aggregating = false)
                                environment.logger.info("Generated $name")
                            }

                            private fun generateInterface(name: String) {
                                FileSpec.builder("me.gulya.anvil.sample", name)
                                    .addType(TypeSpec.interfaceBuilder(name).build())
                                    .build()
                                    .writeTo(environment.codeGenerator, aggregating = false)
                                environment.logger.info("Generated $name")
                            }
                        }
                    }
                },
                ContributesAssistedFactorySymbolProcessor.Provider()
            )
            kspWithCompilation = true
            inheritClassPath = true
        }

        val result = compilation.compile()

        // Verify successful compilation
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)

        // Verify our factory was generated after all types were resolved
        assertNotNull(
            result.classLoader.loadClass("me.gulya.anvil.sample.DefaultSampleComponent_AssistedFactory"),
            "Factory should be created after all types are resolved"
        )
    }

    @Test
    fun `should fail compilation with proper errors when types are unresolved`() {
        val compilation = KotlinCompilation().apply {
            sources = listOf(
                SourceFile.kotlin(
                    "SampleComponent.kt",
                    """
                    package me.gulya.anvil.sample
                    
                    import dagger.assisted.Assisted
                    import dagger.assisted.AssistedInject
                    import me.gulya.anvil.assisted.ContributesAssistedFactory
    
                    // Using undefined types to trigger resolution failures
                    @ContributesAssistedFactory(
                        scope = NonExistentScope::class,  // Undefined scope
                        boundType = NonExistentBoundType::class  // Undefined bound type
                    )
                    class InvalidComponent @AssistedInject constructor(
                        @Assisted val param1: UnresolvedParam1,  // Undefined parameter type
                        @Assisted val param2: UnresolvedParam2   // Another undefined parameter type
                    ) : UnresolvedSuperType  // Undefined supertype
                    """
                )
            )
            symbolProcessorProviders = listOf(
                ContributesAssistedFactorySymbolProcessor.Provider()
            )
            kspWithCompilation = true
            inheritClassPath = true
            messageOutputStream = System.out // Capture compiler output
        }

        // Execute compilation
        val result = compilation.compile()

        // Verify compilation failed
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)

        // Verify error messages
        val errors = result.messages
        assertThat(errors).apply {
            // Scope type error
            contains("Unresolved reference: NonExistentScope")

            // Bound type error
            contains("Unresolved reference: NonExistentBoundType")

            // Constructor parameter type errors
            contains("Unresolved reference: UnresolvedParam1")
            contains("Unresolved reference: UnresolvedParam2")

            // Supertype error
            contains("Unresolved reference: UnresolvedSuperType")
        }

        // Verify that no factory class was generated
        assertThat(
            result.classLoader.runCatching {
                loadClass("me.gulya.anvil.sample.InvalidComponent_AssistedFactory")
            }.isFailure
        ).isTrue()
    }

    @Test
    fun `should fail compilation when only some types are unresolved`() {
        val compilation = KotlinCompilation().apply {
            sources = listOf(
                SourceFile.kotlin(
                    "ValidScope.kt",
                    """
                    package me.gulya.anvil.sample
                    class ValidScope
                    """
                ),
                SourceFile.kotlin(
                    "SampleComponent.kt",
                    """
                    package me.gulya.anvil.sample
                    
                    import dagger.assisted.Assisted
                    import dagger.assisted.AssistedInject
                    import me.gulya.anvil.assisted.ContributesAssistedFactory
    
                    interface ValidBoundType {
                        fun create(param: UnresolvedParam): ValidSuperType
                    }
    
                    interface ValidSuperType
    
                    // Mix of valid and invalid types
                    @ContributesAssistedFactory(
                        scope = ValidScope::class,  // Valid scope
                        boundType = ValidBoundType::class  // Valid bound type
                    )
                    class PartiallyInvalidComponent @AssistedInject constructor(
                        @Assisted val param: UnresolvedParam  // Single unresolved parameter
                    ) : ValidSuperType
                    """
                )
            )
            symbolProcessorProviders = listOf(
                ContributesAssistedFactorySymbolProcessor.Provider()
            )
            kspWithCompilation = true
            inheritClassPath = true
        }

        val result = compilation.compile()

        // Verify compilation failed
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)

        // Verify specific error for unresolved parameter
        assertThat(result.messages).contains("Unresolved reference: UnresolvedParam")

        // Verify no factory was generated despite some types being valid
        assertThat(
            result.classLoader.runCatching {
                loadClass("me.gulya.anvil.sample.PartiallyInvalidComponent_AssistedFactory")
            }.isFailure
        ).isTrue()
    }

}