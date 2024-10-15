package io.exoquery.sql

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.modules.SerializersModule

data class ColumnInfo(val name: String, val type: String)

// Note, this is a class-level annotation so to use Descriptor.annotations.find(...) on it is actually possible
// i.e. you can find the annotation on the object itself. Normally annotations that are on the field-level
// can't be found on the Descriptor.annotations list but only the parentDescriptor.getElementAnnotations(index)
// lookup.
@OptIn(ExperimentalSerializationApi::class)
fun SerialDescriptor.isJsonClassAnnotated() =
  this.annotations.find { it is io.exoquery.sql.SqlJsonValue } != null

@OptIn(ExperimentalSerializationApi::class)
fun SerialDescriptor.isJsonFieldAnnotated(fieldIndex: Int) =
  this.getElementAnnotations(fieldIndex).find { it is io.exoquery.sql.SqlJsonValue } != null

fun SerialDescriptor.isJsonValue() =
  this.serialName == "io.exoquery.sql.JsonValue"

fun <A, B> Iterable<IndexedValue<Pair<A, B>>>.flattenEach() =
  this.map {
    val (i, ab) = it
    val (a, b) = ab
    Triple(i, a, b)
  }

@OptIn(ExperimentalSerializationApi::class)
fun SerialDescriptor.verifyColumns(columns: List<ColumnInfo>): Unit {

  fun flatDescriptorColumnData(desc: SerialDescriptor): List<Pair<String, String>> =
    when {
      // If the entire record that needs to be decoded is a singular json value
      desc.isJsonClassAnnotated() || desc.isJsonValue() ->
        listOf("<unamed>" to desc.serialName)

      desc.kind == StructureKind.CLASS ->
        (desc.elementDescriptors.toList()
          .zip(desc.elementNames.toList()))
          .withIndex()
          .flattenEach()
          .flatMap { (i, fieldDesc, fieldName) ->
        fun plainField() = listOf(fieldName to fieldDesc.serialName)
        when {
          // If the field is supposed to be encoded as a json object treat is as a regular column
          desc.isJsonValue() || fieldDesc.isJsonClassAnnotated() || desc.isJsonFieldAnnotated(i) -> plainField()
          // otherwise if it is a structural-type flatten the structure inside (since it is supposed to be unpacked in a nested fasion)
          fieldDesc.kind == StructureKind.CLASS -> flatDescriptorColumnData(fieldDesc)
          // otherwise its a leaf-value
          else -> plainField()
        }
      }

      else -> listOf("<unamed>" to desc.serialName)
    }

  val descriptorColumns = flatDescriptorColumnData(this)
  if (columns.size != descriptorColumns.size) {
    throw IllegalArgumentException(
          """|Column mismatch. The columns from the SQL ResultSet metadata did not match the expected columns from the deserialized type: ${serialName}
             |SQL Columns (${columns.size}): ${columns.withIndex().map { (i, kv) -> "($i)${kv.name}:${kv.type}" }}
             |Class Columns (${descriptorColumns.size}): ${descriptorColumns.withIndex().map { (i, kv) -> "($i)${kv.first}:${kv.second}" }}
          """.trimMargin())
  }
}

sealed interface StartingIndex {
  val value: Int

  object Zero: StartingIndex { override val value: Int = 0 }
  object One: StartingIndex { override val value: Int = 1 }
}

sealed interface RowDecoderType {
  data class Inline(val descriptor: SerialDescriptor): RowDecoderType
  object Regular: RowDecoderType
}

