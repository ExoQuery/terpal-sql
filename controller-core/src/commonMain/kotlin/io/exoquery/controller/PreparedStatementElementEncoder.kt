package io.exoquery.controller

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.modules.SerializersModule

class PreparedStatementElementEncoder<Session, Stmt>(
  val ctx: EncodingContext<Session, Stmt>,
  val index: Int,
  val api: ApiEncoders<Session, Stmt>,
  val encoders: Set<SqlEncoder<Session, Stmt, out Any>>,
  val module: SerializersModule,
  val json: Json,
  val serializer: SerializationStrategy<*>
): Encoder {

  override val serializersModule: SerializersModule = module

  override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder =
    if (descriptor.kind == StructureKind.CLASS)
      throw IllegalArgumentException(
        """|Cannot encode the structural type `${descriptor.serialName}`, Encoding of structural types is not allowed to be encoded in terpal-sql.
           |Only atomic-values are allowed to be encoded. If `${descriptor.serialName}` has only one field then it should be can be encoded
           |as a primitive representing the underlying field. For example:
           |  
           |  @Serializable(with = MyTypeSerializer::class) 
           |  data class MyType(val value: Int)
           |  
           |  object MyTypeSerializer: KSerializer<MyType> {
           |    override val descriptor = PrimitiveSerialDescriptor("MyType", PrimitiveKind.INT)
           |    override fun serialize(encoder: Encoder, value: MyType) = encoder.encodeInt(value.value)
           |    override fun deserialize(decoder: Decoder): MyType = MyType(decoder.decodeInt())
           |  }
           | 
           |""".trimMargin())
    else
      throw IllegalArgumentException("Illegal descriptor kind: ${descriptor.kind} was attempted for structural decoding. This should be impossible.")

  override fun encodeBoolean(value: Boolean) = api.BooleanEncoder.encode(ctx, value, index)
  override fun encodeByte(value: Byte) = api.ByteEncoder.encode(ctx, value, index)
  override fun encodeChar(value: Char) = api.CharEncoder.encode(ctx, value, index)
  override fun encodeDouble(value: Double) = api.DoubleEncoder.encode(ctx, value, index)
  override fun encodeFloat(value: Float) = api.FloatEncoder.encode(ctx, value, index)
  override fun encodeInt(value: Int) = api.IntEncoder.encode(ctx, value, index)
  override fun encodeLong(value: Long) = api.LongEncoder.encode(ctx, value, index)
  override fun encodeShort(value: Short) = api.ShortEncoder.encode(ctx, value, index)
  override fun encodeString(value: String) = api.StringEncoder.encode(ctx, value, index)

  override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) =
    TODO("Enum encoding not yet supported")

  /**
   * Since the assumption of this encoder is that it is created per every single value that needs to be inserted, we pass a serializer for that particular value
   * each time. That way we know with absolute certainty what the type of each thing we are trying to encode is. Otherwise if null-values are passed in there
   * were situations where we did not no their type.
   */
  @ExperimentalSerializationApi
  override fun encodeNull() =
    encodePrimitiveNull(serializer.descriptor)

  override fun encodeInline(descriptor: SerialDescriptor): Encoder = this

  override public fun <T : Any?> encodeSerializableValue(serializer: SerializationStrategy<T>, value: T) {
    encodeNullableSerializableValue(serializer, value)
  }

  @OptIn(ExperimentalSerializationApi::class)
  override fun <T : Any> encodeNullableSerializableValue(serializer: SerializationStrategy<T>, value: T?) {
    val desc = serializer.descriptor


    // Note, for decoders I do not think it is possible to know on the level of types whether something is nullable. Need to investigate further.
    //fun SqlEncoder<Session, Stmt, out Any>.asNullableIfSpecified() = if (desc.isNullable) asNullable() else this

    return when {
      desc.isJsonValue() -> {
        encoders.find { it.type == SqlJson::class }?.let {
          when {
            value is JsonValue<*>? -> {
              // This is the json of the whole JsonElement e.g. for JsonValue<MyPerson(name, age)> it would be {"value": {"name": "Alice", "age": 30}}
              val outerJson = value?.let { json.encodeToJsonElement(serializer, it) }
              // pull the `value` field out
              val innerJson = outerJson?.let { it.jsonObject["value"] }
              val encoder = it.asNullable() as SqlEncoder<Session, Stmt, SqlJson?>
              encoder.encode(ctx, innerJson?.let { SqlJson(it.toString()) }, index)
            }
            else ->
              throw IllegalArgumentException("Cannot encode ${value} (class: ${value?.let { it::class }}) with the descriptor ${desc} to Json. It was identified as a JsonValue object but was not actually an instance of it.")
          }
        } ?: throw IllegalArgumentException("Cannot encode ${value} (class: ${value?.let { it::class }}) with the descriptor ${desc} to Json. A SqlJson encoder was not found.")
      }

      desc.isJsonClassAnnotated() -> {
        encoders.find { it.type == SqlJson::class }?.let {
          val jsonStr = value?.let { json.encodeToString(serializer, it) }
          val encoder = it.asNullable() as SqlEncoder<Session, Stmt, SqlJson?>
          encoder.encode(ctx, jsonStr?.let { SqlJson(it) }, index)
        } ?: throw IllegalArgumentException("Cannot encode ${value} (class: ${value?.let { it::class }}) with the descriptor ${desc} to Json. A SqlJson encoder was not found.")
      }

      desc.kind == StructureKind.LIST -> {
        val encoder =
          when {
            desc.capturedKClass != null -> {
              encoders.find { it.type == desc.capturedKClass } ?: throw IllegalArgumentException("Could not find a decoder for the list type ${desc.capturedKClass}")
            }
            desc.elementDescriptors.toList().size == 1 && desc.elementDescriptors.first().kind is PrimitiveKind.BYTE ->
              encoders.find { it.type == ByteArray::class }
            else ->
              null
          }

        encoder?.let { (it.asNullable() as SqlEncoder<Session, Stmt, T?> ).encode(ctx, value, index) }
          ?: throw IllegalArgumentException("Could not find a encoder for the structural list type ${desc.capturedKClass} with the descriptor: ${desc}")
      }
      desc.kind == SerialKind.CONTEXTUAL -> {
        val encoder = encoders.find { it.type == desc.capturedKClass }?.asNullable()
        if (encoder == null) throw IllegalArgumentException("Could not find a encoder for the contextual type ${desc.capturedKClass}")
        @Suppress("UNCHECKED_CAST")
        run { (encoder as SqlEncoder<Session, Stmt, T?>).encode(ctx, value, index) }
      }

      desc.kind == StructureKind.CLASS && desc.isInline -> {
        if (value != null)
          serializer.serialize(this, value)
        else
          (serializer as? KSerializer<T>)?.nullable?.serialize(this, value)
            ?: throw IllegalArgumentException("cannot encode null value at (${ctx.startingIndex.value}) index ${index} with the descriptor ${desc}. The serializer ${serializer} could not be converted into a KSerializer.")
      }

      else -> {
        if (value == null) {
          encodePrimitiveNull(serializer.descriptor)
        } else if (desc.kind is PrimitiveKind) {
          // if it is a primitive type then use the encoder defined in the serialization. Note that
          // if it is a wrappedType (e.g. NewTypeInt(value: Int) then this serializer will be the wrapped one
          serializer.serialize(this, value)
        }
        else {
          throw IllegalArgumentException("Unsupported serial-kind: ${desc.kind} for the value ${value} with the descriptor ${desc} could not be decoded as a Array, Contextual, or Primitive value")
        }
      }
    }
  }

  fun encodePrimitiveNull(desc: SerialDescriptor) {
    when {
      // In addition to primitives, support inline classes
      desc.kind == StructureKind.CLASS && desc.isInline -> {
         val elementDescriptor = desc.getElementDescriptor(0)
        encodePrimitiveNull(elementDescriptor)
      }
      desc.kind == PrimitiveKind.BYTE -> api.ByteEncoder.asNullable().encode(ctx, null, index)
      desc.kind == PrimitiveKind.SHORT -> api.ShortEncoder.asNullable().encode(ctx, null, index)
      desc.kind == PrimitiveKind.INT -> api.IntEncoder.asNullable().encode(ctx, null, index)
      desc.kind == PrimitiveKind.LONG -> api.LongEncoder.asNullable().encode(ctx, null, index)
      desc.kind == PrimitiveKind.FLOAT -> api.FloatEncoder.asNullable().encode(ctx, null, index)
      desc.kind == PrimitiveKind.DOUBLE -> api.DoubleEncoder.asNullable().encode(ctx, null, index)
      desc.kind == PrimitiveKind.BOOLEAN -> api.BooleanEncoder.asNullable().encode(ctx, null, index)
      desc.kind == PrimitiveKind.CHAR -> api.CharEncoder.asNullable().encode(ctx, null, index)
      desc.kind == PrimitiveKind.STRING -> api.StringEncoder.asNullable().encode(ctx, null, index)
      else -> throw IllegalArgumentException("Unsupported null primitive kind: ${desc.kind}")
    }
  }
}
