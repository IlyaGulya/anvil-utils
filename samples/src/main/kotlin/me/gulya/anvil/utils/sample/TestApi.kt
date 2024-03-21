package me.gulya.anvil.utils.sample

import com.squareup.anvil.annotations.MergeComponent
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import me.gulya.anvil.assisted.ContributesAssistedFactory
import me.gulya.anvil.assisted.AssistedKey

interface TestApi {
    interface Factory {
        fun create(
            @AssistedKey("arg1") arg: Int,
            @AssistedKey("arg2") arg1: String
        ): TestApi
    }
}

abstract class SampleScope

@ContributesAssistedFactory(SampleScope::class, TestApi.Factory::class)
class DefaultTestApi @AssistedInject constructor(
    @Assisted("arg2") private val arg1: String,
    @Assisted("arg1") private val arg: Int,
) : TestApi

@MergeComponent(SampleScope::class)
interface SampleComponent {
    val factory: TestApi.Factory
}

fun main(args: Array<String>) {
    val component: SampleComponent = DaggerSampleComponent.builder().build()
    val testApi = component.factory.create(1, "arg2")
}