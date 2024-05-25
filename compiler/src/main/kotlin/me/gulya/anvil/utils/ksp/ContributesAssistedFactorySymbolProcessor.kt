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
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.compiler.internal.createAnvilSpec
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import me.gulya.anvil.assisted.AssistedKey
import me.gulya.anvil.assisted.ContributesAssistedFactory
import me.gulya.anvil.utils.ParameterKey
import me.gulya.anvil.utils.ksp.internal.ErrorLoggingSymbolProcessor
import me.gulya.anvil.utils.ksp.internal.SymbolProcessingException

private val contributesAssistedFactoryFqName = ContributesAssistedFactory::class.asClassName()

@AutoService(SymbolProcessorProvider::class)
class Provider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return ContributesAssistedFactorySymbolProcessor(environment)
    }
}

@Suppress("unused")
internal class ContributesAssistedFactorySymbolProcessor(
    override val env: SymbolProcessorEnvironment,
) : ErrorLoggingSymbolProcessor() {

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
        assistedFactoryClass: KSClassDeclaration,
    ): FileSpec {
        val generatedPackage = assistedFactoryClass.packageName.asString()
        val factoryClassName = "${assistedFactoryClass.simpleName.asString()}_AssistedFactory"

        val annotation = assistedFactoryClass.annotations
            .single { it.annotationType.resolve().toClassName() == contributesAssistedFactoryFqName }
        val scope =
            annotation
                .scope()
                .toClassName()

        val generationDetails = ContributesAssistedFactoryValidator(
            annotation = annotation,
            assistedFactoryClass = assistedFactoryClass
        ).validate()

        val boundType = generationDetails.boundType
        val boundTypeName = boundType.toClassName()
        val typeBuilder =
            if (boundType.classKind == ClassKind.INTERFACE) {
                TypeSpec
                    .interfaceBuilder(factoryClassName)
                    .addSuperinterface(boundTypeName)
            } else {
                TypeSpec
                    .classBuilder(factoryClassName)
                    .addModifiers(KModifier.ABSTRACT)
                    .superclass(boundTypeName)
            }

        val content = FileSpec.createAnvilSpec(generatedPackage, factoryClassName) {
            addType(
                typeBuilder
                    .addAnnotation(
                        AnnotationSpec
                            .builder(ContributesBinding::class)
                            .addClassMember(scope)
                            .addClassMember(boundTypeName)
                            .build(),
                    )
                    .addAnnotation(AssistedFactory::class)
                    .addFunction(
                        FunSpec
                            .builder(generationDetails.factoryMethod.simpleName.asString())
                            .addModifiers(KModifier.OVERRIDE, KModifier.ABSTRACT)
                            .apply {
                                generationDetails.factoryMethod.parameters.forEach { parameter ->
                                    val type = parameter.type.toTypeName()
                                    addParameter(
                                        ParameterSpec
                                            .builder(parameter.name!!.asString(), type)
                                            .assisted(parameter.assistedKeyValue())
                                            .build(),
                                    )
                                }
                            }
                            .returns(assistedFactoryClass.toClassName())
                            .build(),
                    )
                    .build(),
            )
        }
        return content
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
//                .associateBy { ParameterKey(it.type.toTypeName(), it.assistedValue()) }

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

private fun KSValueParameter.asParameterKey(keyFactory: (KSValueParameter) -> String?): ParameterKey {
    return ParameterKey(type.toTypeName(), keyFactory(this))
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
