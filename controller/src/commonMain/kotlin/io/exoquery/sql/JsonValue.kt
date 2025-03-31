package io.exoquery.sql

import kotlinx.serialization.Serializable

@Serializable
data class JsonValue<T>(val value: T)