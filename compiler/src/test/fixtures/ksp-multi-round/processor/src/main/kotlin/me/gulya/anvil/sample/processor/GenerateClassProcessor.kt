package me.gulya.anvil.sample.processor

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.writeTo
import javax.inject.Inject

class GenerateClassProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation("me.gulya.anvil.sample.GenerateClass")

        symbols.filterIsInstance<KSClassDeclaration>().forEach { declaration ->
            val packageName = declaration.packageName.asString()
            val className = "${declaration.simpleName.asString()}Generated"

            FileSpec.builder(packageName, className)
                .addType(
                    TypeSpec.classBuilder(className)
                        .primaryConstructor(
                            FunSpec.constructorBuilder()
                                .addAnnotation(AnnotationSpec.builder(Inject::class).build())
                                .build()
                        )
                        .build()
                )
                .build()
                .writeTo(codeGenerator, aggregating = false)
        }

        return emptyList()
    }
}

class GenerateClassProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return GenerateClassProcessor(environment.codeGenerator, environment.logger)
    }
} 