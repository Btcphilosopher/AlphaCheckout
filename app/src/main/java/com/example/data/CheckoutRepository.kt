package com.example.data

import kotlinx.coroutines.flow.Flow
import java.util.UUID

class CheckoutRepository(private val database: AppDatabase) {

    val allProducts: Flow<List<ProductEntity>> = database.productDao().getAllProducts()
    val allTransactions: Flow<List<TransactionEntity>> = database.transactionDao().getAllTransactions()
    val allPromoRules: Flow<List<PromoRuleEntity>> = database.promoRuleDao().getAllPromoRulesFlow()

    suspend fun getProduct(barcode: String): ProductEntity? {
        return database.productDao().getProductByBarcode(barcode)
    }

    suspend fun insertProduct(product: ProductEntity) {
        database.productDao().insertProduct(product)
    }

    suspend fun updateStock(barcode: String, newStock: Int) {
        database.productDao().updateStock(barcode, newStock)
    }

    suspend fun insertPromoRule(rule: PromoRuleEntity) {
        database.promoRuleDao().insertPromoRule(rule)
    }

    suspend fun deletePromoRule(rule: PromoRuleEntity) {
        database.promoRuleDao().deleteRule(rule)
    }

    suspend fun getActiveRules(): List<PromoRuleEntity> {
        return database.promoRuleDao().getActiveRules()
    }

    suspend fun getTransactionItems(txId: String): List<TransactionItemEntity> {
        return database.transactionDao().getItemsByTransactionId(txId)
    }

    suspend fun saveTransaction(tx: TransactionEntity, items: List<TransactionItemEntity>) {
        // First deduct the stock for all purchased items
        for (item in items) {
            val prod = database.productDao().getProductByBarcode(item.barcode)
            if (prod != null) {
                val newStock = (prod.stockQuantity - item.quantity).coerceAtLeast(0)
                database.productDao().updateStock(item.barcode, newStock)
            }
        }
        database.transactionDao().insertTransaction(tx)
        database.transactionDao().insertTransactionItems(items)
    }

    suspend fun initializeDefaultData() {
        val rules = database.promoRuleDao().getActiveRules()
        if (rules.isEmpty()) {
            val defaultRules = listOf(
                PromoRuleEntity(
                    id = "R1",
                    name = "Buy 2 Get 1 Free (Special Bananas)",
                    ruleType = "BUY_2_GET_1_FREE",
                    targetBarcode = "5010203040501"
                ),
                PromoRuleEntity(
                    id = "R2",
                    name = "10% Off Basket Over £50",
                    ruleType = "PERCENT_OFF_BASKET_OVER_X",
                    targetBarcode = null,
                    discountPercent = 10.0,
                    minimumBasketAmount = 50.0
                ),
                PromoRuleEntity(
                    id = "R3",
                    name = "Silver Member 5% Off",
                    ruleType = "LOYALTY_DISCOUNT_TIER",
                    targetBarcode = null,
                    discountPercent = 5.0
                ),
                PromoRuleEntity(
                    id = "R4",
                    name = "Gold Member 10% Off",
                    ruleType = "LOYALTY_DISCOUNT_TIER",
                    targetBarcode = null,
                    discountPercent = 10.0
                ),
                PromoRuleEntity(
                    id = "R5",
                    name = "Platinum Member 20% Off",
                    ruleType = "LOYALTY_DISCOUNT_TIER",
                    targetBarcode = null,
                    discountPercent = 20.0
                )
            )
            database.promoRuleDao().insertPromoRules(defaultRules)
        }

        // Pre-populate realistic retail grocery or high-end items
        val sampleProducts = listOf(
            ProductEntity(
                barcode = "5010203040501",
                sku = "PRD-BANANA-ORG",
                name = "Organic Fairtrade Bananas (Bunch)",
                basePrice = 2.40,
                taxRate = 0.00, // zero-rated food item in UK
                category = "Fresh Produce",
                stockQuantity = 120,
                iconName = "eco"
            ),
            ProductEntity(
                barcode = "5010203040502",
                sku = "PRD-COFFEE-ETH",
                name = "Single-Origin Ethiopian Coffee 250g",
                basePrice = 7.50,
                taxRate = 0.00, // zero-rated food
                category = "Pantry",
                stockQuantity = 45,
                iconName = "local_cafe"
            ),
            ProductEntity(
                barcode = "5010203040503",
                sku = "PRD-MILK-ORG",
                name = "Organic Semi-Skimmed Milk 1L",
                basePrice = 1.65,
                taxRate = 0.00,
                category = "Dairy & Eggs",
                stockQuantity = 80,
                iconName = "water_drop"
            ),
            ProductEntity(
                barcode = "5010203040504",
                sku = "PRD-CHOCO-DRK",
                name = "Premium Madagascan Dark Chocolate 100g",
                basePrice = 2.99,
                taxRate = 0.20, // Standard rate VAT
                category = "Confectionery",
                stockQuantity = 150,
                iconName = "cookie"
            ),
            ProductEntity(
                barcode = "5010203040505",
                sku = "PRD-AVOCADO-PK",
                name = "Ripe & Ready Avocados (4-Pack)",
                basePrice = 4.25,
                taxRate = 0.00,
                category = "Fresh Produce",
                stockQuantity = 60,
                iconName = "spa"
            ),
            ProductEntity(
                barcode = "5010203040506",
                sku = "PRD-BREAD-SD",
                name = "Artisanal Sourdough Boule 800g",
                basePrice = 3.50,
                taxRate = 0.00,
                category = "Bakery",
                stockQuantity = 30,
                iconName = "bakery_dining"
            ),
            ProductEntity(
                barcode = "5010203040507",
                sku = "PRD-WINE-MALB",
                name = "Argentinian Malbec Red Wine 75cl",
                basePrice = 12.00,
                taxRate = 0.20, // 20% Standard VAT
                category = "Alcoholic Beverages",
                stockQuantity = 25,
                iconName = "wine_bar"
            )
        )

        for (prod in sampleProducts) {
            val existing = database.productDao().getProductByBarcode(prod.barcode)
            if (existing == null) {
                database.productDao().insertProduct(prod)
            }
        }
    }
}
