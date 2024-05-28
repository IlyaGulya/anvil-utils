package me.gulya.anvil.utils

import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.compiler.internal.createAnvilSpec
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory

fun createAssistedFactory(
    annotatedName: ClassName,
    boundType: BoundType,
    scope: ClassName,
    factoryMethod: FactoryMethod,
): AssistedFactorySpec {
    val factoryClassName = "${annotatedName.simpleName}_AssistedFactory"

    val typeBuilder =
        if (boundType.isInterface) {
            TypeSpec
                .interfaceBuilder(factoryClassName)
                .addSuperinterface(boundType.name)
        } else {
            TypeSpec
                .classBuilder(factoryClassName)
                .addModifiers(KModifier.ABSTRACT)
                .superclass(boundType.name)
        }

    val spec = FileSpec.createAnvilSpec(annotatedName.packageName, factoryClassName) {
        addType(
            typeBuilder
                .addAnnotation(
                    AnnotationSpec
                        .builder(ContributesBinding::class)
                        .addClassMember(scope)
                        .addClassMember(boundType.name)
                        .build(),
                )
                .addAnnotation(AssistedFactory::class)
                .addFunction(
                    FunSpec
                        .builder(factoryMethod.name)
                        .addModifiers(KModifier.OVERRIDE, KModifier.ABSTRACT)
                        .apply {
                            factoryMethod.parameters.forEach { parameter ->
                                addParameter(
                                    ParameterSpec
                                        .builder(parameter.name, parameter.type)
                                        .assisted(parameter.assistedKeyValue)
                                        .build(),
                                )
                            }
                        }
                        .returns(annotatedName)
                        .build(),
                )
                .build(),
        )
    }
    return AssistedFactorySpec(
        name = factoryClassName,
        packageName = annotatedName.packageName,
        spec = spec,
    )
}

data class AssistedFactorySpec(
    val name: String,
    val packageName: String,
    val spec: FileSpec,
)

data class BoundType(
    val name: ClassName,
    val isInterface: Boolean,
)

data class FactoryMethod(
    val name: String,
    val parameters: List<FactoryMethodParameter>,
)

data class FactoryMethodParameter(
    val type: TypeName,
    val name: String,
    val assistedKeyValue: String?
)

private fun AnnotationSpec.Builder.addClassMember(
    member: TypeName,
): AnnotationSpec.Builder {
    addMember("%T::class", member)
    return this
}

private fun ParameterSpec.Builder.assisted(value: String?): ParameterSpec.Builder {
    if (value == null) return this
    addAnnotation(
        AnnotationSpec
            .builder(Assisted::class)
            .apply {
                addMember("%S", value)
            }
            .build(),
    )
    return this
}
