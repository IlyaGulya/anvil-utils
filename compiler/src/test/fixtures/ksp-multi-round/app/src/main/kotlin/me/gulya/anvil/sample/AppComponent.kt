package me.gulya.anvil.sample

import com.squareup.anvil.annotations.MergeComponent

@MergeComponent(SampleScope::class)
interface AppComponent {
    val factory: SampleComponent.Factory
}

fun main() {
    val component = DaggerAppComponent.create()
    val instance = component.factory("")
}