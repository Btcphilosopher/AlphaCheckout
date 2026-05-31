package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transaction_items")
data class TransactionItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: String,
    val barcode: String,
    val name: String,
    val quantity: Int,
    val unitPrice: Double,
    val appliedDiscountAmount: Double,
    val totalPrice: Double
)
