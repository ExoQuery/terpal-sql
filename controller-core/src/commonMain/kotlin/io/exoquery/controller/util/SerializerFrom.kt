package io.exoquery.controller.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

open class SerializerFrom<Orig, New>(
  private val original: KSerializer<Orig>,
  private val decodeTo: (Orig) -> New,
  private val encodeFrom: (New) -> Orig,
  private val descriptorName: kotlin.String? = null
): KSerializer<New> {
  override val descriptor: SerialDescriptor =
    when {
      descriptorName != null && original.descriptor.kind is PrimitiveKind ->
        PrimitiveSerialDescriptor(descriptorName, original.descriptor.kind as PrimitiveKind)
      descriptorName != null && original.descriptor.kind !is PrimitiveKind ->
        throw IllegalStateException("Can only copy a primitive descriptor kind but kind is ${original.descriptor.kind}")
      else ->
        original.descriptor
    }

  override fun serialize(encoder: Encoder, value: New) = original.serialize(encoder, encodeFrom(value))
  override fun deserialize(decoder: Decoder): New = decodeTo(original.deserialize(decoder))

  open class String<New>(
    private val decodeTo: (kotlin.String) -> New,
    private val encodeFrom: (New) -> kotlin.String,
    private val descriptorName: kotlin.String? = null
  ): SerializerFrom<kotlin.String, New>(kotlin.String.serializer(), decodeTo, encodeFrom, descriptorName)

  open class Int<New>(
    private val decodeTo: (kotlin.Int) -> New,
    private val encodeFrom: (New) -> kotlin.Int,
    private val descriptorName: kotlin.String? = null
  ): SerializerFrom<kotlin.Int, New>(kotlin.Int.serializer(), decodeTo, encodeFrom, descriptorName)

  open class Long<New>(
    private val decodeTo: (kotlin.Long) -> New,
    private val encodeFrom: (New) -> kotlin.Long,
    private val descriptorName: kotlin.String? = null
  ): SerializerFrom<kotlin.Long, New>(kotlin.Long.serializer(), decodeTo, encodeFrom, descriptorName)

  open class Double<New>(
    private val decodeTo: (kotlin.Double) -> New,
    private val encodeFrom: (New) -> kotlin.Double,
    private val descriptorName: kotlin.String? = null
  ): SerializerFrom<kotlin.Double, New>(kotlin.Double.serializer(), decodeTo, encodeFrom, descriptorName)

  open class Float<New>(
    private val decodeTo: (kotlin.Float) -> New,
    private val encodeFrom: (New) -> kotlin.Float,
    private val descriptorName: kotlin.String? = null
  ): SerializerFrom<kotlin.Float, New>(kotlin.Float.serializer(), decodeTo, encodeFrom, descriptorName)

  open class Boolean<New>(
    private val decodeTo: (kotlin.Boolean) -> New,
    private val encodeFrom: (New) -> kotlin.Boolean,
    private val descriptorName: kotlin.String? = null
  ): SerializerFrom<kotlin.Boolean, New>(kotlin.Boolean.serializer(), decodeTo, encodeFrom, descriptorName)

  open class Short<New>(
    private val decodeTo: (kotlin.Short) -> New,
    private val encodeFrom: (New) -> kotlin.Short,
    private val descriptorName: kotlin.String? = null
  ): SerializerFrom<kotlin.Short, New>(kotlin.Short.serializer(), decodeTo, encodeFrom, descriptorName)

  open class Char<New>(
    private val decodeTo: (kotlin.Char) -> New,
    private val encodeFrom: (New) -> kotlin.Char,
    private val descriptorName: kotlin.String? = null
  ): SerializerFrom<kotlin.Char, New>(kotlin.Char.serializer(), decodeTo, encodeFrom, descriptorName)

  open class Byte<New>(
    private val decodeTo: (kotlin.Byte) -> New,
    private val encodeFrom: (New) -> kotlin.Byte,
    private val descriptorName: kotlin.String? = null
  ): SerializerFrom<kotlin.Byte, New>(kotlin.Byte.serializer(), decodeTo, encodeFrom, descriptorName)
}