@OptIn(ExperimentalSerializationApi::class)
class RowDecoder<Session, Row> private constructor(
  val ctx: DecodingContext<Session, Row>,
  val module: SerializersModule,
  val initialRowIndex: Int,
  val api: ApiDecoders<Session, Row>,
  val decoders: Set<SqlDecoder<Session, Row, out Any>>,
  val type: RowDecoderType,
  val json: Json,
  val endCallback: (Int) -> Unit
): Decoder, CompositeDecoder {

  companion object {
    operator fun <Session, Row> invoke(
      ctx: DecodingContext<Session, Row>,
      module: SerializersModule,
      api: ApiDecoders<Session, Row>,
      decoders: Set<SqlDecoder<Session, Row, out Any>>,
      descriptor: SerialDescriptor,
      json: Json,
      startingIndex: StartingIndex
    ): RowDecoder<Session, Row> {
      // If the column infos actaully exist, then verify them
      ctx.columnInfos?.let { columns -> descriptor.verifyColumns(columns) }
      return RowDecoder(ctx, module,  startingIndex.value, api, decoders, RowDecoderType.Regular, json, {})
    }
  }

  fun cloneSelf(ctx: DecodingContext<Session, Row>, initialRowIndex: Int, type: RowDecoderType, endCallback: (Int) -> Unit): RowDecoder<Session, Row> =
    RowDecoder(ctx, this.serializersModule, initialRowIndex, api, decoders, type, json, endCallback)

  // helper to get column names
  fun colName(index: Int) = ctx.columnInfos?.get(index)?.name ?: "<UNKNOWN>"

  var rowIndex: Int = initialRowIndex
  var classIndex: Int = 0

  fun nextRowIndex(desc: SerialDescriptor, descIndex: Int, note: String = ""): Int {
    val curr = rowIndex
    // TODO logging integration
    //println("---- Get Row ${columnInfos[rowIndex-1].name}, Index: ${curr} - (${descIndex}) ${desc.getElementDescriptor(descIndex)} - (Preview:${api.preview(rowIndex, ctx.row)})" + (if (note != "") " - ${note}" else ""))
    rowIndex += 1
    return curr
  }

  fun nextRowIndex(note: String = ""): Int {
    val curr = rowIndex
    // TODO logging integration
    //println("---- Get Row ${columnInfos[rowIndex-1].name} - (Preview:${api.preview(rowIndex, ctx.row)})" + (if (note != "") " - ${note}" else ""))
    rowIndex += 1
    return curr
  }

  override val serializersModule: SerializersModule = module

  override fun decodeBooleanElement(descriptor: SerialDescriptor, index: Int): Boolean = api.BooleanDecoder.decode(ctx, nextRowIndex(descriptor, index))
  override fun decodeByteElement(descriptor: SerialDescriptor, index: Int): Byte = api.ByteDecoder.decode(ctx, nextRowIndex(descriptor, index))
  override fun decodeCharElement(descriptor: SerialDescriptor, index: Int): Char = api.CharDecoder.decode(ctx, nextRowIndex(descriptor, index))
  override fun decodeDoubleElement(descriptor: SerialDescriptor, index: Int): Double = api.DoubleDecoder.decode(ctx, nextRowIndex(descriptor, index))
  override fun decodeFloatElement(descriptor: SerialDescriptor, index: Int): Float = api.FloatDecoder.decode(ctx, nextRowIndex(descriptor, index))
  override fun decodeIntElement(descriptor: SerialDescriptor, index: Int): Int = api.IntDecoder.decode(ctx, nextRowIndex(descriptor, index))
  override fun decodeLongElement(descriptor: SerialDescriptor, index: Int): Long = api.LongDecoder.decode(ctx, nextRowIndex(descriptor, index))
  override fun decodeShortElement(descriptor: SerialDescriptor, index: Int): Short = api.ShortDecoder.decode(ctx, nextRowIndex(descriptor, index))
  override fun decodeStringElement(descriptor: SerialDescriptor, index: Int): String = api.StringDecoder.decode(ctx, nextRowIndex(descriptor, index))

  // These are primarily used when there is some kind of encoder delegation used e.g. if it is a new-type or wrapped-type e.g. DateToLongSerializer
  // they will be invoked from `deserializer.deserialize(this)` in decodeNullableSerializableElement in the last clause.
  override fun decodeBoolean(): Boolean = api.BooleanDecoder.decode(ctx, nextRowIndex())
  override fun decodeByte(): Byte = api.ByteDecoder.decode(ctx, nextRowIndex())
  override fun decodeChar(): Char = api.CharDecoder.decode(ctx, nextRowIndex())
  override fun decodeDouble(): Double = api.DoubleDecoder.decode(ctx, nextRowIndex())
  override fun decodeFloat(): Float = api.FloatDecoder.decode(ctx, nextRowIndex())
  override fun decodeShort(): Short = api.ShortDecoder.decode(ctx, nextRowIndex())
  override fun decodeString(): String = api.StringDecoder.decode(ctx, nextRowIndex())
  override fun decodeInt(): Int = api.IntDecoder.decode(ctx, nextRowIndex())
  override fun decodeLong(): Long = api.LongDecoder.decode(ctx, nextRowIndex())


  @ExperimentalSerializationApi
  override fun decodeNull(): Nothing? = null
  //override fun decodeSequentially(): Boolean = true

  @OptIn(ExperimentalSerializationApi::class)
  override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
    return this
  }
  override fun endStructure(descriptor: SerialDescriptor) {
    // Update the rowIndex of the parent
    endCallback(rowIndex)
  }

  // If the row is null and the descriptor (i.e. the type of the actual column e.g. `MyProduct(myField: MyType?)`) is specified as nullable then
  // return a null value. Otherwise (if it is not null or is null and the column type is not nullable) then make the decoder deal with it.
  // See a more detailed discussion in this logic in `decodeBestEffortFromDescriptor`.
  fun <T> validNullOrElse(desc: SerialDescriptor, index: Int, orElse: () -> T): T? =
    if (api.isNull(rowIndex, ctx.row) && desc.isNullable) {
      nextRowIndex(desc, index, "Skipping Null Value at column:${colName(index)}")
      null
    } else orElse()

  fun <T> decodeJsonValue(desc: SerialDescriptor, index: Int, deserializer: DeserializationStrategy<T?>): T? =
    decodeSqlJson(desc, index)?.let { sqlJson ->
      // create the json represented inside of the JsonValue
      val innerValue = json.parseToJsonElement(sqlJson.value)
      // create the json of the actual JsonValue
      val outerValue = JsonObject(mapOf("value" to innerValue))
      // Parse that into a JsonValue
      val jsonValue = json.decodeFromJsonElement(deserializer, outerValue)
      jsonValue
    }

  fun <T> decodeJsonValueContent(desc: SerialDescriptor, index: Int, deserializer: DeserializationStrategy<T?>): T? =
    decodeSqlJson(desc, index)?.let { sqlJson ->
      // create the json represented inside of a JsonValue e.g. MyPerson for JsonValue<MyPerson>
      val innerValue = json.parseToJsonElement(sqlJson.value)
      // Parse that into a the JsonValue element instance e.g. MyPerson
      val jsonValue = json.decodeFromJsonElement(deserializer, innerValue)
      jsonValue
    }

  fun <T> decodeJsonAnnotated(desc: SerialDescriptor, index: Int, deserializer: DeserializationStrategy<T?>): T? =
    decodeSqlJson(desc, index)?.let { sqlJson ->
      json.decodeFromString(deserializer, sqlJson.value)
    }

  fun decodeSqlJson(desc: SerialDescriptor, index: Int): SqlJson? {
    val jsonDecoder = findJsonDecoderOrFail(desc, index)
    return validNullOrElse(desc, index) {
      val rowIndex = nextRowIndex(desc, index)
      jsonDecoder.decode(ctx, rowIndex)
    }
  }

  @Suppress("UNCHECKED_CAST")
  fun findJsonDecoderOrFail(desc: SerialDescriptor, index: Int): SqlDecoder<Session, Row, SqlJson?> {
    fun currColName() = colName(index)
    fun SqlDecoder<Session, Row, out Any>.asNullableIfSpecified() = if (desc.isNullable) asNullable() else this
    val jsonDecoderRaw = decoders.find { it.type == io.exoquery.sql.SqlJson::class }
      ?: throw IllegalArgumentException("Error decoding ${currColName()}. Cannot decode the value of the column ${currColName()} with the descriptor ${desc} to Json. A SqlJson encoder was not found.")
    val jsonDecoder = (jsonDecoderRaw.asNullableIfSpecified() as SqlDecoder<Session, Row, SqlJson?>)
    return jsonDecoder
  }

  @ExperimentalSerializationApi
  override fun <T : Any> decodeNullableSerializableElement(descriptor: SerialDescriptor, index: Int, deserializer: DeserializationStrategy<T?>, previousValue: T?): T? {

    // get the child descriptor (parent might not have one so make it lazy)
    val childDesc by lazy { descriptor.elementDescriptors.toList()[index] }
    // helper to get column name for various logging statements
    fun currColName() = ctx.columnInfos?.get(index)?.name ?: "<UNKNOWN>"
    // helper to run the decoding
    fun decodeWithDecoder(decoder: SqlDecoder<Session, Row, T>): T? {
      val rowIndex = nextRowIndex(descriptor, index)
      val decoded = decoder.decode(ctx, rowIndex)
      return decoded
    }

    // If the actual decoded element is supposed to be nullable then make the decoder for it nullable
    fun SqlDecoder<Session, Row, out Any>.asNullableIfSpecified() = if (childDesc.isNullable) asNullable() else this

    return when {
      // If the parent decoder is a leaf level i.e. if its actually Query<JsonValue<MyPerson>> instead of Query<SomeProduct(...JsonValue<MyPerson>)>
      descriptor.isJsonValue() ->
        decodeJsonValueContent(descriptor, index, deserializer)

      childDesc.isJsonValue() ->
        decodeJsonValue(childDesc, index, deserializer)

      childDesc.isJsonClassAnnotated() || descriptor.isJsonFieldAnnotated(index) ->
        decodeJsonAnnotated(childDesc, index, deserializer)

      childDesc.kind == StructureKind.LIST -> {
        val decoder =
          when {
            // When its contextual, get the decoder for that base on the capturedKClass
            childDesc.capturedKClass != null ->
              decoders.find { it.type == childDesc.capturedKClass }
                ?: throw IllegalArgumentException("Error decoding column:${currColName()}. Could not find a decoder for the (contextual) structural list type ${childDesc.capturedKClass} with the descriptor: ${childDesc} because not decoder for ${childDesc.capturedKClass} was found")

            childDesc.elementDescriptors.toList().size == 1 && childDesc.elementDescriptors.first().kind is PrimitiveKind.BYTE ->
              // When its not contextual there wont be a captured class, in that case get the first type-parameter from the List descriptor and decode some known types based on that
              decoders.find { it.type == ByteArray::class }
                ?: throw IllegalArgumentException("Error decoding column:${currColName()}. Could not find a byte array decoder in the database-context for the list type ${childDesc.capturedKClass}")

            else ->
              throw IllegalArgumentException("Error decoding column:${currColName()}. Could not find a decoder for the structural list type ${childDesc.capturedKClass} with the descriptor: ${childDesc}. It had an invalid form.")
          }.asNullableIfSpecified()

        // if there is a decoder for the specific array-type use that, otherwise
        run { decodeWithDecoder(decoder as SqlDecoder<Session, Row, T>) }
      }
      childDesc.kind == StructureKind.CLASS -> {
        // Only if all the columns are null (and the returned element can be null) can we assume that the decoded element should be null
        // Since we're always at the current index (e.g. (Person(name,age),Address(street,zip)) when we're on `street` the index will be 3
        // so we need to check [street..<zip] indexes i.e. [3..<(3+2)] for nullity
        val allColsNull =
          (rowIndex until (rowIndex + childDesc.elementsCount)).all {
            api.isNull(it, ctx.row)
          }

        // If all columns are null and the object (that is currently childDesc) is nullable e.g. childDesc=Person(name:Name?, age:Int), Name(first:String?, last:String?)
        // and first/last are both null make Name null (i.e. Person(null, 123)). If Name is not nullable (i.e. Person(name:Name, age:Int))
        // make it Name(null, null)
        if (allColsNull && childDesc.isNullable) {
          decodeNull()
        } else {
          deserializer.deserialize(cloneSelf(ctx, rowIndex, type, { childIndex -> this.rowIndex = childIndex }))
        }
      }
      // When it is contextual and a contextual decoder exists, use that
      childDesc.kind == SerialKind.CONTEXTUAL && serializersModule.getContextualDescriptor(childDesc) != null -> {
        val desc = serializersModule.getContextualDescriptor(childDesc) ?: throw IllegalStateException("Impossible state")
        decodeBestEffortFromDescriptor(desc, descriptor, index, deserializer)
      }

      childDesc.kind == SerialKind.CONTEXTUAL -> {
        val decoder = decoders.find { it.type == childDesc.capturedKClass }?.asNullableIfSpecified()
        if (decoder == null) throw IllegalArgumentException("Error decoding ${currColName()}. Could not find a decoder for the contextual type ${childDesc.capturedKClass}")
        @Suppress("UNCHECKED_CAST")
        run { decodeWithDecoder(decoder as SqlDecoder<Session, Row, T>) }
      }
      else ->
        decodeBestEffortFromDescriptor(childDesc, descriptor, index, deserializer)
    }
  }

  fun <T> decodeBestEffortFromDescriptor(desc: SerialDescriptor, parent: SerialDescriptor, index: Int, alternateDeserializer: DeserializationStrategy<T?>): T? {
    infix fun String.eqOrNull(cls: String): Boolean = this == cls || this == "$cls?"
    // If the actual decoded element is supposed to be nullable then make the decoder for it nullable
    fun SqlDecoder<Session, Row, out Any>.asNullableIfSpecified() = if (desc.isNullable) asNullable() else this

    return when {
      desc.kind is PrimitiveKind ->
        run {
          val descKind = desc.kind
          val serialName = desc.serialName
          // just doing deserializer.deserialize(this) at this point will just call the non-element decoders e.g. decodeString, decodeInt, etc... we
          // want to call the decoders that have element information in them (e.g. decodeByteElement, decodeShortElement, etc...) if this is possible
          when {
            descKind is PrimitiveKind.BYTE  && serialName eqOrNull "kotlin.Byte" -> api.ByteDecoder.asNullableIfSpecified().decode(ctx, nextRowIndex(parent, index))
            descKind is PrimitiveKind.SHORT  && serialName eqOrNull "kotlin.Short" -> api.ShortDecoder.asNullableIfSpecified().decode(ctx, nextRowIndex(parent, index))
            descKind is PrimitiveKind.INT  && serialName eqOrNull "kotlin.Int" -> api.IntDecoder.asNullableIfSpecified().decode(ctx, nextRowIndex(parent, index))
            descKind is PrimitiveKind.LONG  && serialName eqOrNull "kotlin.Long" -> api.LongDecoder.asNullableIfSpecified().decode(ctx, nextRowIndex(parent, index))
            descKind is PrimitiveKind.FLOAT  && serialName eqOrNull "kotlin.Float" -> api.FloatDecoder.asNullableIfSpecified().decode(ctx, nextRowIndex(parent, index))
            descKind is PrimitiveKind.DOUBLE  && serialName eqOrNull "kotlin.Double" -> api.DoubleDecoder.asNullableIfSpecified().decode(ctx, nextRowIndex(parent, index))
            descKind is PrimitiveKind.BOOLEAN  && serialName eqOrNull "kotlin.Boolean"  -> api.BooleanDecoder.asNullableIfSpecified().decode(ctx, nextRowIndex(parent, index))
            descKind is PrimitiveKind.CHAR && serialName eqOrNull "kotlin.Char" -> api.CharDecoder.asNullableIfSpecified().decode(ctx, nextRowIndex(parent, index))
            descKind is PrimitiveKind.STRING && serialName eqOrNull "kotlin.String" -> api.StringDecoder.asNullableIfSpecified().decode(ctx, nextRowIndex(parent, index))
            else -> {
              // If it is a primitive type wrapped into a non-primitive type (e.g. DateToLongSerializer) then use the encoder defined in the serialization. Note that
              // also known as a new-type (e.g. NewTypeInt(value: Int) then this serializer will be the wrapped one. It is assumed that in this case these kinds
              // cannot deal with their own nullability and that the nullability is handled needs to be handeled here. This is primary because this call to deserializer.deserialize
              // would frequently delegate to a non-nullable primitive deserialize call (e.g. this.decodeString()) which would break in the corresponding JDBC decoder (e.g. DecodeString).
              // The function decodeString() would be called by the client that would implement a primitive wrapping decoder that might look something like this:
              //
              //    object TestTypeSerialzier: KSerializer<SerializeableTestType> {
              //      override val descriptor = PrimitiveSerialDescriptor("SerializeableTestType", PrimitiveKind.STRING)
              //      override fun serialize(...) = ...
              //      override fun deserialize(decoder: Decoder): SerializeableTestType = SerializeableTestType(decoder.decodeString())
              //    }
              // Note how the user uses `decoder.decodeString()` which will use this.decodeString() which will call the JDBC decoder's decodeString() which will call the ResultSet.getString() in StringDecoder
              // that function in turn will return null which will fail in the StringDecoder since it was not converted via asNullable. Now if we actually had knowledge of the descriptor
              // (i.e. the ability to call descriptor.isNullable() to know if nullability is possible)
              // in this.decodeString() we could do StringDecoder.asNullable().decode(...) but the function signature does not allow for that. Now we could technically
              // tell the user to always use decodeInline and copy this RowDecoder with the Descriptor available. This is a future direction to explore.
              // For now however, it seems to be the best practice to handle nullability ahead of time and if the value is null, not call the deserializer (e.g. decodeString()) call in the first place.
              // The exception to this is if the child-descriptor is specifically marked non-nullable. In that case we know we cannot actually return a null-value from this function
              // because that would fail downstream. In that case we have no choice but to call deserializer.deserialize(this) and force the deserializer to handle the null-value.
              // For example this is the case in the Oracle StringDecoder since Oracle (very strangely!) automacally converts empty-strings to null-values. Therefore
              // we need to call the deserializer to handle the null-value and return a non-null value (e.g. an empty string) in the case of a null-value.
              validNullOrElse(desc, index) {
                alternateDeserializer.deserialize(this)
              }
            }
          } as T?
        }
      else ->
        throw IllegalArgumentException("Unsupported kind: ${desc.kind} at column:${ctx.columnInfos?.get(index)}")
    }
  }


  override fun decodeInlineElement(descriptor: SerialDescriptor, index: Int): Decoder = this
  @OptIn(ExperimentalSerializationApi::class)
  override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
    if (classIndex >= descriptor.elementsCount) return CompositeDecoder.DECODE_DONE
    //val childDesc = descriptor.elementDescriptors.toList()[fieldIndex]
