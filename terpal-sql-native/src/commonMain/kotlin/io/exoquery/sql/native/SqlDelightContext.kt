package io.exoquery.sql.native

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.Cursor
import co.touchlab.sqliter.DatabaseConnection
import co.touchlab.sqliter.Statement
import io.exoquery.sql.*

class SqlDelightContext(val driver: NativeSqliteDriver) {




  suspend fun <T> run(query: Query<T>): List<T> = TODO()

}

typealias NativeEncoder<T> = SqlEncoder<DatabaseConnection, Statement, T>


typealias NativeEncodingContext = EncodingContext<DatabaseConnection, Statement>
typealias NativeDecodingContext = DecodingContext<DatabaseConnection, Cursor>

//abstract class NativeEncoderAny<T: Any>: NativeEncoder<T>() {
//  abstract val jdbcType: Int
//
//  override fun asNullable(): NativeEncoder<T?> =
//    object: NativeEncoder<T?>() {
//      override val type = this@NativeEncoderAny.type
//      val jdbcType = this@NativeEncoderAny.jdbcType
//      override fun asNullable(): SqlEncoder<DatabaseConnection, Statement, T?> = this
//
//      override fun encode(ctx: NativeEncodingContext, value: T?, index: Int) =
//        try {
//          if (value != null)
//            this@NativeEncoderAny.encode(ctx, value, index)
//          else
//            ctx.stmt.bindNull(index)
//        } catch (e: Throwable) {
//          throw EncodingException("Error encoding ${type} value: $value at index: $index (whose jdbc-type: ${jdbcType})", e)
//        }
//    }
//
//  inline fun <reified R: Any> contramap(crossinline f: (R) -> T):NativeEncoderAny<R> =
//    object: NativeEncoderAny<R>() {
//      override val type = R::class
//      // Get the JDBC type from the parent. This makes sense because most of the time contramapped encoders are from primivites
//      // e.g. StringDecoder.contramap { ... } so we want the jdbc type from the parent.
//      override val jdbcType = this@NativeEncoderAny.jdbcType
//      override fun encode(ctx: NativeEncodingContext, value: R, index: Int) =
//        this@NativeEncoderAny.encode(ctx, f(value), index)
//    }
//
//  /*
//  expected:<[EncodingTestEntity(v1=s, v2=1.1, v3=true, v4=11, v5=23, v6=33, v7=431, v8=34.4, v9=42.0, v10=[1, 2], v11=Fri Nov 22 19:00:00 EST 2013, v12=EncodingTestType(value=s), v13=2013-11-23, v14=173fb134-c84b-4bb2-be5c-85b2552f60b5, o1=s, o2=1.1, o3=true, o4=11, o5=23, o6=33, o7=431, o8=34.4, o9=42.0, o10=[1, 2], o11=Fri Nov 22 19:00:00 EST 2013, o12=EncodingTestType(value=s), o13=2013-11-23, o14=348e85a2-d953-4cb6-a2ff-a90f02006eb4),
//             EncodingTestEntity(v1=, v2=0, v3=false, v4=0, v5=0, v6=0, v7=0, v8=0.0, v9=0.0, v10=[], v11=1969-12-31, v12=EncodingTestType(value=), v13=1970-01-01, v14=00000000-0000-0000-0000-000000000000, o1=null, o2=null, o3=null, o4=null, o5=null, o6=null, o7=null, o8=null, o9=null, o10=null, o11=null, o12=null, o13=null, o14=null)]> but was:<[EncodingTestEntity(v1=s, v2=1.10, v3=true, v4=11, v5=23, v6=33, v7=431, v8=34.4, v9=42.0, v10=[1, 2], v11=Fri Nov 22 19:00:00 EST 2013, v12=EncodingTestType(value=s), v13=2013-11-23, v14=173fb134-c84b-4bb2-be5c-85b2552f60b5, o1=s, o2=1.10, o3=true, o4=null, o5=null, o6=null, o7=null, o8=null, o9=null, o10=null, o11=null, o12=null, o13=null, o14=null), EncodingTestEntity(v1=, v2=0.00, v3=false, v4=0, v5=0, v6=0, v7=0, v8=0.0, v9=0.0, v10=[], v11=Wed Dec 31 19:00:00 EST 1969, v12=EncodingTestType(value=), v13=1970-01-01, v14=00000000-0000-0000-0000-000000000000, o1=null, o2=null, o3=null, o4=null, o5=null, o6=null, o7=null, o8=null, o9=null, o10=null, o11=null, o12=null, o13=null, o14=null)]>
//   */
//
//  companion object {
//    inline fun <reified T: Any> fromFunction(jdbcTypeNum: Int, crossinline f: (NativeEncodingContext, T, Int) -> Unit): NativeEncoderAny<T> =
//      object: NativeEncoderAny<T>() {
//        override val jdbcType: Int = jdbcTypeNum
//        override val type = T::class
//        override fun encode(ctx: NativeEncodingContext, value: T, index: Int) =
//          f(ctx, value, index)
//      }
//  }
//}
