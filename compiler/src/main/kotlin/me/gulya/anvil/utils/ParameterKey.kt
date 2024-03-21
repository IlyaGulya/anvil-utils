package me.gulya.anvil.utils

import com.squareup.kotlinpoet.TypeName

internal data class ParameterKey(
    val type: TypeName,
    val assistedKey: String?,
)