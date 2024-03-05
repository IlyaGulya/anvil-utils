package me.gulya.anvil.utils.sample

import com.squareup.anvil.annotations.MergeComponent
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import me.gulya.anvil.utils.ContributesAssistedFactory

interface TestApi {
    interface Factory {
        fun create(
            arg: String,
            arg1: String
        ): TestApi
    }
}

abstract class SampleScope

@ContributesAssistedFactory(SampleScope::class, TestApi.Factory::class)
class DefaultTestApi @AssistedInject constructor(
    @Assisted private val arg: String,
    @Assisted("arg2") private val arg1: String
) : TestApi

@MergeComponent(SampleScope::class)
interface SampleComponent {
    val factory: TestApi.Factory
}

fun main(args: Array<String>) {

}