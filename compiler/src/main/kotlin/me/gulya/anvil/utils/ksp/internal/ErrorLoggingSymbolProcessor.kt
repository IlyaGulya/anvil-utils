package me.gulya.anvil.utils.ksp.internal

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSNode

internal abstract class ErrorLoggingSymbolProcessor : SymbolProcessor {
    abstract val env: SymbolProcessorEnvironment

    final override fun process(resolver: Resolver): List<KSAnnotated> {
        return try {
            processChecked(resolver)
        } catch (e: SymbolProcessingException) {
            env.logger.error(e.message, e.node)
            e.cause?.let(env.logger::exception)
            emptyList()
        }
    }

    protected abstract fun processChecked(resolver: Resolver): List<KSAnnotated>
}

internal class SymbolProcessingException(
    val node: KSNode,
    override val message: String,
    override val cause: Throwable? = null,
) : Exception()
