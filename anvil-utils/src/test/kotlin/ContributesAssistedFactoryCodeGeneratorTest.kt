import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.annotations.ContributesBinding
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
                import me.gulya.anvil.utils.AssistedKey

                interface TestApi

                interface TestApiFactory {
                    fun create(
                        bebe: String,
                        @AssistedKey("test") bebe2: String,
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
    fun `should fail on primary constructor not annotated with @AssistedInject`() {
        compileAnvil(
            """
            package com.test

            import dagger.assisted.Assisted
            import me.gulya.anvil.utils.ContributesAssistedFactory
            import me.gulya.anvil.utils.AssistedKey

            interface TestApi

            interface TestApiFactory {
                fun create(
                    bebe: String,
                    @AssistedKey("test") bebe2: String,
                ): TestApi
            }

            @ContributesAssistedFactory(Any::class, TestApiFactory::class)
            class DefaultTestApi constructor(
                @Assisted private val bebe: String,
                @Assisted("test") private val bebe2: String,
            ) : TestApi
        """,
        ) {
            assertThat(exitCode).isEqualTo(COMPILATION_ERROR)

            assertThat(messages).contains(
                "Class 'DefaultTestApi' annotated with @ContributesAssistedFactory must have its " +
                        "primary constructor annotated with @AssistedInject"
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
            import me.gulya.anvil.utils.AssistedKey

            interface TestApi

            interface TestApiFactory {
                fun create(
                    bebe: String,
                    @AssistedKey("test") bebe2: String,
                ): TestApi
            }

            @ContributesAssistedFactory(Any::class, TestApiFactory::class)
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
                "Class 'DefaultTestApi' annotated with @ContributesAssistedFactory must have " +
                        "a single primary constructor"
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
            import me.gulya.anvil.utils.AssistedKey

            interface TestApi

            interface TestApiFactory {
                fun create(
                    bebe: String,
                    @AssistedKey("test") bebe2: String,
                ): TestApi

                fun create2(
                    bebe: String,
                    @AssistedKey("test") bebe2: String,
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
                "The bound type 'TestApiFactory' for @ContributesAssistedFactory " +
                        "must have a single abstract method"
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
                "The assisted factory method parameters in 'TestApiFactory.create2' must match " +
                        "the @Assisted parameters in the primary constructor of 'DefaultTestApi'"
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
                @Assisted("bebe") private val bebe: String,
                @Assisted("bebe2") private val bebe2: String,
                private val bebe3: String,
            ) : TestApi
        """,
        ) {
            assertThat(exitCode).isEqualTo(COMPILATION_ERROR)

            assertThat(messages).contains(
                "The factory method parameter 'bebe' does not match any " +
                        "@Assisted parameter in the primary constructor of 'DefaultTestApi'"
//                "The @Assisted annotation value for parameter 'bebe2' in the primary constructor " +
//                        "of 'DefaultTestApi' must match the value on the corresponding parameter in " +
//                        "the factory method 'TestApiFactory.create'"
            )
        }
    }

    // Claude 3 Generated

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
                "The bound type 'TestApiFactory' for @ContributesAssistedFactory on class 'DefaultTestApi' " +
                        "must be an abstract class or interface"
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
                "The factory method parameter 'param' does not match any " +
                        "@Assisted parameter in the primary constructor of 'DefaultTestApi'"
//                "The type of assisted parameter 'param' in the primary constructor of 'DefaultTestApi' " +
//                        "must match the type of the corresponding parameter in the factory method 'TestApiFactory.create'. " +
//                        "Expected: kotlin.Int, Found: kotlin.String"
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
        """
        ) {
            assertThat(exitCode).isEqualTo(OK)

            val interfaceFactoryClass = classLoader.loadClass("com.test.DefaultTestApi_AssistedFactory")
            assertThat(interfaceFactoryClass.isInterface).isTrue()
        }
    }

    @Test
    fun `generated factory is an abstract class if bound type is an abstract class`() {
        compileAnvil(
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

            val abstractClassFactoryClass = classLoader.loadClass("com.test.DefaultTestApi2_AssistedFactory")
            assertThat(abstractClassFactoryClass.isInterface).isFalse()
            assertThat(Modifier.isAbstract(abstractClassFactoryClass.modifiers)).isTrue()
        }
    }

    @Test
    fun `factory method parameters must be annotated with @AssistedKey instead of @Assisted`() {
        compileAnvil(
            """
            package com.test

            import dagger.assisted.Assisted
            import dagger.assisted.AssistedInject
            import me.gulya.anvil.utils.ContributesAssistedFactory

            interface TestApi

            interface TestApiFactory {
                fun create(
                    @Assisted("test") assistedParam: Int
                ): TestApi
            }

            @ContributesAssistedFactory(Any::class, TestApiFactory::class)
            class DefaultTestApi @AssistedInject constructor(
                @Assisted("test") assistedParam: Int
            ) : TestApi
            """
        ) {
            assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
            assertThat(messages).contains(
                "The parameter 'assistedParam' in the factory method 'TestApiFactory.create' " +
                        "must be annotated with @AssistedKey instead of @Assisted to avoid conflicts " +
                        "with Dagger's @AssistedFactory annotation"
            )
        }
    }

    @Test
    fun `should fail on missing assisted key of constructor parameter`() {
        compileAnvil(
            """
        package com.test
        
        import dagger.assisted.AssistedInject
        import dagger.assisted.Assisted
        import me.gulya.anvil.utils.ContributesAssistedFactory
        import me.gulya.anvil.utils.AssistedKey
        
        interface TestApi
        
        interface TestApiFactory {
            fun create(
                @AssistedKey("param1") param1: String
            ): TestApi
        }
        
        @ContributesAssistedFactory(Any::class, TestApiFactory::class)
        class DefaultTestApi @AssistedInject constructor(
            @Assisted param1: String,
        ) : TestApi
        """
        ) {
            assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
            assertThat(messages).contains(
                "The factory method parameter 'param1' does not match any " +
                        "@Assisted parameter in the primary constructor of 'DefaultTestApi'"
            )
        }
    }

    @Test
    fun `generated factory method parameters match the order of the bound type factory method parameters`() {
        compileAnvil(
            """
        package com.test
        
        import dagger.assisted.AssistedInject
        import dagger.assisted.Assisted
        import me.gulya.anvil.utils.ContributesAssistedFactory
        import me.gulya.anvil.utils.AssistedKey
        
        interface TestApi
        
        interface TestApiFactory {
            fun create(
                @AssistedKey("param2") param2: Int,
                @AssistedKey("param3") param3: Boolean,
                @AssistedKey("param1") param1: String
            ): TestApi
        }
        
        @ContributesAssistedFactory(Any::class, TestApiFactory::class)
        class DefaultTestApi @AssistedInject constructor(
            @Assisted("param1") param1: String,
            @Assisted("param2") param2: Int,
            @Assisted("param3") param3: Boolean
        ) : TestApi
        """,
            allWarningsAsErrors = false,
        ) {
            assertThat(exitCode).isEqualTo(OK)

            val testApiFactoryClass = classLoader.loadClass("com.test.TestApiFactory")
            val generatedFactoryClass = classLoader.loadClass("com.test.DefaultTestApi_AssistedFactory")
            val generatedFactoryMethod = generatedFactoryClass.declaredMethods.single()

            assertThat(generatedFactoryMethod.parameterTypes).isEqualTo(
                testApiFactoryClass.declaredMethods.single().parameterTypes
            )

            assertThat(generatedFactoryMethod.parameters.map { it.getAnnotation(Assisted::class.java)?.value })
                .containsExactly(
                    "param2",
                    "param3",
                    "param1"
                )
        }
    }
}

inline fun <reified T> AnnotatedElement.annotationOrNull(): T? =
    annotations.singleOrNull { it.annotationClass == T::class } as? T

inline fun <reified T> AnnotatedElement.requireAnnotation(): T =
    requireNotNull(annotationOrNull<T>()) { "Couldn't find annotation ${T::class}" }