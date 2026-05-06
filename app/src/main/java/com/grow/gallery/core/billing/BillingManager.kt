package com.grow.gallery.core.billing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class PremiumPlan(
    val id: String,
    val name: String,
    val price: String,
    val period: String,
    val isBestValue: Boolean = false,
)

enum class PurchaseState { IDLE, LOADING, SUCCESS, ERROR }

@Singleton
class BillingManager @Inject constructor() {
    private val _isPremium = MutableStateFlow(false)
    val isPremium: Flow<Boolean> = _isPremium.asStateFlow()

    private val _purchaseState = MutableStateFlow(PurchaseState.IDLE)
    val purchaseState: Flow<PurchaseState> = _purchaseState.asStateFlow()

    // Clean abstraction — real Google Play Billing would be wired here.
    // Prices are placeholders; production uses ProductDetails.formattedPrice from Play Billing
    // which returns locale- and currency-correct strings.
    val availablePlans = listOf(
        PremiumPlan("yearly", "Yearly", "$29.99/year", "year", isBestValue = true),
        PremiumPlan("monthly", "Monthly", "$4.99/month", "month"),
        PremiumPlan("lifetime", "Lifetime", "$59.99", "once"),
    )

    suspend fun purchasePlan(planId: String): Result<Unit> {
        // TODO: Integrate Google Play Billing Library 7.x
        // 1. Connect BillingClient
        // 2. queryProductDetailsAsync for the planId
        // 3. launchBillingFlow (requires Activity reference — wire via ActivityResultContract)
        // 4. Handle PurchasesUpdatedListener
        // 5. Acknowledge purchase on server
        _purchaseState.value = PurchaseState.LOADING
        return Result.failure(UnsupportedOperationException("Billing not connected"))
    }

    suspend fun restorePurchases(): Result<Unit> {
        // TODO: queryPurchasesAsync and verify on server
        return Result.failure(UnsupportedOperationException("Billing not connected"))
    }

    // Internal — only callable within this module (e.g. from billing callbacks after purchase verification).
    // Not exposed publicly to prevent monetization bypass.
    internal fun grantPremium() {
        _isPremium.value = true
    }
}
