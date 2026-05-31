package com.example.ui

import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ProductEntity
import com.example.data.PromoRuleEntity
import com.example.domain.ApiLog
import com.example.domain.BasketCalculationResult
import com.example.ui.theme.*
import java.text.DecimalFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CheckoutMainScreen(viewModel: CheckoutViewModel) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var activeTab by remember { mutableStateOf("kiosk") } // "kiosk", "developer", "warehouse"
    
    val feedbackMessage by remember { viewModel.uiFeedbackMessage }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(feedbackMessage) {
        feedbackMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.uiFeedbackMessage.value = null
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("app_main_scaffold"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = "Logo icon",
                            tint = SteelPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                text = "AlphaCheckout OS",
                                color = SteelSecondary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                            Text(
                                text = "High-Performance REST Checkout Core v1.4.2",
                                color = SteelPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                },
                actions = {
                    // Risk & Fraud system status badge
                    val risk = viewModel.riskAssessment.value
                    val riskColor = when (risk.riskLevel) {
                        "HIGH RISK" -> SignalFailure
                        "MEDIUM RISK" -> SignalWarning
                        else -> SignalSuccess
                    }
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(riskColor.copy(alpha = 0.15f))
                            .border(1.dp, riskColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(riskColor)
                            )
                            Text(
                                text = "SECURE AUDIT: ${risk.score}% (${risk.riskLevel})",
                                color = riskColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SpaceDarkBG,
                    titleContentColor = SteelSecondary
                )
            )
        },
        bottomBar = {
            if (!isLandscape) {
                NavigationBar(
                    containerColor = SpaceSurface,
                    tonalElevation = 8.dp,
                    windowInsets = WindowInsets.navigationBars
                ) {
                    NavigationBarItem(
                        selected = activeTab == "kiosk",
                        onClick = { activeTab = "kiosk" },
                        icon = { Icon(Icons.Default.ShoppingCart, "Kiosk UI") },
                        label = { Text("Kiosk Terminal", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = SteelPrimary,
                            indicatorColor = SteelPrimary,
                            unselectedTextColor = Color.Gray,
                            unselectedIconColor = Color.Gray
                        )
                    )
                    NavigationBarItem(
                        selected = activeTab == "developer",
                        onClick = { activeTab = "developer" },
                        icon = { Icon(Icons.Default.PlayArrow, "REST console") },
                        label = { Text("API Explorer", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = SteelPrimary,
                            indicatorColor = SteelPrimary,
                            unselectedTextColor = Color.Gray,
                            unselectedIconColor = Color.Gray
                        )
                    )
                    NavigationBarItem(
                        selected = activeTab == "warehouse",
                        onClick = { activeTab = "warehouse" },
                        icon = { Icon(Icons.Default.Settings, "Warehouse database") },
                        label = { Text("Warehouse Catalog", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = SteelPrimary,
                            indicatorColor = SteelPrimary,
                            unselectedTextColor = Color.Gray,
                            unselectedIconColor = Color.Gray
                        )
                    )
                }
            }
        }
    ) { padding ->
        val modPadding = Modifier.padding(padding)
        if (isLandscape) {
            // Tablet landscape split screen: Kiosk on Left, Developer REST console on Right
            Row(
                modifier = modPadding
                    .fillMaxSize()
                    .background(SpaceDarkBG)
            ) {
                // Left Panel: Checkout Kiosk Simulator
                Box(
                    modifier = Modifier
                        .weight(1.3f)
                        .fillMaxHeight()
                        .padding(8.dp)
                ) {
                    KioskPanel(viewModel)
                }

                // Vertical steel separating divider line
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(SpaceOutline)
                )

                // Right Panel: Split between Developer Console & Warehouse catalog
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(8.dp)
                ) {
                    TabRow(
                        selectedTabIndex = if (activeTab == "developer") 0 else 1,
                        containerColor = SpaceDarkBG,
                        contentColor = SteelPrimary,
                        divider = { HorizontalDivider(color = SpaceOutline) }
                    ) {
                        Tab(
                            selected = activeTab == "developer",
                            onClick = { activeTab = "developer" },
                            text = { Text("Live REST API Console", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                        )
                        Tab(
                            selected = activeTab == "warehouse",
                            onClick = { activeTab = "warehouse" },
                            text = { Text("Warehouse & Rules Catalog", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (activeTab == "developer") {
                            DeveloperConsolePanel(viewModel)
                        } else {
                            WarehousePanel(viewModel)
                        }
                    }
                }
            }
        } else {
            // Portrait phone mode: switcher tab selection
            Box(
                modifier = modPadding
                    .fillMaxSize()
                    .background(SpaceDarkBG)
                    .padding(8.dp)
            ) {
                when (activeTab) {
                    "kiosk" -> KioskPanel(viewModel)
                    "developer" -> DeveloperConsolePanel(viewModel)
                    else -> WarehousePanel(viewModel)
                }
            }
        }
    }
}

// =========================================================
// 🛒 KIOSK PANEL COMPONENT (THE SELF-SERVICE TERMINAL)
// =========================================================

@Composable
fun KioskPanel(viewModel: CheckoutViewModel) {
    val sessionId by remember { viewModel.sessionId }
    val productsCatalog by viewModel.products.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("kiosk_panel_container"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (sessionId == null) {
            // Welcome screen: create new retail cashier / kiosk checkout session
            KioskWelcomeSplash(viewModel)
        } else {
            val calc = viewModel.getBasketCalculation()
            
            // Session info & quick summary
            Card(
                colors = CardDefaults.cardColors(containerColor = SpaceSurface),
                border = BorderStroke(1.dp, SpaceOutline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "STORE DEPLOYMENT: ${viewModel.activeStoreLocation.value}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SteelSecondary
                        )
                        Text(
                            text = "SESSION: #$sessionId | ID: ${viewModel.activeKioskId.value}",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = SteelPrimary
                        )
                    }
                    Button(
                        onClick = { viewModel.resetSession() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Reset / Cancel", color = SignalFailure, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Two sections: Shopping Items view and Bottom control decks list
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Left shopping cart list
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    colors = CardDefaults.cardColors(containerColor = SpaceSurface.copy(alpha = 0.7f)),
                    border = BorderStroke(1.dp, SpaceOutline)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Scanned Basket Core (${calc.items.sumOf { it.quantity }} items)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = SteelPrimary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        HorizontalDivider(color = SpaceOutline, modifier = Modifier.padding(bottom = 6.dp))

                        if (calc.items.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Basket is empty",
                                        color = Color.Gray,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "Select shelf items on the right to scan barcodes",
                                        color = Color.Gray,
                                        fontSize = 10.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(calc.items) { item ->
                                    BasketItemRow(item, viewModel)
                                }
                            }
                        }
                    }
                }

                // Right interactive barcode/shelf scan engine simulator
                Card(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight(),
                    colors = CardDefaults.cardColors(containerColor = SpaceSurface),
                    border = BorderStroke(1.dp, SpaceOutline)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Touch - Scan Physical Item Sim",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = SteelSecondary
                        )
                        Text(
                            text = "Simulates infrared laser barcode capture",
                            fontSize = 9.sp,
                            color = Color.Gray
                        )
                        HorizontalDivider(color = SpaceOutline, modifier = Modifier.padding(vertical = 4.dp))

                        // Manual scan inputs
                        var manualBarcode by remember { mutableStateOf("") }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = manualBarcode,
                                onValueChange = { manualBarcode = it },
                                label = { Text("EAN/UPC Barcode", fontSize = 10.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("manual_barcode_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = SteelPrimary,
                                    unfocusedBorderColor = SpaceOutline
                                )
                            )
                            IconButton(
                                onClick = {
                                    if (manualBarcode.isNotEmpty()) {
                                        viewModel.apiScanProduct(manualBarcode, 1)
                                        manualBarcode = ""
                                    }
                                },
                                modifier = Modifier
                                    .background(SteelPrimary, RoundedCornerShape(8.dp))
                                    .size(42.dp)
                                    .testTag("barcode_submit_button")
                            ) {
                                Icon(Icons.Default.Search, "Scan Barcode key", tint = SpaceDarkBG)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Interactive Smart Shelf:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = SteelPrimary
                        )

                        // Lazy Vertical list of catalog items to tap-and-scan!
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(1),
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = PaddingValues(top = 4.dp)
                        ) {
                            items(productsCatalog) { prod ->
                                ShelfItemScanButton(prod) {
                                    viewModel.apiScanProduct(prod.barcode, 1)
                                }
                            }
                        }
                    }
                }
            }

            // Bottom control summaries (Discount codes, Loyalty programs, and sliding payments checkouts)
            Card(
                colors = CardDefaults.cardColors(containerColor = SpaceSurface),
                border = BorderStroke(1.dp, SpaceOutline)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Discount & loyalty inputs
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Coupon and Loyalty Program", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = SteelSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            // Coupon field
                            var couponInput by remember { mutableStateOf("") }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                OutlinedTextField(
                                    value = couponInput,
                                    onValueChange = { couponInput = it },
                                    placeholder = { Text("Coupon Code (SAVE10)", fontSize = 10.sp) },
                                    singleLine = true,
                                    
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .testTag("coupon_input"),
                                    
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = SteelSecondary,
                                        unfocusedBorderColor = SpaceOutline
                                    )
                                )
                                Button(
                                    onClick = { 
                                        if (couponInput.isNotEmpty()) {
                                            viewModel.apiApplyCoupon(couponInput) 
                                            couponInput = ""
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    modifier = Modifier.height(34.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = SteelSecondary)
                                ) {
                                    Text("Apply", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            // Loyalty Tier labels
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Loyalty:", fontSize = 10.sp, color = Color.Gray)
                                listOf("NONE", "SILVER", "GOLD", "PLATINUM").forEach { tier ->
                                    val isSelected = viewModel.selectedLoyaltyTier.value == tier
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (isSelected) SteelPrimary else SpaceOutline)
                                            .clickable { viewModel.selectedLoyaltyTier.value = tier }
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = tier,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else SteelSecondary
                                        )
                                    }
                                }
                            }
                        }

                        // Checkout payment states slide-to-submit representation
                        PaymentAndPricingSummarizer(calc, viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun ShelfItemScanButton(product: ProductEntity, onScan: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SpaceDarkBG)
            .border(1.dp, SpaceOutline, RoundedCornerShape(8.dp))
            .clickable(onClick = onScan)
            .padding(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Display category icon symbol simulation
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(SteelSecondary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when(product.category) {
                            "Fresh Produce" -> Icons.Default.Star
                            "Pantry" -> Icons.Default.ShoppingCart
                            "Dairy & Eggs" -> Icons.Default.Favorite
                            "Alcoholic Beverages" -> Icons.Default.Share
                            else -> Icons.Default.ShoppingCart
                        },
                        contentDescription = "cat",
                        tint = SteelPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Column {
                    Text(
                        text = product.name,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SteelSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "SKU: ${product.sku} | STOCK: ${product.stockQuantity}",
                        fontSize = 9.sp,
                        color = if (product.stockQuantity < 10) SignalFailure else Color.Gray,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Text(
                text = "£${DecimalFormat("0.00").format(product.basePrice)}",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = SteelPrimary,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun BasketItemRow(item: com.example.domain.CalculatedItem, viewModel: CheckoutViewModel) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SpaceSurface)
            .border(1.dp, SpaceOutline, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.product.name,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SteelSecondary
                )
                if (item.appliedPromoName != null) {
                    Text(
                        text = "🎁 ${item.appliedPromoName}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = SignalSuccess
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "£${DecimalFormat("0.00").format(item.product.basePrice)} x ${item.quantity}",
                        fontSize = 10.sp,
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace
                    )
                    if (item.discountAmount > 0) {
                        Text(
                            text = "-£${DecimalFormat("0.00").format(item.discountAmount)}",
                            fontSize = 10.sp,
                            color = SignalFailure,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
            
            // Increment / Decrement scanner simulation triggers
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = { viewModel.apiRemoveItem(item.product.barcode) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Delete, "Minus", tint = Color.Gray, modifier = Modifier.size(16.dp))
                }
                Text(
                    text = "${item.quantity}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = SteelSecondary,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
                IconButton(
                    onClick = { viewModel.apiScanProduct(item.product.barcode, 1) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Add, "Plus", tint = SteelPrimary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun PaymentAndPricingSummarizer(calc: BasketCalculationResult, viewModel: CheckoutViewModel) {
    val df = DecimalFormat("0.00")
    val payState by remember { viewModel.paymentState }

    Column(
        modifier = Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SpaceDarkBG)
            .border(1.dp, SpaceOutline, RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Subtotal", fontSize = 10.sp, color = Color.Gray)
            Text("£${df.format(calc.baseSubtotal)}", fontSize = 10.sp, color = SteelSecondary, fontFamily = FontFamily.Monospace)
        }
        if (calc.totalDiscounts > 0) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Discounts Applied", fontSize = 10.sp, color = SignalSuccess)
                Text("-£${df.format(calc.totalDiscounts)}", fontSize = 10.sp, color = SignalSuccess, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Tax (VAT)", fontSize = 10.sp, color = Color.Gray)
            Text("£${df.format(calc.taxTotal)}", fontSize = 10.sp, color = SteelSecondary, fontFamily = FontFamily.Monospace)
        }
        
        HorizontalDivider(color = SpaceOutline, modifier = Modifier.padding(vertical = 2.dp))
        
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("BASKET TOTAL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SteelPrimary)
            Text("£${df.format(calc.finalTotal)}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = SteelPrimary, fontFamily = FontFamily.Monospace)
        }

        Spacer(modifier = Modifier.height(6.dp))

        // State Machine actions button representation
        when (payState) {
            "NONE" -> {
                Button(
                    onClick = { viewModel.apiPaySession("CARD") },
                    colors = ButtonDefaults.buttonColors(containerColor = SteelPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .testTag("pay_kiosk_button"),
                    contentPadding = PaddingValues(0.dp),
                    enabled = calc.items.isNotEmpty()
                ) {
                    Text("Swipe / Pay £${df.format(calc.finalTotal)}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
            "INITIATED", "AUTHORIZED" -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "PAYMENT AUTHORIZED", 
                        color = SignalSuccess, 
                        fontSize = 10.sp, 
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = { viewModel.apiCompleteAndInvoice() },
                        colors = ButtonDefaults.buttonColors(containerColor = SteelSecondary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .testTag("complete_invoice_button"),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Print Invoice / Finish", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            "COMPLETED" -> {
                Button(
                    onClick = { viewModel.resetSession() },
                    colors = ButtonDefaults.buttonColors(containerColor = SpaceOutline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp), 
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Check, "Done", tint = SignalSuccess, modifier = Modifier.size(16.dp))
                        Text("Checkout Session Done", fontSize = 10.sp, color = SignalSuccess, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun KioskWelcomeSplash(viewModel: CheckoutViewModel) {
    val clipboard = LocalClipboardManager.current
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(SpaceSurface)
            .border(1.dp, SpaceOutline, RoundedCornerShape(12.dp))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.widthIn(max = 450.dp)
        ) {
            // Cosmic neon glow orb symbol representation
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(SteelPrimary.copy(alpha = 0.15f))
                    .border(1.5.dp, SteelPrimary, RoundedCornerShape(32.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Lock checkout",
                    tint = SteelPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Text(
                text = "AlphaCheckout Hardware Core",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = SteelSecondary,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Kiosk is secure and connected to cloud REST gateway. Create an active session transaction to enable terminal checkout hardware scanning.",
                fontSize = 11.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )

            HorizontalDivider(color = SpaceOutline, modifier = Modifier.padding(vertical = 4.dp))

            // Choose Location Config
            var locationSelection by remember { mutableStateOf("Kiosk #08 - Kings Cross, London") }
            var loyaltySelection by remember { mutableStateOf("NONE") }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                Text("Select Kiosk Location Deploy Node:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SteelPrimary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Kings Cross", "Airport T5", "Oxford St").forEach { loc ->
                        val nodeName = "Kiosk #08 - $loc, London"
                        val isSel = locationSelection == nodeName
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) SteelPrimary else SpaceDarkBG)
                                .clickable { locationSelection = nodeName }
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(loc, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isSel) Color.White else SteelSecondary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text("Inbound Customer Loyalty Card:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SteelPrimary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("NONE", "SILVER", "GOLD", "PLATINUM").forEach { tier ->
                        val isSel = loyaltySelection == tier
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) SteelPrimary else SpaceDarkBG)
                                .clickable { loyaltySelection = tier }
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(tier, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (isSel) Color.White else SteelSecondary)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    viewModel.apiCreateSession(
                        store = locationSelection,
                        kiosk = "KIOSK-NODE-08",
                        tier = loyaltySelection
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("initiate_session_button"),
                colors = ButtonDefaults.buttonColors(containerColor = SteelPrimary)
            ) {
                Text("Initialize Session (REST POST)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

// =========================================================
// 🌐 DEVELOPER CONSOLE PANEL (API EXPLORER & RESPONSE VISUALS)
// =========================================================

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DeveloperConsolePanel(viewModel: CheckoutViewModel) {
    val logs by remember { viewModel.apiLogs }
    val selLog by remember { viewModel.selectedLog }
    val sessionId by remember { viewModel.sessionId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("developer_console_container"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Headers controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("REST Endpoints Request Inspector", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SteelSecondary)
                Text("Observes REST API transaction logs inside checkout sandboxes", fontSize = 10.sp, color = Color.Gray)
            }
            IconButton(
                onClick = { viewModel.clearApiLogs() },
                modifier = Modifier
                    .background(SpaceOutline, RoundedCornerShape(8.dp))
                    .size(32.dp)
            ) {
                Icon(Icons.Default.Delete, "clear log", tint = Color.LightGray, modifier = Modifier.size(16.dp))
            }
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Left list of REST request logs
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = SpaceSurface),
                border = BorderStroke(1.dp, SpaceOutline)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("API Transaction Logs", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SteelPrimary)
                    Spacer(modifier = Modifier.height(6.dp))

                    if (logs.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No request packets captured", color = Color.Gray, fontSize = 11.sp)
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(logs) { log ->
                                ApiLogListItem(log, isSelected = selLog?.id == log.id) {
                                    viewModel.selectedLog.value = log
                                }
                            }
                        }
                    }
                }
            }

            // Right Inspector Panel: Request / Response details
            Card(
                modifier = Modifier
                    .weight(1.3f)
                    .fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = SpaceDarkBG),
                border = BorderStroke(1.dp, SpaceOutline)
            ) {
                if (selLog == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Select a REST transaction log packet to inspect details", color = Color.Gray, fontSize = 10.sp, textAlign = TextAlign.Center)
                    }
                } else {
                    InspectorLogDetails(selLog!!, viewModel)
                }
            }
        }

        // Quick Execute Shortcuts Bar for APIs
        Card(
            colors = CardDefaults.cardColors(containerColor = SpaceSurface),
            border = BorderStroke(1.dp, SpaceOutline)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text("Direct REST API Endpoints Runner", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SteelSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = { viewModel.apiCreateSession("Central Station Store, London", "DEV-KIOSK-09", "SILVER") },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SpaceOutline),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("POST /session/create", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = SteelPrimary)
                    }

                    Button(
                        onClick = { viewModel.apiGetSession() },
                        enabled = sessionId != null,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SpaceOutline),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("GET /session/{id}", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = SteelPrimary)
                    }

                    Button(
                        onClick = { viewModel.apiScanProduct("5010203040504", 1) }, // Scan Dark Choc
                        enabled = sessionId != null,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SpaceOutline),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("POST /scan?barcode=504 (Choc)", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = SteelPrimary)
                    }

                    Button(
                        onClick = { viewModel.apiGetTotal() },
                        enabled = sessionId != null,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SpaceOutline),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("GET /total", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = SteelPrimary)
                    }

                    Button(
                        onClick = { viewModel.apiPaySession("MOBILE_PAY") },
                        enabled = sessionId != null,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SpaceOutline),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("POST /pay", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = SteelPrimary)
                    }
                }
            }
        }
    }
}

@Composable
fun ApiLogListItem(log: ApiLog, isSelected: Boolean, onClick: () -> Unit) {
    val mColor = when (log.method) {
        "POST" -> Color(0xFF1E88E5)
        "GET" -> Color(0xFF43A047)
        "DELETE" -> Color(0xFFD32F2F)
        else -> Color.Gray
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) SpaceOutline else Color.Transparent)
            .border(
                1.dp, 
                if (isSelected) SteelPrimary else SpaceOutline.copy(alpha = 0.5f), 
                RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(mColor.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = log.method,
                        color = mColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    text = log.endpoint,
                    fontSize = 11.sp,
                    color = SteelSecondary,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                val codeColor = if (log.responseCode in 200..299) SignalSuccess else SignalFailure
                Text(
                    text = "${log.responseCode}",
                    color = codeColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "${log.latencyMs}ms",
                    color = Color.Gray,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun InspectorLogDetails(log: ApiLog, viewModel: CheckoutViewModel) {
    val clipboard = LocalClipboardManager.current
    var activeViewerTab by remember { mutableStateOf("response") } // "request", "response"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${log.method} ${log.endpoint}",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = SteelPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(
                onClick = {
                    val codeToCopy = if (activeViewerTab == "response") log.responseBody else log.requestBody
                    clipboard.setText(AnnotatedString(codeToCopy))
                    viewModel.uiFeedbackMessage.value = "JSON payload copied to clipboard!"
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.Share, "copy payload", tint = Color.LightGray, modifier = Modifier.size(14.dp))
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(SpaceDarkBG) // Better contrast on white panel
                .padding(2.dp)
        ) {
            Button(
                onClick = { activeViewerTab = "request" },
                modifier = Modifier
                    .weight(1f)
                    .height(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeViewerTab == "request") SpaceOutline else Color.Transparent,
                    contentColor = if (activeViewerTab == "request") SteelSecondary else Color.Gray
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("Request Payload JSON", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { activeViewerTab = "response" },
                modifier = Modifier
                    .weight(1f)
                    .height(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeViewerTab == "response") SpaceOutline else Color.Transparent,
                    contentColor = if (activeViewerTab == "response") SteelSecondary else Color.Gray
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("Response Outcome JSON", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Show payload in monospace log viewer component
        val jsonToShow = if (activeViewerTab == "response") log.responseBody else log.requestBody

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF020617))
                .border(1.dp, SpaceOutline, RoundedCornerShape(6.dp))
                .verticalScroll(rememberScrollState())
                .padding(8.dp)
        ) {
            Text(
                text = if (jsonToShow.isEmpty()) "[No payload parameters passed]" else jsonToShow,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = if (jsonToShow.contains("error")) SignalFailure else Color(0xFFCE93D8)
            )
        }

        Text(
            text = "Packet Trace Captured: ${log.timestamp} | Latency: ${log.latencyMs}ms",
            fontSize = 9.sp,
            color = Color.Gray,
            fontFamily = FontFamily.Monospace
        )
    }
}

// =========================================================
// ⚙️ WAREHOUSE PANEL (CENTRAL CATALOGS & DYNAMIC PROMO CONFIGS)
// =========================================================

@Composable
fun WarehousePanel(viewModel: CheckoutViewModel) {
    val productsCatalog by viewModel.products.collectAsState()
    val promoRulesCatalog by viewModel.promoRules.collectAsState()

    var showAddProductDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("warehouse_panel_container"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Central Warehouse Repository Editor", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SteelSecondary)
                Text("Manage pricing catalogues, inventory levels and dynamic promotion rules", fontSize = 10.sp, color = Color.Gray)
            }
            Button(
                onClick = { showAddProductDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = SteelPrimary),
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                Icon(Icons.Default.Add, "add product", tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Register SKU", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Left inventory items list
            Card(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = SpaceSurface),
                border = BorderStroke(1.dp, SpaceOutline)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("Inventory Core (Active SQLite Table)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SteelPrimary)
                    Spacer(modifier = Modifier.height(6.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(productsCatalog) { prod ->
                            WarehouseProductRow(prod, viewModel)
                        }
                    }
                }
            }

            // Right active promo regulations rules
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = SpaceSurface),
                border = BorderStroke(1.dp, SpaceOutline)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Promotions & Loyalty Rates", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SteelPrimary)
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(promoRulesCatalog) { rule ->
                            WarehousePromoRuleRow(rule, viewModel)
                        }
                    }
                }
            }
        }
    }

    if (showAddProductDialog) {
        AddProductDialog(
            onDismiss = { showAddProductDialog = false },
            onConfirm = { barcode, sku, name, price, tax, category, stock ->
                viewModel.addNewProductToDb(barcode, sku, name, price, tax, category, stock, "store")
                showAddProductDialog = false
            }
        )
    }
}

@Composable
fun WarehouseProductRow(product: ProductEntity, viewModel: CheckoutViewModel) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(SpaceDarkBG)
            .border(1.dp, SpaceOutline, RoundedCornerShape(6.dp))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(product.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SteelSecondary)
                Text(
                    text = "UPC: ${product.barcode} | Category: ${product.category}",
                    fontSize = 9.sp,
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Base Price: £${DecimalFormat("0.00").format(product.basePrice)}", fontSize = 10.sp, color = SteelPrimary, fontFamily = FontFamily.Monospace)
                    Text("VAT Rate: ${(product.taxRate * 100).toInt()}%", fontSize = 10.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (product.stockQuantity > 10) SteelSecondary.copy(alpha = 0.2f) else SignalFailure.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Stock: ${product.stockQuantity}",
                        color = if (product.stockQuantity > 10) SteelPrimary else SignalFailure,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                // Quick Restock trigger
                Button(
                    onClick = { viewModel.addNewProductToDb(product.barcode, product.sku, product.name, product.basePrice, product.taxRate, product.category, product.stockQuantity + 50, product.iconName) },
                    colors = ButtonDefaults.buttonColors(containerColor = SpaceOutline),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Text("Restock +50", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = SteelPrimary)
                }
            }
        }
    }
}

@Composable
fun WarehousePromoRuleRow(rule: PromoRuleEntity, viewModel: CheckoutViewModel) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(SpaceDarkBG)
            .border(1.dp, SpaceOutline, RoundedCornerShape(6.dp))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(rule.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SteelSecondary)
                Text("Type: ${rule.ruleType}", fontSize = 9.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                if (rule.targetBarcode != null) {
                    Text("Targets Barcode: ${rule.targetBarcode}", fontSize = 9.sp, color = SteelPrimary, fontFamily = FontFamily.Monospace)
                } else if (rule.minimumBasketAmount > 0) {
                    Text("Min Basket Spend: £${rule.minimumBasketAmount}", fontSize = 9.sp, color = SteelPrimary, fontFamily = FontFamily.Monospace)
                }
            }
            IconButton(
                onClick = { viewModel.deletePromoRule(rule) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Delete, "Delete promo rule", tint = SignalFailure, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// Dialogs

@Composable
fun AddProductDialog(
    onDismiss: () -> Unit,
    onConfirm: (barcode: String, sku: String, name: String, price: Double, tax: Double, category: String, stock: Int) -> Unit
) {
    var barcode by remember { mutableStateOf("") }
    var sku by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var priceStr by remember { mutableStateOf("") }
    var taxStr by remember { mutableStateOf("20") } // default 20%
    var category by remember { mutableStateOf("Fresh Produce") }
    var stockStr by remember { mutableStateOf("100") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Register New Product SKU") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Product Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = barcode,
                    onValueChange = { barcode = it },
                    label = { Text("UPC / Barcode (e.g. 5010203040508)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sku,
                    onValueChange = { sku = it },
                    label = { Text("Central ERP SKU Code (e.g. PRD-BREAD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                    value = priceStr,
                    onValueChange = { priceStr = it },
                    label = { Text("Price (£)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = taxStr,
                    onValueChange = { taxStr = it },
                    label = { Text("VAT Tax (%)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category Group") },
                    singleLine = true,
                    modifier = Modifier.weight(1.2f)
                )
                OutlinedTextField(
                    value = stockStr,
                    onValueChange = { stockStr = it },
                    label = { Text("Initial Stock") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val price = priceStr.toDoubleOrNull() ?: 0.0
                    val tax = (taxStr.toDoubleOrNull() ?: 20.0) / 100.0
                    val stock = stockStr.toIntOrNull() ?: 100
                    if (barcode.isNotEmpty() && name.isNotEmpty()) {
                        onConfirm(barcode, sku, name, price, tax, category, stock)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = SteelPrimary)
            ) {
                Text("Register RFID & Barcode", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
