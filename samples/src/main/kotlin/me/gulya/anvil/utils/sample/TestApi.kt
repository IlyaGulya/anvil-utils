package me.gulya.anvil.utils.sample

import com.squareup.anvil.annotations.MergeComponent
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import me.gulya.anvil.utils.ContributesAssistedFactory

interface TestApi {
    interface Factory {
        fun create(
            @Assisted("arg1") arg: String,
            @Assisted("arg2") arg1: String
        ): TestApi
    }
}

abstract class SampleScope

@ContributesAssistedFactory(SampleScope::class, TestApi.Factory::class)
class DefaultTestApi @AssistedInject constructor(
    @Assisted("arg1") private val arg: String,
    @Assisted("arg2") private val arg1: String,
) : TestApi

@MergeComponent(SampleScope::class)
interface SampleComponent {
    val factory: TestApi.Factory
}

fun main(args: Array<String>) {
    val component: SampleComponent = DaggerSampleComponent.factory().create()
    val testApi = component.factory.create("key", "arg2")
}