package io.exoquery.controller

import kotlinx.serialization.Serializable

@Serializable
data class JsonValue<T>(val value: T)