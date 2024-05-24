import androidx.lifecycle.ViewModel
import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.compiler.internal.testing.compileAnvil
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.asTypeName
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.COMPILATION_ERROR
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import org.junit.Test
import javax.inject.Provider
import kotlin.reflect.full.primaryConstructor

class ContributesViewModelCodeGeneratorTest {

    @Test
    fun `generates a static view model factory`() {
        compileAnvil(
            """
                @file:OptIn(ExperimentalAnvilUtilsApi::class)
           
                package com.test
                
                import androidx.lifecycle.ViewModel
                import me.gulya.anvil.vm.ContributesViewModel
                import me.gulya.anvil.api.ExperimentalAnvilUtilsApi
                import javax.inject.Inject
    

                @ContributesViewModel(Any::class)
                class TestViewModel @Inject constructor() : ViewModel()
            """,

        ) {
            assertThat(exitCode).isEqualTo(OK)

            val testViewModelClazz = classLoader.loadClass("com.test.TestViewModel").kotlin
            val viewModelFactoryClazz = classLoader.loadClass("me.gulya.anvil.vm.Any_ViewModelFactory").kotlin
            val viewModelProviderFactoryClazz = classLoader.loadClass("androidx.lifecycle.ViewModelProvider\$Factory")

            val primaryConstructor = viewModelFactoryClazz.primaryConstructor
            assertThat(primaryConstructor).isNotNull()

            assertThat(primaryConstructor!!.parameters.map { it.name to it.type.asTypeName() }).containsExactly(
                "testViewModelProvider" to Provider::class.parameterizedBy(testViewModelClazz)
            )

//            assertThat(viewModelFactoryClazz.interfaces).asList().containsExactly(viewModelProviderFactoryClazz)
//
//            val contributesBindingAnnotation = viewModelFactoryClazz.requireAnnotation<ContributesBinding>()
//            assertThat(contributesBindingAnnotation.scope).isEqualTo(Any::class)
//            assertThat(contributesBindingAnnotation.boundType.java).isEqualTo(viewModelProviderFactoryClazz)
//
//            val constructor = viewModelFactoryClazz.declaredConstructors.single()
//            val constructorParameter = constructor.parameters.single()
//            assertThat(constructorParameter.type).isEqualTo(Provider::class.java)
//
//            val createMethod = viewModelFactoryClazz.declaredMethods.first { it.name == "create" }
//            assertThat(createMethod.returnType).isEqualTo(ViewModel::class.java)
        }
    }

    @Test
    fun `generates a static view model factory for multiple view models`() {
        compileAnvil(
            """
                @file:OptIn(ExperimentalAnvilUtilsApi::class)
                package com.test
                
                import androidx.lifecycle.ViewModel
                import me.gulya.anvil.vm.ContributesViewModel
                import me.gulya.anvil.api.ExperimentalAnvilUtilsApi
                import javax.inject.Inject 

                @ContributesViewModel(Any::class)
                class TestViewModel1 @Inject constructor() : ViewModel()
                
                @ContributesViewModel(Any::class)
                class TestViewModel2 @Inject constructor() : ViewModel()
            """,
        ) {
            assertThat(exitCode).isEqualTo(OK)

            val viewModelFactoryClazz = classLoader.loadClass("me.gulya.anvil.vm.Any_ViewModelFactory")
            val viewModelProviderFactoryClazz = classLoader.loadClass("androidx.lifecycle.ViewModelProvider\$Factory")

            assertThat(viewModelFactoryClazz.interfaces).asList().containsExactly(viewModelProviderFactoryClazz)

            val contributesBindingAnnotation = viewModelFactoryClazz.requireAnnotation<ContributesBinding>()
            assertThat(contributesBindingAnnotation.scope).isEqualTo(Any::class)
            assertThat(contributesBindingAnnotation.boundType.java).isEqualTo(viewModelProviderFactoryClazz)

            val constructor = viewModelFactoryClazz.declaredConstructors.single()
            assertThat(constructor.parameters).hasLength(2)
            constructor.parameters.forEach { parameter ->
                assertThat(parameter.type).isEqualTo(Provider::class.java)
            }
        }
    }

    @Test
    fun `fails when view model class is not annotated with @Inject`() {
        val result = compileAnvil(
            """
                package com.test
                
                import androidx.lifecycle.ViewModel
                import me.gulya.anvil.vm.ContributesViewModel
    
                @ContributesViewModel(Any::class)
                class TestViewModel constructor() : ViewModel()
            """,
        )

        assertThat(result.exitCode).isEqualTo(COMPILATION_ERROR)
        assertThat(result.messages).contains(
            "Class 'TestViewModel' annotated with @ContributesViewModel must have its primary constructor annotated with @Inject"
        )
    }

    @Test
    fun `fails when view model class has multiple constructors`() {
        val result = compileAnvil(
            """
                package com.test
                
                import androidx.lifecycle.ViewModel
                import me.gulya.anvil.vm.ContributesViewModel
                import javax.inject.Inject
    
                @ContributesViewModel(Any::class)
                class TestViewModel @Inject constructor() : ViewModel() {
                    constructor(value: String) : this()
                }
            """,
        )

        assertThat(result.exitCode).isEqualTo(COMPILATION_ERROR)
        assertThat(result.messages).contains(
            "Class 'TestViewModel' annotated with @ContributesViewModel must have a single primary constructor"
        )
    }
}