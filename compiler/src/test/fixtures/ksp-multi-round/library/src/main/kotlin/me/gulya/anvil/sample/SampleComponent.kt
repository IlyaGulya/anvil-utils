package me.gulya.anvil.sample

@GenerateClass // This will trigger our KSP processor to generate SampleComponentGenerated
interface SampleComponent {
    fun interface Factory {
        operator fun invoke(str: String): SampleComponent
    }
}