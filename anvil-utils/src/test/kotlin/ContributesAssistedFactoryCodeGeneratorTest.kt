import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.compiler.internal.testing.compileAnvil
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.COMPILATION_ERROR
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Test
import java.lang.reflect.AnnotatedElement

@OptIn(ExperimentalCompilerApi::class)
class ContributesAssistedFactoryCodeGeneratorTest {

    @Test
    fun `an assisted factory with binding is generated`() {
        compileAnvil(
            """
                package com.test
                
                import dagger.assisted.Assisted
                import dagger.assisted.AssistedInject
                import me.gulya.anvil.utils.ContributesAssistedFactory

                interface TestApi

                interface TestApiFactory {
                    fun create(
                        bebe: String,
                        @Assisted("test") bebe2: String,
                    ): TestApi
                }

                @ContributesAssistedFactory(Any::class, TestApiFactory::class)
                class DefaultTestApi @AssistedInject constructor(
                    @Assisted private val bebe: String,
                    @Assisted("test") private val bebe2: String,
                ) : TestApi
            """,
        ) {
            assertThat(exitCode).isEqualTo(OK)

            val testApiFactoryClazz = classLoader.loadClass("com.test.TestApiFactory")
            val clazz = classLoader.loadClass("com.test.DefaultTestApi_AssistedFactory")

            assertThat(clazz.interfaces).asList().containsExactly(testApiFactoryClazz)

            clazz.requireAnnotation<AssistedFactory>()

            val contributesBindingAnnotation = clazz.requireAnnotation<ContributesBinding>()
            assertThat(contributesBindingAnnotation.scope).isEqualTo(Any::class)
            assertThat(contributesBindingAnnotation.boundType.java).isEqualTo(testApiFactoryClazz)

            val factoryMethod = clazz.declaredMethods.single()

            assertThat(factoryMethod.returnType).isEqualTo(classLoader.loadClass("com.test.DefaultTestApi"))
            val firstParameter = factoryMethod.parameters[0]
            assertThat(firstParameter.annotations).isEmpty()

            val secondParameter = factoryMethod.parameters[1]
            val assisted = secondParameter.requireAnnotation<Assisted>()

            assertThat(assisted.value).isEqualTo("test")
        }
    }

    @Test
    fun `an assisted factory without binding is generated`() {
        compileAnvil(
            """
            package com.test

            import dagger.assisted.Assisted
            import dagger.assisted.AssistedInject
            import me.gulya.anvil.utils.ContributesAssistedFactory

            interface TestApi

            interface TestApiFactory {
                fun create(
                    bebe: String,
                    @Assisted("test") bebe2: String,
                ): TestApi
            }

            @ContributesAssistedFactory(Any::class)
            class DefaultTestApi @AssistedInject constructor(
                @Assisted private val bebe: String,
                @Assisted("test") private val bebe2: String,
            ) : TestApi
        """,
        ) {
            assertThat(exitCode).isEqualTo(OK)

            val clazz = classLoader.loadClass("com.test.DefaultTestApi_AssistedFactory")

            assertThat(clazz.interfaces).isEmpty()

            val contributesToAnnotation = clazz.requireAnnotation<ContributesTo>()
            assertThat(contributesToAnnotation.scope).isEqualTo(Any::class)

            val factoryMethod = clazz.declaredMethods.single()

            assertThat(factoryMethod.returnType).isEqualTo(classLoader.loadClass("com.test.DefaultTestApi"))
            val firstParameter = factoryMethod.parameters[0]
            assertThat(firstParameter.annotations).isEmpty()

            val secondParameter = factoryMethod.parameters[1]
            val assisted = secondParameter.requireAnnotation<Assisted>()

            assertThat(assisted.value).isEqualTo("test")
        }
    }

    @Test
    fun `should fail on primary constructor not annotated with @AssistedInject`() {
        compileAnvil(
            """
            package com.test

            import dagger.assisted.Assisted
            import me.gulya.anvil.utils.ContributesAssistedFactory

            interface TestApi

            interface TestApiFactory {
                fun create(
                    bebe: String,
                    @Assisted("test") bebe2: String,
                ): TestApi
            }

            @ContributesAssistedFactory(Any::class)
            class DefaultTestApi constructor(
                @Assisted private val bebe: String,
                @Assisted("test") private val bebe2: String,
            ) : TestApi
        """,
        ) {
            assertThat(exitCode).isEqualTo(COMPILATION_ERROR)

            assertThat(messages).contains(
                "Primary constructor for @ContributesAssistedFactory must be annotated with @AssistedInject"
            )
        }
    }

