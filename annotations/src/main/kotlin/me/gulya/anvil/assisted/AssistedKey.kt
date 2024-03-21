package me.gulya.anvil.assisted

/**
 * Allows to specify a custom key for assisted parameters. This is useful when you have multiple
 * parameters of the same type and you want to differentiate them.
 *
 * Should be used instead of similar Dagger's @Assisted annotation in your public factories bound by
 * @ContributesAssistedFactory annotation.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
public annotation class AssistedKey(
    val value: String,
)