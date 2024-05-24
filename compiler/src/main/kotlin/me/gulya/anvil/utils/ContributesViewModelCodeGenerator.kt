import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.auto.service.AutoService
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.GeneratedFile
import com.squareup.anvil.compiler.api.createGeneratedFile
import com.squareup.anvil.compiler.internal.asClassName
import com.squareup.anvil.compiler.internal.buildFile
import com.squareup.anvil.compiler.internal.decapitalize
import com.squareup.anvil.compiler.internal.fqName
import com.squareup.anvil.compiler.internal.reference.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import dagger.assisted.AssistedInject
import me.gulya.anvil.vm.ContributesViewModel
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import javax.inject.Inject
import javax.inject.Provider

private val contributesViewModelFqName = ContributesViewModel::class.fqName
private val viewModelProviderFactoryFqName = ViewModelProvider.Factory::class.fqName
private val viewModelFqName = ViewModel::class.fqName
private val providerClassName = Provider::class.asClassName()

@Suppress("unused")
@AutoService(CodeGenerator::class)
class ContributesViewModelCodeGenerator : CodeGenerator {

    override fun isApplicable(context: AnvilContext): Boolean {
        return true
    }

    override fun generateCode(
        codeGenDir: File,
        module: ModuleDescriptor,
        projectFiles: Collection<KtFile>,
    ): Collection<GeneratedFile> {
        return projectFiles
            .classAndInnerClassReferences(module)
            .filter { it.isAnnotatedWith(contributesViewModelFqName) }
            .onEach { validateViewModel(it) }
            .groupBy {
                val annotation = it.annotations.first { it.fqName == contributesViewModelFqName }
                annotation
            }
            .map { (annotation, viewModels) ->
                generateStaticViewModelFactory(
                    annotation = annotation,
                    viewModels = viewModels,
                    codeGenDir = codeGenDir,
                    module = module,
                )
            }
    }

    private fun validateViewModel(vmClass: ClassReference) {
        val primaryConstructor = vmClass.constructors.singleOrNull()

        primaryConstructor ?: throw AnvilCompilationExceptionClassReference(
            vmClass,
            "Class '${vmClass.shortName}' annotated with @ContributesViewModel " +
                    "must have a single primary constructor"
        )

        // TODO: Support assisted factories
        if (primaryConstructor.isAnnotatedWith(AssistedInject::class.fqName)) {
            throw AnvilCompilationExceptionFunctionReference(
                primaryConstructor,
                "@AssistedInject is not currently supported by anvil-utils. " +
                        "Primary constructor of class '${vmClass.shortName}' annotated with @ContributesViewModel " +
                        "is annotated with @AssistedInject.",
            )
        }

        if (!primaryConstructor.isAnnotatedWith(Inject::class.fqName)) {
            throw AnvilCompilationExceptionFunctionReference(
                primaryConstructor,
                "Class '${vmClass.shortName}' annotated with @ContributesViewModel " +
                        "must have its primary constructor annotated with @Inject",
            )
        }
    }

    private fun generateStaticViewModelFactory(
        annotation: AnnotationReference,
        viewModels: List<ClassReference>,
        codeGenDir: File,
        module: ModuleDescriptor,
    ): GeneratedFile {
        val scope =
            annotation
                .scope()
                .asClassName()

        val generatedPackage = "me.gulya.anvil.vm"
        val vmFactoryClassName = "${scope.simpleName}_ViewModelFactory"

        val viewModelProviderFactoryClassName = viewModelProviderFactoryFqName.asClassName(module)
        val viewModelClassName = viewModelFqName.asClassName(module)

        fun ClassReference.asProviderName() =
            "${shortName.decapitalize()}Provider"

        fun ClassReference.asProviderType() =
            providerClassName.parameterizedBy(this.asClassName())

        val content = FileSpec.buildFile(generatedPackage, vmFactoryClassName) {
            addType(
                TypeSpec.classBuilder(vmFactoryClassName)
                    .addAnnotation(
                        AnnotationSpec
                            .builder(ContributesBinding::class)
                            .addClassMember(scope)
                            .addClassMember(viewModelProviderFactoryClassName)
                            .build(),
                    )
                    .addAnnotation(
                        AnnotationSpec
                            .builder(Suppress::class)
                            .addMember("%S", "UNCHECKED_CAST")
                            .build()
                    )
                    .buildPrimaryConstructor {
                        configureConstructor {
                            addAnnotation(Inject::class)
                        }
                        viewModels.forEach {
                            this.addPrimaryConstructorProperty(
                                name = it.asProviderName(),
                                type = it.asProviderType()
                            )
                        }
                    }
                    .addSuperinterface(viewModelProviderFactoryClassName)
                    .addFunction(
                        FunSpec.builder("create")
                            .addModifiers(KModifier.OVERRIDE)
                            .addTypeVariable(TypeVariableName("T", viewModelClassName))
                            .returns(TypeVariableName("T"))
                            .addParameter(
                                "modelClass",
                                Class::class.asClassName().parameterizedBy(TypeVariableName("T"))
                            )
                            .beginControlFlow("return when (modelClass)")
                            .apply {
                                viewModels.forEach { viewModel ->
                                    addStatement(
                                        "%T::class.java -> ${viewModel.shortName.decapitalize()}Provider.get() as T",
                                        viewModel.asClassName(),
                                    )
                                }
                            }
                            .addStatement("else -> throw IllegalArgumentException(\"Unknown ViewModel class: \${'$'}modelClass\")")
                            .endControlFlow()
                            .build()
                    )
                    .build(),
            )
        }
        return createGeneratedFile(codeGenDir, generatedPackage, vmFactoryClassName, content)
    }
}

class PrimaryConstructorBuilder(
    val typeSpec: TypeSpec.Builder,
) {
    private val constructor = FunSpec.constructorBuilder()

    fun configureConstructor(configure: FunSpec.Builder.() -> Unit) {
        constructor.configure()
    }

    fun addPrimaryConstructorProperty(name: String, type: TypeName) {
        constructor.addParameter(ParameterSpec(name, type))
        typeSpec.addProperty(
            PropertySpec.builder(
                name = name,
                type = type,
            )
                .initializer(name)
                .build()
        )
    }

    fun build(): TypeSpec.Builder {
        typeSpec.primaryConstructor(constructor.build())
        return typeSpec
    }
}

fun TypeSpec.Builder.buildPrimaryConstructor(builder: PrimaryConstructorBuilder.() -> Unit): TypeSpec.Builder {
    return PrimaryConstructorBuilder(this).also(builder).build()
}

private fun AnnotationSpec.Builder.addClassMember(
    member: TypeName,
): AnnotationSpec.Builder {
    addMember("%T::class", member)
    return this
}
