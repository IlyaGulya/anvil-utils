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
import java.lang.reflect.Modifier

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
                "@ContributesAssistedFactory-annotated class must have a single primary constructor"
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
                "The assisted factory method parameters must match the @Assisted parameters in the primary constructor"
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
                "The @Assisted annotation value for 'bebe2' must match the value on the " +
                        "corresponding factory method parameter"
            )
        }
    }

    // Claude 3 Generated

    @Test
    fun `an assisted factory with parameter without value is generated`() {
        compileAnvil(
            """
        package com.test
        
        import dagger.assisted.Assisted
        import dagger.assisted.AssistedInject
        import me.gulya.anvil.utils.ContributesAssistedFactory

        interface TestApi
        
        @ContributesAssistedFactory(Any::class)
        class DefaultTestApi @AssistedInject constructor(
            @Assisted private val bebe: String,
        ) : TestApi
        """,
        ) {
            assertThat(exitCode).isEqualTo(OK)

            val clazz = classLoader.loadClass("com.test.DefaultTestApi_AssistedFactory")
            val factoryMethod = clazz.declaredMethods.single()

            assertThat(factoryMethod.parameters).hasLength(1)
            val parameter = factoryMethod.parameters[0]
            assertThat(parameter.annotations).isEmpty()
        }
    }

    @Test
    fun `an assisted factory with primitive types is generated`() {
        compileAnvil(
            """
        package com.test
        
        import dagger.assisted.Assisted
        import dagger.assisted.AssistedInject
        import me.gulya.anvil.utils.ContributesAssistedFactory

        interface TestApi
        
        @ContributesAssistedFactory(Any::class)
        class DefaultTestApi @AssistedInject constructor(
            @Assisted private val intParam: Int,
            @Assisted private val booleanParam: Boolean,
        ) : TestApi
        """,
        ) {
            assertThat(exitCode).isEqualTo(OK)

            val clazz = classLoader.loadClass("com.test.DefaultTestApi_AssistedFactory")
            val factoryMethod = clazz.declaredMethods.single()

            assertThat(factoryMethod.parameters).hasLength(2)
            assertThat(factoryMethod.parameters[0].type).isEqualTo(Int::class.java)
            assertThat(factoryMethod.parameters[1].type).isEqualTo(Boolean::class.java)
        }
    }

    @Test
    fun `bound type must be an interface or abstract class`() {
        compileAnvil(
            """
        package com.test
        
        import dagger.assisted.Assisted
        import dagger.assisted.AssistedInject  
        import me.gulya.anvil.utils.ContributesAssistedFactory

        class TestApiFactory
        
        interface TestApi
        
        @ContributesAssistedFactory(Any::class, TestApiFactory::class)
        class DefaultTestApi @AssistedInject constructor(
            @Assisted private val param: String,
        ) : TestApi
        """,
        ) {
            assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
            assertThat(messages).contains(
                "The bound type for @ContributesAssistedFactory must be an abstract class or interface"
            )
        }
    }

    @Test
    fun `parameter types in primary constructor and factory method must match`() {
        compileAnvil(
            """
        package com.test
        
        import dagger.assisted.Assisted
        import dagger.assisted.AssistedInject
        import me.gulya.anvil.utils.ContributesAssistedFactory
        
        interface TestApi

        interface TestApiFactory {
            fun create(param: Int): TestApi 
        }
        
        @ContributesAssistedFactory(Any::class, TestApiFactory::class)
        class DefaultTestApi @AssistedInject constructor(
            @Assisted private val param: String,
        ) : TestApi
        """,
        ) {
            assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
            assertThat(messages).contains(
                "The assisted parameter 'param' type kotlin.String must match the factory method parameter type kotlin.Int"
            )
        }
    }

    @Test
    fun `generated factory is an interface if bound type is an interface, and an abstract class if bound type is an abstract class`() {
        compileAnvil(
            """
        package com.test
        
        import dagger.assisted.Assisted
        import dagger.assisted.AssistedInject
        import me.gulya.anvil.utils.ContributesAssistedFactory

        interface TestApi

        interface TestApiFactory {
            fun create(param: String): TestApi
        }
        
        @ContributesAssistedFactory(Any::class, TestApiFactory::class)
        class DefaultTestApi @AssistedInject constructor(
            @Assisted private val param: String,
        ) : TestApi
        """,
            """
        package com.test
        
        import dagger.assisted.Assisted
        import dagger.assisted.AssistedInject
        import me.gulya.anvil.utils.ContributesAssistedFactory

        interface TestApi2

        abstract class TestApi2Factory {
            abstract fun create(param: String): TestApi2
        }
        
        @ContributesAssistedFactory(Any::class, TestApi2Factory::class)
        class DefaultTestApi2 @AssistedInject constructor(
            @Assisted private val param: String,  
        ) : TestApi2
        """
        ) {
            assertThat(exitCode).isEqualTo(OK)

            val interfaceFactoryClass = classLoader.loadClass("com.test.DefaultTestApi_AssistedFactory")
            assertThat(interfaceFactoryClass.isInterface).isTrue()

            val abstractClassFactoryClass = classLoader.loadClass("com.test.DefaultTestApi2_AssistedFactory")
            assertThat(abstractClassFactoryClass.isInterface).isFalse()
            assertThat(Modifier.isAbstract(abstractClassFactoryClass.modifiers)).isTrue()
        }
    }
}

inline fun <reified T> AnnotatedElement.annotationOrNull(): T? =
    annotations.singleOrNull { it.annotationClass == T::class } as? T

inline fun <reified T> AnnotatedElement.requireAnnotation(): T =
    requireNotNull(annotationOrNull<T>()) { "Couldn't find annotation ${T::class}" }