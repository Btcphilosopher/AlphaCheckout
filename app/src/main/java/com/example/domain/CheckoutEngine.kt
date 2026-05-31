package com.example.domain

import com.example.data.ProductEntity
import com.example.data.PromoRuleEntity

/**
 * Result of computing the shopping basket prices, discounts and taxes in real time.
 */
data class BasketCalculationResult(
    val items: List<CalculatedItem>,
    val baseSubtotal: Double,
    val itemDiscountsTotal: Double,
    val basketDiscountsTotal: Double,
    val totalDiscounts: Double,
    val taxTotal: Double,
    val finalTotal: Double,
    val loyaltyPointsEarned: Int,
    val appliedBasketPromos: List<String>
)

data class CalculatedItem(
    val product: ProductEntity,
    val quantity: Int,
    val originalPrice: Double, // basePrice * quantity
    val discountAmount: Double,
    val finalPrice: Double,
    val taxAmount: Double,
    val appliedPromoName: String?
)

/**
 * Audit and Security risk evaluation outcome.
 */
data class RiskAssessment(
    val score: Int,
    val riskLevel: String, // "LOW", "MEDIUM", "HIGH"
    val flags: List<String>
)

object CheckoutEngine {

    /**
     * Solves the pricing engine formula:
     * 1. Calculates base item-level price = price * qty.
     * 2. Runs item-level promotions (e.g. BUY_2_GET_1_FREE).
     * 3. Aggregates item-level prices to compute Basket subtotal.
     * 4. Evaluates basket-level discounts (10% over £50, Loyalty Tier percentage).
     * 5. Computes Taxes based on the final discounted price of items.
     * 6. Appends loyalty points (currency-based, e.g. 1 point per £1 spent).
     */
    fun calculateBasket(
        scannedItems: Map<ProductEntity, Int>,
        loyaltyTier: String,
        couponCode: String?,
        rules: List<PromoRuleEntity>
    ): BasketCalculationResult {
        val calculatedItems = mutableListOf<CalculatedItem>()
        var baseSubtotalSum = 0.0
        var itemDiscountsTotalSum = 0.0

        for ((product, qty) in scannedItems) {
            val originalPrice = product.basePrice * qty
            var discountAmount = 0.0
            var appliedRuleName: String? = null

            // Find item-level rules (TargetBarcode match)
            val itemRule = rules.firstOrNull { it.isActive && it.targetBarcode == product.barcode }
            if (itemRule != null) {
                when (itemRule.ruleType) {
                    "BUY_2_GET_1_FREE" -> {
                        if (qty >= 3) {
                            val freeItems = qty / 3
                            discountAmount = freeItems * product.basePrice
                            appliedRuleName = itemRule.name
                        }
                    }
                    "PERCENT_OFF" -> {
                        discountAmount = originalPrice * (itemRule.discountPercent / 100.0)
                        appliedRuleName = itemRule.name
                    }
                }
            }

            val finalPrice = (originalPrice - discountAmount).coerceAtLeast(0.0)
            // Compute tax on the discounted price (VAT on the real sale price)
            val taxAmount = finalPrice * product.taxRate

            calculatedItems.add(
                CalculatedItem(
                    product = product,
                    quantity = qty,
                    originalPrice = originalPrice,
                    discountAmount = discountAmount,
                    finalPrice = finalPrice,
                    taxAmount = taxAmount,
                    appliedPromoName = appliedRuleName
                )
            )

            baseSubtotalSum += originalPrice
            itemDiscountsTotalSum += discountAmount
        }

        var runningTotalAndTaxes = calculatedItems.sumOf { it.finalPrice }
        var basketDiscountsTotalSum = 0.0
        val appliedBasketPromos = mutableListOf<String>()

        // 1. Coupon-code handling
        if (couponCode != null && couponCode.trim().uppercase() == "SAVE10") {
            val couponDiscount = runningTotalAndTaxes * 0.10
            basketDiscountsTotalSum += couponDiscount
            runningTotalAndTaxes -= couponDiscount
            appliedBasketPromos.add("SAVE10 Coupon (10% Off)")
        }

        // 2. "10% Off Basket Over £50" promo rule
        val overFiftyRule = rules.firstOrNull { it.isActive && it.ruleType == "PERCENT_OFF_BASKET_OVER_X" }
        if (overFiftyRule != null && runningTotalAndTaxes >= overFiftyRule.minimumBasketAmount) {
            val valueDiscount = runningTotalAndTaxes * (overFiftyRule.discountPercent / 100.0)
            basketDiscountsTotalSum += valueDiscount
            runningTotalAndTaxes -= valueDiscount
            appliedBasketPromos.add(overFiftyRule.name)
        }

        // 3. Loyalty Tier Discount Rules (Silver = 5%, Gold = 10%, Platinum = 20%)
        if (loyaltyTier != "NONE") {
            val loyaltyRuleName = "LOYALTY_DISCOUNT_TIER"
            val tierRule = rules.firstOrNull { it.isActive && it.ruleType == loyaltyRuleName && it.name.contains(loyaltyTier, ignoreCase = true) }
            if (tierRule != null) {
                val tierDiscount = runningTotalAndTaxes * (tierRule.discountPercent / 100.0)
                basketDiscountsTotalSum += tierDiscount
                runningTotalAndTaxes -= tierDiscount
                appliedBasketPromos.add(tierRule.name)
            }
        }

        // Final tax sum is aggregated tax of items, proportionately reduced if basket discounts were applied
        val rawTaxTotal = calculatedItems.sumOf { it.taxAmount }
        val finalDiscountRatio = if (baseSubtotalSum - itemDiscountsTotalSum > 0) {
            runningTotalAndTaxes / (baseSubtotalSum - itemDiscountsTotalSum)
        } else {
            1.0
        }
        val taxTotal = rawTaxTotal * finalDiscountRatio

        val finalTotal = runningTotalAndTaxes.coerceAtLeast(0.0)
        
        // Loyalty points earned: 1 point for every 1.0 item currency spent post-discounts
        val loyaltyPointsEarned = finalTotal.toInt()

        return BasketCalculationResult(
            items = calculatedItems,
            baseSubtotal = baseSubtotalSum,
            itemDiscountsTotal = itemDiscountsTotalSum,
            basketDiscountsTotal = basketDiscountsTotalSum,
            totalDiscounts = itemDiscountsTotalSum + basketDiscountsTotalSum,
            taxTotal = taxTotal,
            finalTotal = finalTotal,
            loyaltyPointsEarned = loyaltyPointsEarned,
            appliedBasketPromos = appliedBasketPromos
        )
    }

