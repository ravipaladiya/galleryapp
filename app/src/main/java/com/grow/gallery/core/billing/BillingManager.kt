package com.grow.gallery.core.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.grow.gallery.core.common.DataStoreManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class PremiumPlan(
    val id: String,
    val name: String,
    val price: String,
    val period: String,
    val isBestValue: Boolean = false,
    val productDetails: ProductDetails? = null,
)

enum class PurchaseState { IDLE, LOADING, SUCCESS, ERROR }

@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStoreManager: DataStoreManager,
) : PurchasesUpdatedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _isPremium = MutableStateFlow(false)
    val isPremium: Flow<Boolean> = _isPremium.asStateFlow()

    private val _purchaseState = MutableStateFlow(PurchaseState.IDLE)
    val purchaseState: Flow<PurchaseState> = _purchaseState.asStateFlow()

    private val _availablePlans = MutableStateFlow<List<PremiumPlan>>(PLACEHOLDER_PLANS)
    val availablePlans: StateFlow<List<PremiumPlan>> = _availablePlans.asStateFlow()

    private var billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    private val productIds = listOf("gallery_premium_yearly", "gallery_premium_monthly", "gallery_premium_lifetime")
    private val subsProductIds = listOf("gallery_premium_yearly", "gallery_premium_monthly")
    private val inappProductIds = listOf("gallery_premium_lifetime")

    init {
        scope.launch { restoreFromDataStore() }
        connect()
    }

    private fun connect() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    scope.launch {
                        queryProducts()
                        restorePurchases()
                    }
                }
            }
            override fun onBillingServiceDisconnected() {
                connect()
            }
        })
    }

    private suspend fun ensureConnected(): Boolean {
        if (billingClient.isReady) return true
        return suspendCancellableCoroutine { cont ->
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                }
                override fun onBillingServiceDisconnected() {
                    cont.resume(false)
                }
            })
        }
    }

    private suspend fun queryProducts() {
        val subsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(subsProductIds.map {
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(it)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            })
            .build()

        val inappParams = QueryProductDetailsParams.newBuilder()
            .setProductList(inappProductIds.map {
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(it)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            })
            .build()

        val subsResult = withContext(Dispatchers.IO) {
            billingClient.queryProductDetails(subsParams)
        }
        val inappResult = withContext(Dispatchers.IO) {
            billingClient.queryProductDetails(inappParams)
        }

        val allDetails = (subsResult.productDetailsList ?: emptyList()) +
                (inappResult.productDetailsList ?: emptyList())

        if (allDetails.isNotEmpty()) {
            _availablePlans.value = allDetails.map { pd ->
                val price = pd.subscriptionOfferDetails?.firstOrNull()
                    ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                    ?: pd.oneTimePurchaseOfferDetails?.formattedPrice
                    ?: "—"
                val period = when {
                    pd.productId.contains("yearly") -> "year"
                    pd.productId.contains("monthly") -> "month"
                    else -> "once"
                }
                PremiumPlan(
                    id = pd.productId,
                    name = pd.title.substringBefore(" ("),
                    price = price,
                    period = period,
                    isBestValue = pd.productId.contains("yearly"),
                    productDetails = pd,
                )
            }
        }
    }

    fun launchBillingFlow(activity: Activity, planId: String): Result<Unit> {
        val plan = _availablePlans.value.firstOrNull { it.id == planId }
            ?: return Result.failure(IllegalArgumentException("Plan not found: $planId"))
        val productDetails = plan.productDetails
            ?: return Result.failure(IllegalStateException("Product details not loaded"))

        _purchaseState.value = PurchaseState.LOADING

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .apply {
                            productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken?.let {
                                setOfferToken(it)
                            }
                        }
                        .build()
                )
            )
            .build()

        val result = billingClient.launchBillingFlow(activity, params)
        return if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            Result.success(Unit)
        } else {
            _purchaseState.value = PurchaseState.ERROR
            Result.failure(RuntimeException(result.debugMessage))
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    scope.launch { handlePurchase(purchase) }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _purchaseState.value = PurchaseState.IDLE
            }
            else -> {
                _purchaseState.value = PurchaseState.ERROR
            }
        }
    }

    private suspend fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        if (!purchase.isAcknowledged) {
            val ackParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            val ackResult = withContext(Dispatchers.IO) {
                billingClient.acknowledgePurchase(ackParams)
            }
            if (ackResult.responseCode != BillingClient.BillingResponseCode.OK) return
        }

        grantPremium()
        _purchaseState.value = PurchaseState.SUCCESS
    }

    suspend fun restorePurchases(): Result<Unit> {
        if (!ensureConnected()) return Result.failure(RuntimeException("Billing not connected"))

        val subsResult = withContext(Dispatchers.IO) {
            billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            )
        }
        val inappResult = withContext(Dispatchers.IO) {
            billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )
        }

        val activePurchases = (subsResult.purchasesList + inappResult.purchasesList)
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }

        if (activePurchases.isNotEmpty()) {
            activePurchases.forEach { handlePurchase(it) }
            return Result.success(Unit)
        }
        return Result.failure(RuntimeException("No active purchases found"))
    }

    private suspend fun restoreFromDataStore() {
        dataStoreManager.isPremiumCached.collect { cached ->
            if (cached) _isPremium.value = true
        }
    }

    internal fun grantPremium() {
        _isPremium.value = true
        scope.launch { dataStoreManager.setIsPremiumCached(true) }
    }

    companion object {
        private val PLACEHOLDER_PLANS = listOf(
            PremiumPlan("gallery_premium_yearly", "Yearly", "$29.99/year", "year", isBestValue = true),
            PremiumPlan("gallery_premium_monthly", "Monthly", "$4.99/month", "month"),
            PremiumPlan("gallery_premium_lifetime", "Lifetime", "$59.99", "once"),
        )
    }
}
