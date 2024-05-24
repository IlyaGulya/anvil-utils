import com.google.auto.service.AutoService
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.GeneratedFileWithSources
import com.squareup.anvil.compiler.api.createGeneratedFile
import com.squareup.anvil.compiler.internal.buildFile
import com.squareup.anvil.compiler.internal.fqName
import com.squareup.anvil.compiler.internal.reference.*
import com.squareup.kotlinpoet.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import me.gulya.anvil.assisted.AssistedKey
import me.gulya.anvil.assisted.ContributesAssistedFactory
import me.gulya.anvil.utils.ParameterKey
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

private val contributesAssistedFactoryFqName = ContributesAssistedFactory::class.fqName

@Suppress("unused")
@AutoService(CodeGenerator::class)
class ContributesAssistedFactoryCodeGenerator : CodeGenerator {

    override fun isApplicable(context: AnvilContext): Boolean = true

    override fun generateCode(
        codeGenDir: File,
        module: ModuleDescriptor,
        projectFiles: Collection<KtFile>,
    ): Collection<GeneratedFileWithSources> {
        return projectFiles.classAndInnerClassReferences(module)
            .filter { it.isAnnotatedWith(contributesAssistedFactoryFqName) }
            .map { generateAssistedFactory(it, codeGenDir) }
            .toList()
    }

