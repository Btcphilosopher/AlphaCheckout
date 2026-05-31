package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String, // UUID string
    val storeLocation: String,
    val kioskId: String,
    val customerLoyaltyTier: String, // "NONE", "SILVER", "GOLD", "PLATINUM"
    val timestamp: Long,
    val subtotal: Double,
    val taxAmount: Double,
    val discountAmount: Double,
    val totalAmount: Double,
    val paymentStatus: String, // "INITIATED", "AUTHORIZED", "FAILED", "COMPLETED", "REFUNDED"
    val paymentType: String, // "CARD", "MOBILE", "POINTS"
    val riskScore: Int, // 0 to 100 risk score
    val loyaltyPointsEarned: Int
)
