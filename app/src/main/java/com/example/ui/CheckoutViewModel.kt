package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CheckoutViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = CheckoutRepository(db)

    // Exposed Flows from Room persistence
    val products = repository.allProducts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val transactions = repository.allTransactions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val promoRules = repository.allPromoRules.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Active session state
    var sessionId = androidx.compose.runtime.mutableStateOf<String?>(null)
    var activeStoreLocation = androidx.compose.runtime.mutableStateOf("Oxford Street, London (#104)")
    var activeKioskId = androidx.compose.runtime.mutableStateOf("KIOSK-04")
    var selectedLoyaltyTier = androidx.compose.runtime.mutableStateOf("NONE")
    var appliedCoupon = androidx.compose.runtime.mutableStateOf<String?>(null)

    // Session's scan parameters
    var scannedItems = androidx.compose.runtime.mutableStateOf<Map<ProductEntity, Int>>(emptyMap())

    // History counters for fraud analyzer
    var couponAttempts = androidx.compose.runtime.mutableStateOf(0)
    var manualRemovalsCount = androidx.compose.runtime.mutableStateOf(0)
    var scanTimestamps = androidx.compose.runtime.mutableStateOf<List<Long>>(emptyList())

    // Risk outcome
    var riskAssessment = androidx.compose.runtime.mutableStateOf<RiskAssessment>(RiskAssessment(10, "LOW RISK", emptyList()))

    // Payment State Machine: "NONE", "INITIATED", "AUTHORIZED", "FAILED", "COMPLETED", "REFUNDED"
    var paymentState = androidx.compose.runtime.mutableStateOf("NONE")

    // Logs for REST API Explorer
    var apiLogs = androidx.compose.runtime.mutableStateOf<List<ApiLog>>(emptyList())
    var selectedLog = androidx.compose.runtime.mutableStateOf<ApiLog?>(null)

    // Loading/status messages
    var uiFeedbackMessage = androidx.compose.runtime.mutableStateOf<String?>(null)

    init {
        // Initialize default catalogs in database
        viewModelScope.launch {
            repository.initializeDefaultData()
        }
    }

    /**
     * Compute current basket using the Pricing Engine in real time
     */
    fun getBasketCalculation(): BasketCalculationResult {
        val activeRules = run {
            // Synchronously extract promo rules value or empty list
            promoRules.value
        }
        return CheckoutEngine.calculateBasket(
            scannedItems = scannedItems.value,
            loyaltyTier = selectedLoyaltyTier.value,
            couponCode = appliedCoupon.value,
            rules = activeRules
        )
    }

    private fun triggerRiskAnalysis() {
        riskAssessment.value = CheckoutEngine.analyzeRisk(
            scanTimestamps = scanTimestamps.value,
            scannedItems = scannedItems.value,
            couponAttempts = couponAttempts.value,
            manualRemovalsCount = manualRemovalsCount.value
        )
    }

    // ==========================================
    // RETAIL WEB SERVICE API SIMULATION METHODS
    // ==========================================

    fun apiCreateSession(store: String, kiosk: String, tier: String) {
        val startTime = System.currentTimeMillis()
        val newSessionId = UUID.randomUUID().toString().take(8)
        
        sessionId.value = newSessionId
        activeStoreLocation.value = store
        activeKioskId.value = kiosk
        selectedLoyaltyTier.value = tier
        appliedCoupon.value = null
        scannedItems.value = emptyMap()
        couponAttempts.value = 0
        manualRemovalsCount.value = 0
        scanTimestamps.value = emptyList()
        paymentState.value = "NONE"
        riskAssessment.value = RiskAssessment(10, "LOW RISK", emptyList())

        val requestJson = """
            {
               "storeLocation": "$store",
               "kioskId": "$kiosk",
               "customerLoyaltyTier": "$tier"
            }
        """.trimIndent()

        val responseJson = """
            {
               "sessionId": "$newSessionId",
               "storeLocation": "$store",
               "kioskId": "$kiosk",
               "customerLoyaltyTier": "$tier",
               "status": "ACTIVE",
               "createdAt": "${Date()}"
            }
        """.trimIndent()

        logApiCall(
            method = "POST",
            endpoint = "/api/v1/checkout/session/create",
            req = requestJson,
            res = responseJson,
            code = 201,
            timeStart = startTime
        )
    }

    fun apiGetSession() {
        val startTime = System.currentTimeMillis()
        val sessId = sessionId.value
        if (sessId == null) {
            logApiCall("GET", "/api/v1/checkout/session/null", "", "{\"error\": \"No active checkout session\"}", 404, startTime)
            return
        }

        val calc = getBasketCalculation()
        val itemsJson = calc.items.joinToString(separator = ",\n      ") {
            """{"barcode": "${it.product.barcode}", "name": "${it.product.name}", "quantity": ${it.quantity}, "baseTotal": ${it.originalPrice}, "discount": ${it.discountAmount}, "finalTotal": ${it.finalPrice}}"""
        }

        val responseJson = """
            {
               "sessionId": "$sessId",
               "storeLocation": "${activeStoreLocation.value}",
               "loyaltyTier": "${selectedLoyaltyTier.value}",
               "status": "ACTIVE",
               "riskScore": ${riskAssessment.value.score},
               "basket": {
                  "items": [
                     $itemsJson
                  ],
                  "totals": {
                     "subtotal": ${calc.baseSubtotal},
                     "itemDiscounts": ${calc.itemDiscountsTotal},
                     "basketDiscounts": ${calc.basketDiscountsTotal},
                     "totalDiscounts": ${calc.totalDiscounts},
                     "tax": ${calc.taxTotal},
                     "grandTotal": ${calc.finalTotal}
                  }
               }
            }
        """.trimIndent()

        logApiCall(
            method = "GET",
            endpoint = "/api/v1/checkout/session/$sessId",
            req = "",
            res = responseJson,
            code = 200,
            timeStart = startTime
        )
    }

    fun apiScanProduct(barcode: String, quantity: Int = 1) {
        val startTime = System.currentTimeMillis()
        val sessId = sessionId.value
        if (sessId == null) {
            logApiCall("POST", "/api/v1/checkout/session/null/scan", "{\"barcode\":\"$barcode\"}", "{\"error\":\"No active session\"}", 400, startTime)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val product = repository.getProduct(barcode)
            if (product == null) {
                launch(Dispatchers.Main) {
                    uiFeedbackMessage.value = "Barcode not found: $barcode"
                    logApiCall(
                        method = "POST",
                        endpoint = "/api/v1/checkout/session/$sessId/scan",
                        req = """{"barcode": "$barcode", "quantity": $quantity}""",
                        res = """{"error": "Product not found in systems metadata lookup database."}""",
                        code = 404,
                        timeStart = startTime
                    )
                }
                return@launch
            }

            if (product.stockQuantity < quantity) {
                launch(Dispatchers.Main) {
                    uiFeedbackMessage.value = "Out of stock! Only ${product.stockQuantity} remaining."
                    logApiCall(
                        method = "POST",
                        endpoint = "/api/v1/checkout/session/$sessId/scan",
                        req = """{"barcode": "$barcode", "quantity": $quantity}""",
                        res = """{"error": "Out of stock. Requested: $quantity, In-stock: ${product.stockQuantity}"}""",
                        code = 409,
                        timeStart = startTime
                    )
                }
                return@launch
            }

            launch(Dispatchers.Main) {
                // Update timestamps for rate/speed fraud check
                val currentTimes = scanTimestamps.value.toMutableList()
                currentTimes.add(System.currentTimeMillis())
                scanTimestamps.value = currentTimes

                // Add to basket map
                val currentBask = scannedItems.value.toMutableMap()
                val currentQty = currentBask[product] ?: 0
                currentBask[product] = currentQty + quantity
                scannedItems.value = currentBask

                // Re-evaluate risk
                triggerRiskAnalysis()

                val calc = getBasketCalculation()
                uiFeedbackMessage.value = "Scanned ${product.name}"

                val requestJson = """
                    {
                       "barcode": "$barcode",
                       "quantity": $quantity
                    }
                """.trimIndent()

                val responseJson = """
                    {
                       "status": "SUCCESS",
                       "scannedItem": {
                          "sku": "${product.sku}",
                          "name": "${product.name}",
                          "basePrice": ${product.basePrice},
                          "quantity": ${currentQty + quantity}
                       },
                       "runningTotal": ${calc.finalTotal},
                       "riskScore": ${riskAssessment.value.score}
                    }
                """.trimIndent()

                logApiCall(
                    method = "POST",
                    endpoint = "/api/v1/checkout/session/$sessId/scan",
                    req = requestJson,
                    res = responseJson,
                    code = 200,
                    timeStart = startTime
                )
            }
        }
    }

    fun apiRemoveItem(barcode: String) {
        val startTime = System.currentTimeMillis()
        val sessId = sessionId.value
        if (sessId == null) return

        val product = scannedItems.value.keys.find { it.barcode == barcode }
        if (product == null) {
            logApiCall(
                method = "POST",
                endpoint = "/api/v1/checkout/session/$sessId/remove",
                req = """{"barcode": "$barcode"}""",
                res = """{"error": "Item not in basket"}""",
                code = 400,
                timeStart = startTime
            )
            return
        }

        val currentBask = scannedItems.value.toMutableMap()
        val qty = currentBask[product] ?: 0
        if (qty <= 1) {
            currentBask.remove(product)
        } else {
            currentBask[product] = qty - 1
        }
        scannedItems.value = currentBask

        manualRemovalsCount.value = manualRemovalsCount.value + 1
        triggerRiskAnalysis()

        val calc = getBasketCalculation()
        uiFeedbackMessage.value = "Withdrew 1 unit of ${product.name}"

        val responseJson = """
            {
               "status": "REMOVED",
               "barcode": "$barcode",
               "remainingQuantity": ${currentBask[product] ?: 0},
               "runningTotal": ${calc.finalTotal}
            }
        """.trimIndent()

        logApiCall(
            method = "POST",
            endpoint = "/api/v1/checkout/session/$sessId/remove",
            req = """{"barcode": "$barcode"}""",
            res = responseJson,
            code = 200,
            timeStart = startTime
        )
    }

    fun apiApplyCoupon(coupon: String) {
        val startTime = System.currentTimeMillis()
        val sessId = sessionId.value
        if (sessId == null) return

        val normalized = coupon.trim().uppercase()
        if (normalized == "SAVE10") {
            appliedCoupon.value = normalized
            val calc = getBasketCalculation()
            uiFeedbackMessage.value = "SAVE10 (10% Off) applied successfully!"
            logApiCall(
                method = "POST",
                endpoint = "/api/v1/checkout/session/$sessId/coupon",
                req = """{"coupon": "$coupon"}""",
                res = """{"status": "VALID", "applied": "SAVE10", "newTotal": ${calc.finalTotal}}""",
                code = 200,
                timeStart = startTime
            )
        } else {
            couponAttempts.value = couponAttempts.value + 1
            triggerRiskAnalysis()
            uiFeedbackMessage.value = "Invalid coupon code: $coupon"
            logApiCall(
                method = "POST",
                endpoint = "/api/v1/checkout/session/$sessId/coupon",
                req = """{"coupon": "$coupon"}""",
                res = """{"status": "INVALID_COUPON_FAULT", "provided": "$coupon", "riskImpact": 30}""",
                code = 422,
                timeStart = startTime
            )
        }
    }

    fun apiGetTotal() {
        val startTime = System.currentTimeMillis()
        val sessId = sessionId.value
        if (sessId == null) return

        val calc = getBasketCalculation()
        val responseJson = """
            {
               "subtotal": ${calc.baseSubtotal},
               "tax": ${calc.taxTotal},
               "discounts": ${calc.totalDiscounts},
               "finalTotal": ${calc.finalTotal},
               "loyaltyPointsEarned": ${calc.loyaltyPointsEarned}
            }
        """.trimIndent()

        logApiCall(
            method = "GET",
            endpoint = "/api/v1/checkout/session/$sessId/total",
            req = "",
            res = responseJson,
            code = 200,
            timeStart = startTime
        )
    }

    fun apiPaySession(paymentType: String) {
        val startTime = System.currentTimeMillis()
        val sessId = sessionId.value
        if (sessId == null) return

        paymentState.value = "INITIATED"
        viewModelScope.launch {
            // Fast State Transitions simulating secure background token handshake
            kotlinx.coroutines.delay(400)
            paymentState.value = "AUTHORIZED"

            val calc = getBasketCalculation()
            val dummyToken = "tok_checkout_" + UUID.randomUUID().toString().take(6)

            val reqJson = """
                {
                   "paymentMethodType": "$paymentType",
                   "amount": ${calc.finalTotal},
                   "currency": "GBP"
                }
            """.trimIndent()

            val resJson = """
                {
                   "status": "AUTHORIZED",
                   "paymentToken": "$dummyToken",
                   "authorizedAmount": ${calc.finalTotal},
                   "gatewayResponse": "APPROVED_00_AUTHENTICATED",
                   "verificationAudit": "CVV2_PASS_SIGNATURE_NONE"
                }
            """.trimIndent()

            logApiCall(
                method = "POST",
                endpoint = "/api/v1/checkout/session/$sessId/pay",
                req = reqJson,
                res = resJson,
                code = 200,
                timeStart = startTime
            )
        }
    }

    fun apiCompleteAndInvoice() {
        val startTime = System.currentTimeMillis()
        val sessId = sessionId.value ?: return

        paymentState.value = "COMPLETED"
        uiFeedbackMessage.value = "Processing and Finalising Invoice..."

        viewModelScope.launch(Dispatchers.IO) {
            val calc = getBasketCalculation()
            val transactionId = UUID.randomUUID().toString()

            val transaction = TransactionEntity(
                id = transactionId,
                storeLocation = activeStoreLocation.value,
                kioskId = activeKioskId.value,
                customerLoyaltyTier = selectedLoyaltyTier.value,
                timestamp = System.currentTimeMillis(),
                subtotal = calc.baseSubtotal,
                taxAmount = calc.taxTotal,
                discountAmount = calc.totalDiscounts,
                totalAmount = calc.finalTotal,
                paymentStatus = "COMPLETED",
                paymentType = "CARD",
                riskScore = riskAssessment.value.score,
                loyaltyPointsEarned = calc.loyaltyPointsEarned
            )

            val itemsList = calc.items.map {
                TransactionItemEntity(
                    transactionId = transactionId,
                    barcode = it.product.barcode,
                    name = it.product.name,
                    quantity = it.quantity,
                    unitPrice = it.product.basePrice,
                    appliedDiscountAmount = it.discountAmount,
                    totalPrice = it.finalPrice
                )
            }

            repository.saveTransaction(transaction, itemsList)

            launch(Dispatchers.Main) {
                val itemizedListJson = itemsList.joinToString(separator = ",\n         ") {
                    """{"product": "${it.name}", "qty": ${it.quantity}, "unitPrice": ${it.unitPrice}, "total": ${it.totalPrice}}"""
                }

                val resJson = """
                    {
                       "transactionId": "$transactionId",
                       "status": "SUCCESS_CLOSED",
                       "timestamp": ${transaction.timestamp},
                       "totals": {
                          "subtotal": ${transaction.subtotal},
                          "tax": ${transaction.taxAmount},
                          "discounts": ${transaction.discountAmount},
                          "totalAmount": ${transaction.totalAmount}
                       },
                       "items": [
                          $itemizedListJson
                       ],
                       "inventoryDecremented": true,
                       "riskAuditScore": ${transaction.riskScore}
                    }
                """.trimIndent()

                logApiCall(
                    method = "POST",
                    endpoint = "/api/v1/checkout/session/$sessId/complete",
                    req = "",
                    res = resJson,
                    code = 200,
                    timeStart = startTime
                )

                uiFeedbackMessage.value = "Transaction final! Receipt printed."
            }
        }
    }

    fun apiGetReceiptDirectly(transactionId: String) {
        val startTime = System.currentTimeMillis()
        viewModelScope.launch(Dispatchers.IO) {
            val tx = db.transactionDao().getTransactionById(transactionId)
            if (tx == null) {
                launch(Dispatchers.Main) {
                    logApiCall(
                        method = "GET",
                        endpoint = "/api/v1/receipt/$transactionId",
                        req = "",
                        res = """{"error": "Transaction not found."}""",
                        code = 404,
                        timeStart = startTime
                    )
                }
                return@launch
            }

            val items = db.transactionDao().getItemsByTransactionId(transactionId)
            launch(Dispatchers.Main) {
                val itemsJson = items.joinToString(",\n         ") {
                    """{"name": "${it.name}", "qty": ${it.quantity}, "unitPrice": ${it.unitPrice}, "finalPrice": ${it.totalPrice}}"""
                }

                val responseJson = """
                    {
                       "receiptType": "DIGITAL_TAX_INVOICE",
                       "store": "${tx.storeLocation}",
                       "kiosk": "${tx.kioskId}",
                       "timestamp": ${tx.timestamp},
                       "payment": {
                          "status": "${tx.paymentStatus}",
                          "method": "${tx.paymentType}"
                       },
                       "basketBreakdown": [
                          $itemsJson
                       ],
                       "feesSummary": {
                          "netSubtotal": ${tx.subtotal},
                          "discountsGiven": ${tx.discountAmount},
                          "taxesEstimated": ${tx.taxAmount},
                          "grossPaid": ${tx.totalAmount}
                       },
                       "loyaltyEarned": ${tx.loyaltyPointsEarned}
                    }
                """.trimIndent()

                logApiCall(
                    method = "GET",
                    endpoint = "/api/v1/receipt/$transactionId",
                    req = "",
                    res = responseJson,
                    code = 200,
                    timeStart = startTime
                )
            }
        }
    }

    // ==========================================
    // BACKEND CONFIGURATION / UTILITIES
    // ==========================================

    fun addNewProductToDb(
        barcode: String,
        sku: String,
        name: String,
        price: Double,
        taxRate: Double,
        category: String,
        stock: Int,
        icon: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val newProd = ProductEntity(
                barcode = barcode.trim(),
                sku = sku.trim().uppercase(),
                name = name.trim(),
                basePrice = price,
                taxRate = taxRate,
                category = category,
                stockQuantity = stock,
                iconName = icon
            )
            repository.insertProduct(newProd)
            launch(Dispatchers.Main) {
                uiFeedbackMessage.value = "Product '${name}' registered in central repo!"
            }
        }
    }

    fun deletePromoRule(rule: PromoRuleEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deletePromoRule(rule)
        }
    }

    fun addNewPromoRule(rule: PromoRuleEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertPromoRule(rule)
        }
    }

    fun resetSession() {
        sessionId.value = null
        scannedItems.value = emptyMap()
        couponAttempts.value = 0
        manualRemovalsCount.value = 0
        scanTimestamps.value = emptyList()
        paymentState.value = "NONE"
        appliedCoupon.value = null
        riskAssessment.value = RiskAssessment(10, "LOW RISK", emptyList())
    }

    fun clearApiLogs() {
        apiLogs.value = emptyList()
        selectedLog.value = null
    }

    private fun logApiCall(
        method: String,
        endpoint: String,
        req: String,
        res: String,
        code: Int,
        timeStart: Long
    ) {
        val latency = System.currentTimeMillis() - timeStart
        val newLog = ApiLog(
            method = method,
            endpoint = endpoint,
            requestBody = req,
            responseCode = code,
            responseBody = res,
            latencyMs = latency
        )
        apiLogs.value = listOf(newLog) + apiLogs.value
        selectedLog.value = newLog
    }
}