    private fun generateAssistedFactory(
        assistedFactoryClass: ClassReference,
        codeGenDir: File,
    ): GeneratedFileWithSources {
        val generatedPackage = assistedFactoryClass.packageFqName.toString()
        val factoryClassName = "${assistedFactoryClass.shortName}_AssistedFactory"

        val annotation = assistedFactoryClass.annotations
            .single { it.fqName == contributesAssistedFactoryFqName }
        val scope =
            annotation
                .scope()
                .asClassName()

        val generationDetails = ContributesAssistedFactoryValidator(
            annotation = annotation,
            assistedFactoryClass = assistedFactoryClass
        ).validate()

        val boundType = generationDetails.boundType
        val boundTypeName = boundType.asTypeName()
        val typeBuilder =
            if (boundType.isInterface()) {
                TypeSpec
                    .interfaceBuilder(factoryClassName)
                    .addSuperinterface(boundTypeName)
            } else {
                TypeSpec
                    .classBuilder(factoryClassName)
                    .addModifiers(KModifier.ABSTRACT)
                    .superclass(boundTypeName)
            }

        val content = FileSpec.buildFile(generatedPackage, factoryClassName) {
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
                            .builder(generationDetails.factoryMethod.name)
                            .addModifiers(KModifier.OVERRIDE, KModifier.ABSTRACT)
                            .apply {
                                generationDetails.factoryMethod.parameters.forEach { parameter ->
                                    val type = parameter.type().asTypeName()
                                    addParameter(
                                        ParameterSpec
                                            .builder(parameter.name, type)
                                            .assisted(parameter.assistedKeyValue())
                                            .build(),
                                    )
                                }
                            }
                            .returns(assistedFactoryClass.asClassName())
                            .build(),
                    )
                    .build(),
            )
        }
        return createGeneratedFile(
            codeGenDir = codeGenDir,
            packageName = generatedPackage,
            fileName = factoryClassName,
            content = content,
            sourceFile = assistedFactoryClass.containingFileAsJavaFile,
        )
    }

    internal class ContributesAssistedFactoryValidator(
        private val annotation: AnnotationReference,
        private val assistedFactoryClass: ClassReference,
    ) {
        fun validate(): GenerationDetails {
            val boundType = annotation.boundTypeOrNull()

            boundType ?: throw AnvilCompilationExceptionAnnotationReference(
                annotation,
                "The @ContributesAssistedFactory annotation on class '${assistedFactoryClass.shortName}' " +
                        "must have a 'boundType' parameter",
            )

            val primaryConstructor = assistedFactoryClass.constructors.singleOrNull()

            primaryConstructor ?: throw AnvilCompilationExceptionClassReference(
                assistedFactoryClass,
                "Class '${assistedFactoryClass.shortName}' annotated with @ContributesAssistedFactory " +
                        "must have a single primary constructor",
            )

            if (!primaryConstructor.isAnnotatedWith(AssistedInject::class.fqName)) {
                throw AnvilCompilationExceptionFunctionReference(
                    primaryConstructor,
                    "Class '${assistedFactoryClass.shortName}' annotated with @ContributesAssistedFactory " +
                            "must have its primary constructor annotated with @AssistedInject",
                )
            }

            if (!boundType.isAbstract() && !boundType.isInterface()) {
                throw AnvilCompilationExceptionAnnotationReference(
                    annotation,
                    "The bound type '${boundType.shortName}' for @ContributesAssistedFactory on class " +
                            "'${assistedFactoryClass.shortName}' must be an abstract class or interface",
                )
            }

            val factoryMethod = boundType.functions.singleOrNull { it.isAbstract() }

            factoryMethod ?: throw AnvilCompilationExceptionClassReference(
                boundType,
                "The bound type '${boundType.shortName}' for @ContributesAssistedFactory " +
                        "must have a single abstract method",
            )
            val factoryMethodParameters = factoryMethod.parameters
            val constructorParameters = primaryConstructor.parameters
                .filter { it.isAnnotatedWith(Assisted::class.fqName) }
                .associateBy { ParameterKey(it.type().asTypeName(), it.assistedValue()) }

            if (constructorParameters.size != factoryMethodParameters.size) {
                throw AnvilCompilationExceptionFunctionReference(
                    factoryMethod,
                    "The assisted factory method parameters in '${boundType.shortName}.${factoryMethod.name}' " +
                            "must match the @Assisted parameters in the primary constructor of " +
                            "'${assistedFactoryClass.shortName}'",
                )
            }

            factoryMethodParameters.forEach { factoryParameter ->
                val isAnnotatedWithDaggerAssisted = factoryParameter.isAnnotatedWith(Assisted::class.fqName)
                val isAnnotatedWithAssistedKey = factoryParameter.isAnnotatedWith(AssistedKey::class.fqName)
                if (isAnnotatedWithDaggerAssisted && !isAnnotatedWithAssistedKey) {
                    throw AnvilCompilationExceptionParameterReference(
                        factoryParameter,
                        "The parameter '${factoryParameter.name}' in the factory method " +
                                "'${boundType.shortName}.${factoryMethod.name}' must be annotated with " +
                                "@${AssistedKey::class.simpleName} instead of @${Assisted::class.simpleName} " +
                                "to avoid conflicts with Dagger's @${AssistedFactory::class.simpleName} annotation",
                    )
                }

                val assistedKey = factoryParameter.assistedKeyValue()
                val constructorParameter = constructorParameters[factoryParameter.asParameterKey { assistedKey }]

                constructorParameter ?: throw AnvilCompilationExceptionParameterReference(
                    factoryParameter,
                    "The factory method parameter '${factoryParameter.name}' does not match any @Assisted parameter " +
                            "in the primary constructor of '${assistedFactoryClass.shortName}'",
                )

                // TODO: Improve heuristics for better error messages
//                val factoryParameterTypeName = factoryParameter.type().asTypeName()
//                val constructorParameterTypeName = constructorParameter.type().asTypeName()
//                if (constructorParameterTypeName != factoryParameterTypeName) {
//                    throw AnvilCompilationExceptionParameterReference(
//                        factoryParameter,
//                        "The type of factory method parameter '${factoryParameter.name}' in " +
//                                "'${boundType.shortName}.${factoryMethod.name}' must match the type of the corresponding " +
//                                "@Assisted parameter in the primary constructor of '${assistedFactoryClass.shortName}'. " +
//                                "Expected: $constructorParameterTypeName, Found: $factoryParameterTypeName",
//                    )
//                }

//                if (factoryParameter.assistedKeyValue() != constructorParameter.assistedValue()) {
//                    val clarification =
//                        when {
//                            factoryParameter.assistedKeyValue() == null -> "Expected @AssistedKey(\"$assistedKey\") annotation on the '${boundType.shortName}.${factoryMethod.name}' parameter '${factoryParameter.name}'"
//                            constructorParameter.assistedValue() == null -> "Expected @Assisted annotation on the '${assistedFactoryClass.shortName}' primary constructor parameter '${constructorParameter.name}'"
//                            else -> null
//                        }
//
//                    val actualClarification = clarification?.let { ". $it" } ?: ""
//
//                    throw AnvilCompilationExceptionParameterReference(
//                        factoryParameter,
//                        "The @Assisted annotation value for parameter '${factoryParameter.name}' in the primary constructor " +
//                                "of '${assistedFactoryClass.shortName}' must match the value on the corresponding parameter " +
//                                "in the factory method '${boundType.shortName}.${factoryMethod.name}'" +
//                                actualClarification,
//                    )
//                }
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
    val boundType: ClassReference,
    val factoryMethod: MemberFunctionReference,
    val factoryParameters: Map<ParameterKey, ParameterReference>,
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

private fun ParameterReference.asParameterKey(keyFactory: (ParameterReference) -> String?): ParameterKey {
    return ParameterKey(type().asTypeName(), keyFactory(this))
}

private fun ParameterReference.assistedValue(): String? {
    return annotationStringValue<Assisted>()
}

private fun ParameterReference.assistedKeyValue(): String? {
    return annotationStringValue<AssistedKey>()
}

private inline fun <reified T> AnnotatedReference.annotationStringValue(): String? {
    return annotations
        .singleOrNull { it.fqName == T::class.fqName }
        ?.argumentAt("value", 0)
        ?.value<String>()
        ?.takeIf { it.isNotBlank() }
}
