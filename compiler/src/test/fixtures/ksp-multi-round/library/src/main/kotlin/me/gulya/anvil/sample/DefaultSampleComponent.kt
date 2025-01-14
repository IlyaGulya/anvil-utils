package me.gulya.anvil.sample

import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import me.gulya.anvil.assisted.ContributesAssistedFactory

@ContributesAssistedFactory(SampleScope::class, SampleComponent.Factory::class)
class DefaultSampleComponent @AssistedInject constructor(
    val generated: SampleComponentGenerated, // This depends on the generated class
    @Assisted val string: String,
) : SampleComponent