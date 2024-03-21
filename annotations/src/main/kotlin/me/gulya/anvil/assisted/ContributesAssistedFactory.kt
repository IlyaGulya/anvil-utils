package me.gulya.anvil.assisted

import kotlin.reflect.KClass

/**
 * Automatically generates an assisted factory for the annotated class and contributes
 * it to the specified [scope] as binding of the type specified as [boundType].
 *
 * [boundType] should be treated the same way as regular Dagger @AssistedFactory.
 * [boundType] should conform to the same requirements as regular Dagger @AssistedFactory.
 * [boundType] factory method can have @Assisted parameters. Due to Dagger 2 limitations you should
 * use another annotation on your [boundType] factory method parameters:
 *
 * Usage example:
 *
 * ```
 * abstract class AppScope private constructor()
 *
 * interface MyClass
 *
 * interface MyFactory {
 *   fun create(
 *     assistedParam: Int,
 *   ): MyClass
 * }
 *
 * @ContributesAssistedFactory(AppScope::class, MyFactory::class)
 * class DefaultMyClass @AssistedInject constructor(
 *   regularParam: String,
 *   @AssistedKey assistedParam: Int
 * ) : MyClass
 * ```
 *
 * The following factory will be generated, implementing MyFactory:
 *
 * ```
 * @ContributesBinding(AppScope::class, MyFactory::class)
 * @AssistedFactory
 * interface MyClass_AssistedFactory : MyFactory {
 *   override fun create(
 *      assistedParam: Int,
 *   ): DefaultMyClass
 * }
 * ```
 *
 * @param scope The scope to contribute the generated factory to.
 * @param boundType The type that the generated factory will implement or extend.
 */
@Target(AnnotationTarget.CLASS)
public annotation class ContributesAssistedFactory(
    val scope: KClass<*>,
    val boundType: KClass<*>,
)