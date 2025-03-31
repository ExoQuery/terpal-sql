package io.exoquery.sql

import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.ExperimentalSerializationApi as SerApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.serializer
//import java.math.BigDecimal
import kotlinx.datetime.*
import kotlin.jvm.JvmName
import kotlin.reflect.KClass


// Note that T can't extend Any because then T will not be allowed to be null when it is being decoded
// that is why we have KClass<*> and not KClass<T>

data class StatementParam<T>(val serializer: SerializationStrategy<T>, val cls: KClass<*>, val value: T?)
