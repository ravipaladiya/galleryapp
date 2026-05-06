package com.grow.gallery.core.billing

import kotlinx.coroutines.delay
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

    val availablePlans = listOf(
        PremiumPlan("yearly", "Yearly", "$29.99/year", "year", isBestValue = true),
        PremiumPlan("monthly", "Monthly", "$4.99/month", "month"),
        PremiumPlan("lifetime", "Lifetime", "$59.99", "once"),
    )

    suspend fun purchasePlan(planId: String): Result<Unit> {
        // Google Play Billing Library 7.x integration:
        // 1. startConnection → BillingClientStateListener
        // 2. queryProductDetailsAsync for the planId SKU
        // 3. launchBillingFlow with BillingFlowParams
        // 4. PurchasesUpdatedListener → verify + acknowledge on server
        // 5. On server confirmation → call grantPremium()
        _purchaseState.value = PurchaseState.LOADING
        delay(500)
        _purchaseState.value = PurchaseState.ERROR
        return Result.failure(Exception("In-app purchases are coming soon. Stay tuned!"))
    }

    suspend fun restorePurchases(): Result<Unit> {
        // queryPurchasesAsync(QueryPurchasesParams) then verify each token on server
        delay(300)
        return Result.failure(Exception("No purchases found to restore."))
    }

    // Only called after server-side purchase verification — not exposed as public API
    internal fun grantPremium() {
        _isPremium.value = true
    }

    internal fun revokePremium() {
        _isPremium.value = false
    }

    fun resetPurchaseState() {
        _purchaseState.value = PurchaseState.IDLE
    }
}