    @Test
    fun `should fail on multiple constructors`() {
        compileAnvil(
            """
            package com.test

            import dagger.assisted.Assisted
            import dagger.assisted.AssistedInject
            import me.gulya.anvil.utils.ContributesAssistedFactory

            interface TestApi

            interface TestApiFactory {
                fun create(
                    bebe: String,
                    @Assisted("test") bebe2: String,
                ): TestApi
            }

            @ContributesAssistedFactory(Any::class)
            class DefaultTestApi @AssistedInject constructor(
                @Assisted private val bebe: String,
                @Assisted("test") private val bebe2: String,
            ) : TestApi {
                constructor() : this("", "")
            }
        """,
        ) {
            assertThat(exitCode).isEqualTo(COMPILATION_ERROR)

            assertThat(messages).contains(
                "Class annotated with @ContributesAssistedFactory must have a single primary constructor"
            )
        }
    }

    @Test
    fun `should fail on binding type not being an interface`() {
        compileAnvil(
            """
            package com.test

            import dagger.assisted.Assisted
            import dagger.assisted.AssistedInject
            import me.gulya.anvil.utils.ContributesAssistedFactory

            interface TestApi

            abstract class TestApiFactory {
                fun create(
                    bebe: String,
                    @Assisted("test") bebe2: String,
                ): TestApi
            }

            @ContributesAssistedFactory(Any::class, TestApiFactory::class)
            class DefaultTestApi @AssistedInject constructor(
                @Assisted private val bebe: String,
                @Assisted("test") private val bebe2: String,
            ) : TestApi
        """,
        ) {
            assertThat(exitCode).isEqualTo(COMPILATION_ERROR)

            assertThat(messages).contains(
                "The bound type for @ContributesAssistedFactory must be an interface"
            )
        }
    }

    @Test
    fun `bound factory interface must have single abstract method`() {
        compileAnvil(
            """
            package com.test

            import dagger.assisted.Assisted
            import dagger.assisted.AssistedInject
            import me.gulya.anvil.utils.ContributesAssistedFactory

            interface TestApi

            interface TestApiFactory {
                fun create(
                    bebe: String,
                    @Assisted("test") bebe2: String,
                ): TestApi

                fun create2(
                    bebe: String,
                    @Assisted("test") bebe2: String,
                ): TestApi
            }

            @ContributesAssistedFactory(Any::class, TestApiFactory::class)
            class DefaultTestApi @AssistedInject constructor(
                @Assisted private val bebe: String,
                @Assisted("test") private val bebe2: String,
            ) : TestApi
        """,
        ) {
            assertThat(exitCode).isEqualTo(COMPILATION_ERROR)

            assertThat(messages).contains(
                "The bound type for @ContributesAssistedFactory must have a single abstract method"
            )
        }
    }

    @Test
    fun `bound factory interface method must have same number of parameters as primary constructor assisted parameters`() {
        compileAnvil(
            """
            package com.test

            import dagger.assisted.Assisted
            import dagger.assisted.AssistedInject
            import me.gulya.anvil.utils.ContributesAssistedFactory

            interface TestApi

            interface TestApiFactory {
                fun create2(
                    bebe: String,
                    bebe2: String,
                    bebe3: String,
                ): TestApi
            }

            @ContributesAssistedFactory(Any::class, TestApiFactory::class)
            class DefaultTestApi @AssistedInject constructor(
                @Assisted private val bebe: String,
                @Assisted("test") private val bebe2: String,
                private val bebe3: String,
            ) : TestApi
        """,
        ) {
            assertThat(exitCode).isEqualTo(COMPILATION_ERROR)

            assertThat(messages).contains(
                "Mismatch in number of parameters: the constructor of the annotated class with @ContributesAssistedFactory has 2 @Assisted parameters, " +
                        "but the factory method in com.test.TestApiFactory expects 3 parameters. " +
                        "Please ensure they have the same number of parameters."
            )
        }
    }

    @Test
    fun `bound factory interface method must have same parameters as primary constructor assisted parameters`() {
        compileAnvil(
            """
            package com.test

            import dagger.assisted.Assisted
            import dagger.assisted.AssistedInject
            import me.gulya.anvil.utils.ContributesAssistedFactory

            interface TestApi

            interface TestApiFactory {
                fun create(
                    bebe: String,
                    bebe2: String,
                ): TestApi
            }

            @ContributesAssistedFactory(Any::class, TestApiFactory::class)
            class DefaultTestApi @AssistedInject constructor(
                @Assisted private val bebe: String,
                @Assisted("test") private val bebe2: String,
                private val bebe3: String,
            ) : TestApi
        """,
        ) {
            assertThat(exitCode).isEqualTo(COMPILATION_ERROR)

            assertThat(messages).contains(
                "The bound type for @ContributesAssistedFactory must have a single abstract method with the same " +
                        "parameters as the primary constructor of the annotated class"
            )
        }
    }
}

inline fun <reified T> AnnotatedElement.annotationOrNull(): T? =
    annotations.singleOrNull { it.annotationClass == T::class } as? T

inline fun <reified T> AnnotatedElement.requireAnnotation(): T =
    requireNotNull(annotationOrNull<T>()) { "Couldn't find annotation ${T::class}" }