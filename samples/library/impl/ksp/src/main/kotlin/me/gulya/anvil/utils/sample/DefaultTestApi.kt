package me.gulya.anvil.utils.sample

import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import me.gulya.anvil.assisted.ContributesAssistedFactory

@ContributesAssistedFactory(SampleScope::class, TestApi.Factory::class)
class DefaultTestApi @AssistedInject constructor(
    @Assisted("arg2") private val arg1: String,
    @Assisted("arg1") private val arg: Int,
) : TestApi