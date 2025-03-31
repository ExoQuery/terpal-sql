package io.exoquery.controller

@RequiresOptIn(message = "This is internal Terpal-SQL API and may change in the future.")
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class TerpalSqlInternal
