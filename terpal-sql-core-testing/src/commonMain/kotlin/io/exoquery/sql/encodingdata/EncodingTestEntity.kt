package io.exoquery.sql.encodingdata

import io.exoquery.sql.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object TestTypeSerialzier: KSerializer<SerializeableTestType> {
  override val descriptor = PrimitiveSerialDescriptor("SerializeableTestType", PrimitiveKind.STRING)
  override fun serialize(encoder: Encoder, value: SerializeableTestType) = encoder.encodeString(value.value)
  override fun deserialize(decoder: Decoder): SerializeableTestType = SerializeableTestType(decoder.decodeString())
}

@Serializable(with = TestTypeSerialzier::class)
data class SerializeableTestType(val value: String)

@Serializable
data class EncodingTestEntity(
  val stringMan: String,
  val booleanMan: Boolean,
  val byteMan: Byte,
  val shortMan: Short,
  val intMan: Int,
  val long: Long,
  val floatMan: Float,
  val double: Double,
  val byteArrayMan: ByteArray,
  val customMan: SerializeableTestType,
  val stringOpt: String?,
  val booleanOpt: Boolean?,
  val byteOpt: Byte?,
  val shortOpt: Short?,
  val intOpt: Int?,
  val longOpt: Long?,
  val floatOpt: Float?,
  val doubleOpt: Double?,
  val byteArrayOpt: ByteArray?,
  val customOpt: SerializeableTestType?
) {
  companion object {
    val regular =
      EncodingTestEntity(
        stringMan = "s",
        booleanMan = true,
        byteMan = 11.toByte(),
        shortMan = 23.toShort(),
        intMan = 33,
        long = 431L,
        floatMan = 34.4f,
        double = 42.0,
        byteArrayMan = byteArrayOf(1.toByte(), 2.toByte()),
        customMan = SerializeableTestType("s"),
        stringOpt = "s",
        booleanOpt = true,
        byteOpt = 11.toByte(),
        shortOpt = 23.toShort(),
        intOpt = 33,
        longOpt = 431L,
        floatOpt = 34.4f,
        doubleOpt = 42.0,
        byteArrayOpt = byteArrayOf(1.toByte(), 2.toByte()),
        customOpt = SerializeableTestType("s")
      )

    val empty =
      EncodingTestEntity(
        stringMan = "",
        booleanMan = false,
        byteMan = 0.toByte(),
        shortMan = 0.toShort(),
        intMan = 0,
        long = 0L,
        floatMan = 0f,
        double = 0.0,
        byteArrayMan = byteArrayOf(),
        customMan = SerializeableTestType(""),
        stringOpt = null,
        booleanOpt = null,
        byteOpt = null,
        shortOpt = null,
        intOpt = null,
        longOpt = null,
        floatOpt = null,
        doubleOpt = null,
        byteArrayOpt = null,
        customOpt = null
      )
  }
}

fun insert(e: EncodingTestEntity): Action {
  fun wrap(value: SerializeableTestType?): Param<SerializeableTestType> = Param.withSer(value, SerializeableTestType.serializer())
  return Sql("INSERT INTO EncodingTestEntity VALUES (${e.stringMan}, ${e.booleanMan}, ${e.byteMan}, ${e.shortMan}, ${e.intMan}, ${e.long}, ${e.floatMan}, ${e.double}, ${e.byteArrayMan}, ${wrap(e.customMan)}, ${e.stringOpt}, ${e.booleanOpt}, ${e.byteOpt}, ${e.shortOpt}, ${e.intOpt}, ${e.longOpt}, ${e.floatOpt}, ${e.doubleOpt}, ${e.byteArrayOpt}, ${wrap(e.customOpt)})").action()
}

fun insertBatch(es: List<EncodingTestEntity>): BatchAction {
  fun wrap(value: SerializeableTestType?): Param<SerializeableTestType> = Param.withSer(value, SerializeableTestType.serializer())
  return SqlBatch { e: EncodingTestEntity ->
    "INSERT INTO EncodingTestEntity VALUES (${e.stringMan}, ${e.booleanMan}, ${e.byteMan}, ${e.shortMan}, ${e.intMan}, ${e.long}, ${e.floatMan}, ${e.double}, ${e.byteArrayMan}, ${wrap(e.customMan)}, ${e.stringOpt}, ${e.booleanOpt}, ${e.byteOpt}, ${e.shortOpt}, ${e.intOpt}, ${e.longOpt}, ${e.floatOpt}, ${e.doubleOpt}, ${e.byteArrayOpt}, ${wrap(e.customOpt)})"
  }.values(es.asSequence()).action()
}

fun verifyOracle(e1: EncodingTestEntity, e2: EncodingTestEntity) =
  verify(e1, e2, oracleStrings = true)

fun verify(e1: EncodingTestEntity, e2: EncodingTestEntity, oracleStrings: Boolean = false) {
  e1.stringMan shouldBeEqualEmptyNullable e2.stringMan
  e1.booleanMan shouldBeEqual e2.booleanMan
  e1.byteMan shouldBeEqual e2.byteMan
  e1.shortMan shouldBeEqual e2.shortMan
  e1.intMan shouldBeEqual e2.intMan
  e1.long shouldBeEqual e2.long
  e1.floatMan shouldBeEqual e2.floatMan
  e1.double shouldBeEqual e2.double
  e1.byteArrayMan.toList() shouldBeEqual e2.byteArrayMan.toList()
  e1.customMan shouldBeEqual e2.customMan

  if (!oracleStrings) e1.stringOpt shouldBeEqualNullable e2.stringOpt
  else e1.stringOpt shouldBeEqualEmptyNullable  e2.stringOpt
  e1.booleanOpt shouldBeEqualNullable e2.booleanOpt
  e1.byteOpt shouldBeEqualNullable e2.byteOpt
  e1.shortOpt shouldBeEqualNullable e2.shortOpt
  e1.intOpt shouldBeEqualNullable e2.intOpt
  e1.longOpt shouldBeEqualNullable e2.longOpt
  e1.floatOpt shouldBeEqualNullable e2.floatOpt
  e1.doubleOpt shouldBeEqualNullable e2.doubleOpt
  (e1.byteArrayOpt?.let { it.toList() } ?: listOf()) shouldBeEqual (e2.byteArrayOpt?.let { it.toList() } ?: listOf())
  if (!oracleStrings) e1.customOpt shouldBeEqualNullable e2.customOpt
  else e1.customOpt shouldBeEqualEmptyNullable e2.customOpt
}
