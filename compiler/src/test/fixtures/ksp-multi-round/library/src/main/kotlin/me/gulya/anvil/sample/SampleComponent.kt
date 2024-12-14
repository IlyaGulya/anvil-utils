package me.gulya.anvil.sample

import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import me.gulya.anvil.assisted.ContributesAssistedFactory

@GenerateClass // This will trigger our KSP processor to generate SampleComponentGenerated
interface SampleComponent {
    fun interface Factory {
        operator fun invoke(generated: SampleComponentGenerated): SampleComponent
    }
}

@ContributesAssistedFactory(SampleScope::class, SampleComponent.Factory::class)
class DefaultSampleComponent @AssistedInject constructor(
    @Assisted val generated: SampleComponentGenerated // This depends on the generated class
) : SampleComponent

class SampleScope