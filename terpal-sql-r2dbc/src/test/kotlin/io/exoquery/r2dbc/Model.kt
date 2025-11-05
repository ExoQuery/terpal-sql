package io.exoquery.r2dbc

import kotlinx.serialization.Serializable

@Serializable
data class Product(val id: Int, val description: String, val sku: Long)

fun makeProducts(num: Int): List<Product> = (1..num).map { Product(it, "Product-$it", it.toLong()) }
