import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import me.gulya.anvil.assisted.AssistedKey

internal object Errors {
    fun missingBoundType(className: String): String {
        return "The @ContributesAssistedFactory annotation on class '$className' " +
                "must have a 'boundType' parameter"
    }

    fun mustHaveSinglePrimaryConstructor(className: String): String {
        return "Class '$className' annotated with @ContributesAssistedFactory " +
                "must have a single primary constructor"
    }

    fun primaryConstructorMustBeAnnotatedWithAssistedInject(className: String): String {
        return "Class '$className' annotated with @ContributesAssistedFactory " +
                "must have its primary constructor annotated with @AssistedInject"
    }

    fun boundTypeMustBeAbstractOrInterface(boundTypeName: String, assistedFactoryName: String): String {
        return "The bound type '$boundTypeName' for @ContributesAssistedFactory on class " +
                "'$assistedFactoryName' must be an abstract class or interface"
    }

    fun boundTypeMustHasSingleAbstractMethod(boundType: String): String {
        return "The bound type '$boundType' for @ContributesAssistedFactory " +
                "must have a single abstract method"
    }

    fun parameterMismatch(boundTypeName: String, factoryMethodName: String, assistedFactoryName: String): String {
        return "The assisted factory method parameters in '$boundTypeName.$factoryMethodName' " +
                "must match the @Assisted parameters in the primary constructor of " +
                "'$assistedFactoryName'"
    }

    fun parameterMustBeAnnotatedWithAssistedKey(
        factoryParameterName: String,
        boundTypeName: String,
        factoryMethodName: String
    ): String {
        return "The parameter '${factoryParameterName}' in the factory method " +
                "'${boundTypeName}.${factoryMethodName}' must be annotated with " +
                "@${AssistedKey::class.simpleName} instead of @${Assisted::class.simpleName} " +
                "to avoid conflicts with Dagger's @${AssistedFactory::class.simpleName} annotation"
    }

    fun parameterDoesNotMatchAssistedParameter(factoryParameterName: String, assistedFactoryName: String): String {
        return "The factory method parameter '${factoryParameterName}' does not match any @Assisted parameter " +
                "in the primary constructor of '${assistedFactoryName}'"
    }

    fun boundTypeMustBeClassOrInterface(boundTypeName: String): String {
        return "Bound type ${boundTypeName} must be a class or interface"
    }

}