package me.gulya.anvil.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.writeTo

class GenerateClassProcessor(
    private val codeGenerator: CodeGenerator,
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation("me.gulya.anvil.sample.GenerateClass")

        symbols.filterIsInstance<KSClassDeclaration>().forEach { declaration ->
            val packageName = declaration.packageName.asString()
            val className = "${declaration.simpleName.asString()}Generated"

            FileSpec.builder(packageName, className)
                .addType(
                    TypeSpec.classBuilder(className)
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
        return GenerateClassProcessor(environment.codeGenerator)
    }
}