//    return when (childDesc.kind) {
//      StructureKind.CLASS -> elementIndex
//      else -> elementIndex++
//    }
    val currClassIndex = classIndex
    classIndex += 1
    return currClassIndex
  }

  @OptIn(ExperimentalSerializationApi::class)
  @Suppress("UNCHECKED_CAST")
  override fun <T> decodeSerializableElement(descriptor: SerialDescriptor, index: Int, deserializer: DeserializationStrategy<T>, previousValue: T?): T {
    val element = decodeNullableSerializableElement(descriptor, index, deserializer, previousValue)
    return when {
      element != null -> element
      //now: element == null must be true
      descriptor.getElementDescriptor(index).isNullable -> null as T
      else -> throw IllegalArgumentException("Error at column ${ctx.columnInfos?.get(index)}. Found null element at index ${index} of descriptor ${descriptor.getElementDescriptor(index)} (of ${descriptor}) where null values are not allowed.")
    }
  }

  override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
    TODO("Not yet implemented")
  }

  // When using classes with a single primitive (e.g. value-classes) this is very useful because we want to retain informaiton about the primitive type.
  // For example, if we have a class `class NewTypeInt(val value: Int?)` we want to know that the value is a nullable-Int and not just an Int.
  // Currently this functionality is not used but we might want to know the type of the primitive in the future.
  override fun decodeInline(descriptor: SerialDescriptor): Decoder = cloneSelf(ctx, rowIndex, RowDecoderType.Inline(descriptor), endCallback)

  /**
   * Checks to see if the element is null before calling an actual deserialzier. Can't use this for nested classes because
   * we need to check all upcoming rows to see if all of them are null, only then is the parent element considered null
   * so instead we just opt to return true and check for nullity in the parent call of decodeNullableSerializableElement.
   */
  @ExperimentalSerializationApi
  override fun decodeNotNullMark(): Boolean {
    return true
  }


}