package me.gulya.anvil.utils

import kotlin.reflect.KClass

/**
 * Annotate a class with this to automatically generate an AssistedFactory for it and
 * contribute it to the specified scope.
 *
 * Sample:
 *
 *   @ContributesAssistedFactory(AppScope::class, FactoryToBindTo::class)
 *   class SomeClass @AssistedInject constructor(
 *     @Assisted private val someArg: String,
 *     @Assisted("test") private val someArg2: String,
 *   )
 *
 * is equivalent to the following declaration:
 *
 *   @ContributesBinding(AppScope::class, FactoryToBindTo::class)
 *   @AssistedFactory
 *   interface SomeClass_AssistedFactory : FactoryToBindTo {
 *     fun create(
 *       someArg: String,
 *       @Assisted("test") someArg2: String,
 *     ): SomeClass
 *   }
 *
 * If the bound type is not specified, factory will be contributed to the scope as is:
 *
 *   @ContributesTo(AppScope::class)
 *   @AssistedFactory
 *   interface SomeClass_AssistedFactory {
 *     fun create(
 *       someArg: String,
 *       @Assisted("test") someArg2: String,
 *     ): SomeClass
 *   }
 *
 * The generated code created via the :codegen:anvil module.
 */
@Target(AnnotationTarget.CLASS)
annotation class ContributesAssistedFactory(
    val scope: KClass<*>,
    val boundType: KClass<*> = Nothing::class,
)