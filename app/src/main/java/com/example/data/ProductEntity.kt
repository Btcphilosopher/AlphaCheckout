package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val barcode: String,
    val sku: String,
    val name: String,
    val basePrice: Double,
    val taxRate: Double, // e.g. 0.20 for 20% VAT
    val category: String,
    val stockQuantity: Int,
    val iconName: String // icon representation identifier
)
