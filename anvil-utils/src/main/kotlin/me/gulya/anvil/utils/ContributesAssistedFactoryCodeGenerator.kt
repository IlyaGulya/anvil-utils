import com.google.auto.service.AutoService
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.GeneratedFile
import com.squareup.anvil.compiler.api.createGeneratedFile
import com.squareup.anvil.compiler.internal.buildFile
import com.squareup.anvil.compiler.internal.fqName
import com.squareup.anvil.compiler.internal.reference.*
import com.squareup.kotlinpoet.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import me.gulya.anvil.utils.ContributesAssistedFactory
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
    ): Collection<GeneratedFile> {
        return projectFiles.classAndInnerClassReferences(module)
            .filter { it.isAnnotatedWith(contributesAssistedFactoryFqName) }
            .map { generateAssistedFactory(it, codeGenDir) }
            .toList()
    }

    private fun generateAssistedFactory(
        assistedFactoryClass: ClassReference,
        codeGenDir: File,
    ): GeneratedFile {
        val generatedPackage = assistedFactoryClass.packageFqName.toString()
        val factoryClassName = "${assistedFactoryClass.shortName}_AssistedFactory"

        val annotation = assistedFactoryClass.annotations
            .single { it.fqName == contributesAssistedFactoryFqName }
        val scope =
            annotation
                .scope()
                .asClassName()

        val boundType = annotation.boundTypeOrNull()
        val factoryMethod = boundType?.functions?.singleOrNull { it.isAbstract() }

        val primaryConstructor = assistedFactoryClass.constructors.singleOrNull()

        primaryConstructor ?: throw AnvilCompilationExceptionAnnotationReference(
            annotation,
            "Class annotated with @ContributesAssistedFactory must have a single primary constructor",
        )

        if (!primaryConstructor.isAnnotatedWith(AssistedInject::class.fqName)) {
            throw AnvilCompilationExceptionAnnotationReference(
                annotation,
                "Primary constructor for @ContributesAssistedFactory must be annotated with @AssistedInject",
            )
        }

        val assistedParameters = primaryConstructor.parameters
            .filter { it.isAnnotatedWith(Assisted::class.fqName) }

        val isBoundTypeSpecified = boundType != null && boundType.fqName != Nothing::class.fqName

        if (boundType != null && isBoundTypeSpecified) {
            if (!boundType.isInterface()) {
                throw AnvilCompilationExceptionAnnotationReference(
                    annotation,
                    "The bound type for @ContributesAssistedFactory must be an interface",
                )
            }

            factoryMethod ?: throw AnvilCompilationExceptionAnnotationReference(
                annotation,
                "The bound type for @ContributesAssistedFactory must have a single abstract method",
            )

            val factoryMethodParameters = factoryMethod.parameters

            if (assistedParameters.size != factoryMethodParameters.size) {
                throw AnvilCompilationExceptionAnnotationReference(
                    annotation,
                    "Mismatch in number of parameters: the constructor of the annotated class with @ContributesAssistedFactory has ${assistedParameters.size} @Assisted parameters, " +
                            "but the factory method in ${boundType.asTypeName()} expects ${factoryMethodParameters.size} parameters. " +
                            "Please ensure they have the same number of parameters."
                )
            }

            assistedParameters.forEachIndexed { index, assistedParameter ->
                val factoryParameter = factoryMethodParameters[index]
                if (assistedParameter.type() != factoryParameter.type()) {
                    throw AnvilCompilationExceptionAnnotationReference(
                        annotation,
                        """Type mismatch for parameter at position ${index + 1}: the constructor of the annotated class with @ContributesAssistedFactory has a parameter of type ${
                            assistedParameter.type().asTypeName()
                        }, """ +
                                "but the corresponding parameter in the factory method of ${boundType.asTypeName()} is of type ${
                                    factoryParameter.type().asTypeName()
                                }. " +
                                "Please ensure the parameter types match."
                    )
                }
            }


//
//
//            val missingAssistedParams = factoryMethod.parameters
//                .filter { it.name !in assistedParamsByNames }
//                .map { it.name }
//
//            val excessAssistedParams = assistedParameters
//                .filter { it.name !in boundFactoryParamsByNames }
//                .map { it.name }
//
//            // TODO: Check assignability instead of just type equality
//            val incompatiblyTypedParams = factoryMethod.parameters
//                .filter { it.name in assistedParamsByNames }
//                .filter { assistedParamsByNames.getValue(it.name).type() != it.type() }
//
//            val formattedMissingParams =
//                if (missingAssistedParams.isNotEmpty()) {
//                    "Missing assisted parameters: ${missingAssistedParams.joinToString()}"
//                } else {
//                    null
//                }
//
//            val formattedExcessParams =
//                if (excessAssistedParams.isNotEmpty()) {
//                    "Excess assisted parameters: ${excessAssistedParams.joinToString()}"
//                } else {
//                    null
//                }
//
//            val formattedIncompatiblyTypedParams =
//                if (incompatiblyTypedParams.isNotEmpty()) {
//                    incompatiblyTypedParams.joinToString(separator = ". ") { param ->
//                        "Parameter ${param.name} is of type ${param.type()} in the factory method, but is of type ${assistedParamsByNames.getValue(param.name).type()} in the assisted constructor"
//                    }
//                } else {
//                    null
//                }
//
//            val errorMessages = listOfNotNull(
//                formattedMissingParams,
//                formattedExcessParams,
//                formattedIncompatiblyTypedParams,
//            )
//
//            if (errorMessages.isNotEmpty()) {
//                throw AnvilCompilationExceptionAnnotationReference(
//                    annotation,
//                    errorMessages.joinToString(separator = ". "),
//                )
//            }
        }


        val functionBuilder =
            if (factoryMethod != null) {
                FunSpec
                    .builder(factoryMethod.name)
                    .addModifiers(KModifier.OVERRIDE)
            } else {
                FunSpec.builder("create")
            }

        val content = FileSpec.buildFile(generatedPackage, factoryClassName) {
            addType(
                TypeSpec.funInterfaceBuilder(factoryClassName)
                    .apply {
                        if (boundType != null && isBoundTypeSpecified) {
                            val boundTypeName = boundType.asTypeName()
                            addSuperinterface(boundTypeName)
                            addAnnotation(
                                AnnotationSpec
                                    .builder(ContributesBinding::class)
                                    .addClassMember(scope)
                                    .addClassMember(boundTypeName)
                                    .build(),
                            )
                        } else {
                            addAnnotation(
                                AnnotationSpec
                                    .builder(ContributesTo::class)
                                    .addClassMember(scope)
                                    .build(),
                            )
                        }
                    }
                    .addAnnotation(AssistedFactory::class)
                    .addFunction(
                        functionBuilder
                            .addModifiers(KModifier.ABSTRACT)
                            .apply {
                                assistedParameters.forEach { parameter ->
                                    addParameter(
                                        ParameterSpec
                                            .builder(
                                                parameter.name,
                                                parameter.type().asTypeName(),
                                            )
                                            .assisted(parameter.assistedValue())
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
        return createGeneratedFile(codeGenDir, generatedPackage, factoryClassName, content)
    }

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

    private fun ParameterReference.assistedValue(): String? {
        return annotations
            .single { it.fqName == Assisted::class.fqName }
            .argumentAt("value", 0)
            ?.value<String>()
            ?.takeIf { it.isNotBlank() }
    }
}