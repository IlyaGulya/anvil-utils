package me.gulya.anvil.utils.sample

import com.squareup.anvil.annotations.MergeComponent

@MergeComponent(SampleScope::class)
interface SampleComponent {
    val factory: TestApi.Factory
}

fun main(args: Array<String>) {
    val component: SampleComponent = DaggerSampleComponent.builder().build()
    val testApi = component.factory.create(1, "arg2")
}