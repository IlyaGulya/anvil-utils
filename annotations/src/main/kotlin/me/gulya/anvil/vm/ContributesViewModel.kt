package me.gulya.anvil.vm

import me.gulya.anvil.api.ExperimentalAnvilUtilsApi
import kotlin.reflect.KClass

/**
 * Mark this ViewModel to make it being added to generated `ViewModelFactory` in the specified [scope].
 */
@ExperimentalAnvilUtilsApi
public annotation class ContributesViewModel(
    val scope: KClass<*>,
)