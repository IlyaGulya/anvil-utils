package me.gulya.anvil.utils.embedded

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.GeneratedFileWithSources
import com.squareup.anvil.compiler.api.createGeneratedFile
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
import com.squareup.anvil.compiler.internal.reference.classAndInnerClassReferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import me.gulya.anvil.assisted.AssistedKey
import me.gulya.anvil.assisted.ContributesAssistedFactory
import me.gulya.anvil.utils.BoundType
import me.gulya.anvil.utils.FactoryMethod
import me.gulya.anvil.utils.FactoryMethodParameter
import me.gulya.anvil.utils.ParameterKey
import me.gulya.anvil.utils.createAssistedFactory
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
        annotatedClass: ClassReference,
        codeGenDir: File,
    ): GeneratedFileWithSources {
        val annotation = annotatedClass.annotations.single {
            it.fqName == contributesAssistedFactoryFqName
        }

        val generationDetails = ContributesAssistedFactoryValidator(
            annotation = annotation,
            assistedFactoryClass = annotatedClass
        ).validate()

        val factory = createAssistedFactory(
            annotatedName = annotatedClass.asClassName(),
            boundType = generationDetails.boundType.run {
                BoundType(
                    name = asClassName(),
                    isInterface = isInterface(),
                )
            },
            scope = annotation.scope().asClassName(),
            factoryMethod = generationDetails.factoryMethod.run {
                FactoryMethod(
                    name = name,
                    parameters = parameters.map { parameter ->
                        FactoryMethodParameter(
                            type = parameter.type().asTypeName(),
                            name = parameter.name,
                            assistedKeyValue = parameter.assistedKeyValue(),
                        )
                    }
                )
            },
        )

        return createGeneratedFile(
            codeGenDir = codeGenDir,
            packageName = factory.packageName,
            fileName = factory.name,
            content = factory.spec.toString(),
            sourceFile = annotatedClass.containingFileAsJavaFile,
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
