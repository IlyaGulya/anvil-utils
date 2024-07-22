package me.gulya.anvil.utils.ksp

import com.google.auto.service.AutoService
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.isDefault
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueArgument
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import me.gulya.anvil.assisted.AssistedKey
import me.gulya.anvil.assisted.ContributesAssistedFactory
import me.gulya.anvil.utils.BoundType
import me.gulya.anvil.utils.FactoryMethod
import me.gulya.anvil.utils.FactoryMethodParameter
import me.gulya.anvil.utils.ParameterKey
import me.gulya.anvil.utils.createAssistedFactory
import me.gulya.anvil.utils.ksp.internal.ErrorLoggingSymbolProcessor
import me.gulya.anvil.utils.ksp.internal.SymbolProcessingException

private val contributesAssistedFactoryFqName = ContributesAssistedFactory::class.asClassName()

@Suppress("unused")
internal class ContributesAssistedFactorySymbolProcessor(
    override val env: SymbolProcessorEnvironment,
) : ErrorLoggingSymbolProcessor() {

    @Suppress("unused")
    @AutoService(SymbolProcessorProvider::class)
    class Provider : SymbolProcessorProvider {
        override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
            return ContributesAssistedFactorySymbolProcessor(environment)
        }
    }

    override fun processChecked(resolver: Resolver): List<KSAnnotated> {
        resolver.getSymbolsWithAnnotation(contributesAssistedFactoryFqName.reflectionName())
            .filterIsInstance<KSClassDeclaration>()
            .forEach { annotated ->
                generateAssistedFactory(annotated)
                    .writeTo(env.codeGenerator, Dependencies(false, annotated.containingFile!!))
            }

        return emptyList()
    }

    private fun generateAssistedFactory(
        annotatedClass: KSClassDeclaration,
    ): FileSpec {
        val annotation = annotatedClass.annotations.single {
            it.annotationType.resolve().toClassName() == contributesAssistedFactoryFqName
        }

        val generationDetails = ContributesAssistedFactoryValidator(
            annotation = annotation,
            assistedFactoryClass = annotatedClass
        ).validate()

        val factory = createAssistedFactory(
            annotatedName = annotatedClass.toClassName(),
            boundType = generationDetails.boundType.run {
                BoundType(
                    name = toClassName(),
                    isInterface = classKind == ClassKind.INTERFACE,
                )
            },
            scope = annotation.scope().toClassName(),
            factoryMethod = generationDetails.factoryMethod.run {
                FactoryMethod(
                    name = simpleName.asString(),
                    parameters = parameters.map { parameter ->
                        FactoryMethodParameter(
                            type = parameter.type.toTypeName(),
                            name = parameter.name!!.asString(),
                            assistedKeyValue = parameter.assistedKeyValue(),
                        )
                    }
                )
            },
        )
        return factory.spec
    }

    internal class ContributesAssistedFactoryValidator(
        private val annotation: KSAnnotation,
        private val assistedFactoryClass: KSClassDeclaration,
    ) {
        @OptIn(KspExperimental::class)
        fun validate(): GenerationDetails {
            val boundType = annotation.boundTypeOrNull()?.declaration

            boundType ?: throw SymbolProcessingException(
                annotation,
                Errors.missingBoundType(assistedFactoryClass.simpleName.asString()),
            )

            if (boundType !is KSClassDeclaration) {
                throw SymbolProcessingException(
                    annotation,
                    Errors.boundTypeMustBeClassOrInterface(boundType.simpleName.asString()),
                )
            }

            val primaryConstructor = assistedFactoryClass.primaryConstructor
            val hasMoreThanOneConstructor = assistedFactoryClass.getConstructors().toList().size != 1

            if (primaryConstructor == null || hasMoreThanOneConstructor) {
                throw SymbolProcessingException(
                    assistedFactoryClass,
                    Errors.mustHaveSinglePrimaryConstructor(assistedFactoryClass.simpleName.asString()),
                )
            }

            if (!primaryConstructor.isAnnotationPresent(AssistedInject::class)) {
                throw SymbolProcessingException(
                    primaryConstructor,
                    Errors.primaryConstructorMustBeAnnotatedWithAssistedInject(assistedFactoryClass.simpleName.asString()),
                )
            }

            if (!boundType.isAbstract()) {
                throw SymbolProcessingException(
                    annotation,
                    Errors.boundTypeMustBeAbstractOrInterface(
                        boundType.simpleName.asString(),
                        assistedFactoryClass.simpleName.asString(),
                    ),
                )
            }

            val factoryMethod = boundType.getAllFunctions().singleOrNull { it.isAbstract }

            factoryMethod ?: throw SymbolProcessingException(
                boundType,
                Errors.boundTypeMustHasSingleAbstractMethod(boundType.simpleName.asString()),
            )
            val factoryMethodParameters = factoryMethod.parameters
            val constructorParameters = primaryConstructor.parameters
                .filter { it.isAnnotationPresent(Assisted::class) }
                .associateBy { ParameterKey(it.type.resolve().toTypeName(), it.assistedValue()) }

            if (constructorParameters.size != factoryMethodParameters.size) {
                throw SymbolProcessingException(
                    factoryMethod,
                    Errors.parameterMismatch(
                        boundType.simpleName.asString(),
                        factoryMethod.simpleName.asString(),
                        assistedFactoryClass.simpleName.asString(),
                    ),
                )
            }

            factoryMethodParameters.forEach { factoryParameter ->
                val isAnnotatedWithDaggerAssisted = factoryParameter.isAnnotationPresent(Assisted::class)
                val isAnnotatedWithAssistedKey = factoryParameter.isAnnotationPresent(AssistedKey::class)
                if (isAnnotatedWithDaggerAssisted && !isAnnotatedWithAssistedKey) {
                    throw SymbolProcessingException(
                        factoryParameter,
                        Errors.parameterMustBeAnnotatedWithAssistedKey(
                            factoryParameter.name!!.asString(),
                            boundType.simpleName.asString(),
                            factoryMethod.simpleName.asString(),
                        ),
                    )
                }

                val assistedKey = factoryParameter.assistedKeyValue()
                val constructorParameter = constructorParameters[factoryParameter.asParameterKey { assistedKey }]

                constructorParameter ?: throw SymbolProcessingException(
                    factoryParameter,
                    Errors.parameterDoesNotMatchAssistedParameter(
                        factoryParameter.name!!.asString(),
                        assistedFactoryClass.simpleName.asString(),
                    ),
                )
            }

            return GenerationDetails(
                boundType = boundType,
                factoryMethod = factoryMethod,
                factoryParameters = constructorParameters,
            )
        }
    }
}

