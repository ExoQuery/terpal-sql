package io.exoquery.controller

@RequiresOptIn(message = "This is internal Terpal-SQL API and may change in the future.")
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD)
annotation class TerpalSqlInternal

@RequiresOptIn(message = "This is an unsafe raw-SQL API. Injections are possible.")
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class TerpalSqlUnsafe