    /**
     * Rule-based engine computing enterprise kiosk security risk flags and vulnerability/fraud probability.
     */
    fun analyzeRisk(
        scanTimestamps: List<Long>,
        scannedItems: Map<ProductEntity, Int>,
        couponAttempts: Int,
        manualRemovalsCount: Int
    ): RiskAssessment {
        val flags = mutableListOf<String>()
        var score = 10 // baseline risk score

        // 1. Rapid Scanning Rate check (e.g. scanning multiple codes within 1000ms)
        if (scanTimestamps.size >= 2) {
            val fastScans = scanTimestamps.zipWithNext().count { (t1, t2) ->
                (t2 - t1) < 1200 // less than 1.2 seconds between scans
            }
            if (fastScans > 0) {
                val addScore = fastScans * 15
                score += addScore
                flags.add("Rapid sequential items scan (<1.2s between barcode signals)")
            }
        }

        // 2. High product quantities per lookup
        val highQuantityProducts = scannedItems.filter { it.value > 8 }
        if (highQuantityProducts.isNotEmpty()) {
            score += 25
            flags.add("Abnormal container volume: ${highQuantityProducts.size} items with Qty > 8")
        }

        // 3. Repeated coupon failures
        if (couponAttempts >= 3) {
            score += 30
            flags.add("Excessive coupon validation faults ($couponAttempts failure state triggers)")
        }

        // 4. Repeated scanner removals (frequent delete signals can imply barcode manipulation fraud)
        if (manualRemovalsCount >= 3) {
            score += 20
            flags.add("Suspect hand-offs: $manualRemovalsCount active items withdrawn from check-out state")
        }

        score = score.coerceIn(0, 100)
        val level = when {
            score >= 60 -> "HIGH RISK"
            score >= 30 -> "MEDIUM RISK"
            else -> "LOW RISK"
        }

        return RiskAssessment(score, level, flags)
    }
}