internal data class GenerationDetails(
    val boundType: KSClassDeclaration,
    val factoryMethod: KSFunctionDeclaration,
    val factoryParameters: Map<ParameterKey, KSValueParameter>,
)

private fun KSValueParameter.asParameterKey(keyFactory: (KSValueParameter) -> String?): ParameterKey {
    return ParameterKey(type.resolve().toTypeName(), keyFactory(this))
}

private fun KSValueParameter.assistedValue(): String? {
    return annotationStringValue<Assisted>()
}

private fun KSValueParameter.assistedKeyValue(): String? {
    return annotationStringValue<AssistedKey>()
}

private inline fun <reified T> KSAnnotated.annotationStringValue(): String? {
    val value = annotations
        .singleOrNull { it.annotationType.resolve().toClassName() == T::class.asClassName() }
        ?.argumentAt("value")
        ?.value
    return (value as String?)?.takeIf { it.isNotBlank() }
}

internal fun KSAnnotation.scope(): KSType =
    scopeOrNull()
        ?: throw SymbolProcessingException(
            this,
            "Couldn't find scope for ${annotationType.resolve().declaration.qualifiedName}.",
        )

internal fun KSAnnotation.scopeOrNull(): KSType? {
    return argumentAt("scope")?.value as? KSType?
}

internal fun KSAnnotation.argumentAt(
    name: String,
): KSValueArgument? {
    arguments
    return arguments.find { it.name?.asString() == name }
        ?.takeUnless { it.isDefault() }
}

internal fun KSAnnotation.boundTypeOrNull(): KSType? = argumentAt("boundType")?.value as? KSType?
