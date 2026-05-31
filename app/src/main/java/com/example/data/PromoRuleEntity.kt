package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "promo_rules")
data class PromoRuleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val ruleType: String, // "BUY_2_GET_1_FREE", "PERCENT_OFF_BASKET_OVER_X", "LOYALTY_DISCOUNT_TIER"
    val targetBarcode: String?, // Nullable if applies to all / basket-level
    val discountPercent: Double = 0.0, // e.g. 10.0 for 10%
    val minimumBasketAmount: Double = 0.0,
    val isActive: Boolean = true
)
