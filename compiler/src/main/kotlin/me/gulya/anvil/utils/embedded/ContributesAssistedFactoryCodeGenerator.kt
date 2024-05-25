import com.google.auto.service.AutoService
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.GeneratedFileWithSources
import com.squareup.anvil.compiler.api.createGeneratedFile
import com.squareup.anvil.compiler.internal.buildFile
import com.squareup.anvil.compiler.internal.fqName
import com.squareup.anvil.compiler.internal.reference.AnnotatedReference
import com.squareup.anvil.compiler.internal.reference.AnnotationReference
import com.squareup.anvil.compiler.internal.reference.AnvilCompilationExceptionAnnotationReference
import com.squareup.anvil.compiler.internal.reference.AnvilCompilationExceptionClassReference
import com.squareup.anvil.compiler.internal.reference.AnvilCompilationExceptionFunctionReference
import com.squareup.anvil.compiler.internal.reference.AnvilCompilationExceptionParameterReference
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.internal.reference.MemberFunctionReference
import com.squareup.anvil.compiler.internal.reference.ParameterReference
import com.squareup.anvil.compiler.internal.reference.argumentAt
import com.squareup.anvil.compiler.internal.reference.asClassName
import com.squareup.anvil.compiler.internal.reference.asTypeName
import com.squareup.anvil.compiler.internal.reference.classAndInnerClassReferences
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
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
        fun validate(): KspGenerationDetails {
            val boundType = annotation.boundTypeOrNull()

            boundType ?: throw AnvilCompilationExceptionAnnotationReference(
                annotation,
                Errors.missingBoundType(assistedFactoryClass.shortName),
            )

            val primaryConstructor = assistedFactoryClass.constructors.singleOrNull()

            primaryConstructor ?: throw AnvilCompilationExceptionClassReference(
                assistedFactoryClass,
                Errors.mustHaveSinglePrimaryConstructor(assistedFactoryClass.shortName),
            )

            if (!primaryConstructor.isAnnotatedWith(AssistedInject::class.fqName)) {
                throw AnvilCompilationExceptionFunctionReference(
                    primaryConstructor,
                    Errors.primaryConstructorMustBeAnnotatedWithAssistedInject(assistedFactoryClass.shortName),
                )
            }

            if (!boundType.isAbstract() && !boundType.isInterface()) {
                throw AnvilCompilationExceptionAnnotationReference(
                    annotation,
                    Errors.boundTypeMustBeAbstractOrInterface(boundType.shortName, assistedFactoryClass.shortName),
                )
            }

            val factoryMethod = boundType.functions.singleOrNull { it.isAbstract() }

            factoryMethod ?: throw AnvilCompilationExceptionClassReference(
                boundType,
                Errors.boundTypeMustHasSingleAbstractMethod(boundType.shortName)
            )
            val factoryMethodParameters = factoryMethod.parameters
            val constructorParameters = primaryConstructor.parameters
                .filter { it.isAnnotatedWith(Assisted::class.fqName) }
                .associateBy { ParameterKey(it.type().asTypeName(), it.assistedValue()) }

            if (constructorParameters.size != factoryMethodParameters.size) {
                throw AnvilCompilationExceptionFunctionReference(
                    factoryMethod,
                    Errors.parameterMismatch(boundType.shortName, factoryMethod.name, assistedFactoryClass.shortName),
                )
            }

            factoryMethodParameters.forEach { factoryParameter ->
                val isAnnotatedWithDaggerAssisted = factoryParameter.isAnnotatedWith(Assisted::class.fqName)
                val isAnnotatedWithAssistedKey = factoryParameter.isAnnotatedWith(AssistedKey::class.fqName)
                if (isAnnotatedWithDaggerAssisted && !isAnnotatedWithAssistedKey) {
                    throw AnvilCompilationExceptionParameterReference(
                        factoryParameter,
                        Errors.parameterMustBeAnnotatedWithAssistedKey(
                            factoryParameter.name,
                            boundType.shortName,
                            factoryMethod.name
                        ),
                    )
                }

                val assistedKey = factoryParameter.assistedKeyValue()
                val constructorParameter = constructorParameters[factoryParameter.asParameterKey { assistedKey }]

                constructorParameter ?: throw AnvilCompilationExceptionParameterReference(
                    factoryParameter,
                    Errors.parameterDoesNotMatchAssistedParameter(
                        factoryParameter.name,
                        assistedFactoryClass.shortName
                    ),
                )
            }

            return KspGenerationDetails(
                boundType = boundType,
                factoryMethod = factoryMethod,
                factoryParameters = constructorParameters,
            )
        }
    }
}

internal data class KspGenerationDetails(
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
