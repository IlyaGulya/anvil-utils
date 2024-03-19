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

        val boundType = annotation.boundTypeOrNull()?.takeIf {
            it.fqName != Nothing::class.fqName
        }
        val factoryMethod = boundType?.functions?.singleOrNull { it.isAbstract() }

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

        val assistedParameters = primaryConstructor.parameters
            .filter { it.isAnnotatedWith(Assisted::class.fqName) }

        if (boundType != null) {
            BoundTypeValidator(
                boundType = boundType,
                assistedParameters = assistedParameters,
                annotation = annotation,
                assistedFactoryClass = assistedFactoryClass
            ).validate()
        }

        val functionBuilder =
            if (factoryMethod != null) {
                FunSpec
                    .builder(factoryMethod.name)
                    .addModifiers(KModifier.OVERRIDE)
            } else {
                FunSpec.builder("create")
            }

        val typeBuilder =
            if (boundType == null) {
                TypeSpec
                    .funInterfaceBuilder(factoryClassName)
                    .addAnnotation(
                        AnnotationSpec
                            .builder(ContributesTo::class)
                            .addClassMember(scope)
                            .build(),
                    )
            } else {
                val boundTypeName = boundType.asTypeName()
                val builder =
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

                builder
                    .addAnnotation(
                        AnnotationSpec
                            .builder(ContributesBinding::class)
                            .addClassMember(scope)
                            .addClassMember(boundTypeName)
                            .build(),
                    )
            }

        val content = FileSpec.buildFile(generatedPackage, factoryClassName) {
            addType(
                typeBuilder
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

    class BoundTypeValidator(
        private val boundType: ClassReference,
        private val assistedParameters: List<ParameterReference>,
        private val annotation: AnnotationReference,
        private val assistedFactoryClass: ClassReference,
    ) {
        fun validate() {
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

            if (assistedParameters.size != factoryMethodParameters.size) {
                throw AnvilCompilationExceptionFunctionReference(
                    factoryMethod,
                    "The assisted factory method parameters in '${boundType.shortName}.${factoryMethod.name}' " +
                            "must match the @Assisted parameters in the primary constructor of " +
                            "'${assistedFactoryClass.shortName}'",
                )
            }

            assistedParameters.forEachIndexed { index, assistedParameter ->
                val factoryParameter = factoryMethodParameters[index]
                if (assistedParameter.type().asTypeName() != factoryParameter.type().asTypeName()) {
                    throw AnvilCompilationExceptionParameterReference(
                        assistedParameter,
                        "The type of assisted parameter '${assistedParameter.name}' in the primary constructor " +
                                "of '${assistedFactoryClass.shortName}' must match the type of the corresponding parameter " +
                                "in the factory method '${boundType.shortName}.${factoryMethod.name}'. " +
                                "Expected: ${factoryParameter.type().asTypeName()}, Found: ${
                                    assistedParameter.type().asTypeName()
                                }",
                    )
                }
                if (assistedParameter.assistedValue() != factoryParameter.assistedValue()) {
                    throw AnvilCompilationExceptionParameterReference(
                        assistedParameter,
                        "The @Assisted annotation value for parameter '${assistedParameter.name}' in the primary constructor " +
                                "of '${assistedFactoryClass.shortName}' must match the value on the corresponding parameter " +
                                "in the factory method '${boundType.shortName}.${factoryMethod.name}'",
                    )
                }
            }
        }
    }

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
        .singleOrNull { it.fqName == Assisted::class.fqName }
        ?.argumentAt("value", 0)
        ?.value<String>()
        ?.takeIf { it.isNotBlank() }
}