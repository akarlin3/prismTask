package com.averycorp.averytask.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetailsResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.averycorp.averytask.data.preferences.ProStatusPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

enum class SubscriptionState {
    NOT_SUBSCRIBED,
    SUBSCRIBED,
    GRACE_PERIOD,
    PAUSED,
    EXPIRED
}

@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val proStatusPreferences: ProStatusPreferences
) {
    companion object {
        const val PRODUCT_ID = "averytask_pro_monthly"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _isProUser = MutableStateFlow(false)
    val isProUser: StateFlow<Boolean> = _isProUser.asStateFlow()

    private val _proSubscriptionState = MutableStateFlow(SubscriptionState.NOT_SUBSCRIBED)
    val proSubscriptionState: StateFlow<SubscriptionState> = _proSubscriptionState.asStateFlow()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            scope.launch { handlePurchaseUpdate(purchases) }
        }
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    fun initialize(activity: Activity) {
        scope.launch {
            // Load cached Pro status immediately for offline access
            val cached = proStatusPreferences.isProCached()
            val expiresAt = proStatusPreferences.proExpiresAt()
            if (cached && expiresAt > System.currentTimeMillis()) {
                _isProUser.value = true
                _proSubscriptionState.value = SubscriptionState.SUBSCRIBED
            }

            // Connect to Google Play and verify
            connectAndVerify()
        }
    }

    private suspend fun connectAndVerify() {
        val connected = connectBillingClient()
        if (connected) {
            restorePurchases()
        }
    }

    private suspend fun connectBillingClient(): Boolean = suspendCancellableCoroutine { cont ->
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (cont.isActive) {
                    cont.resume(billingResult.responseCode == BillingClient.BillingResponseCode.OK)
                }
            }

            override fun onBillingServiceDisconnected() {
                // Will be retried on next interaction
            }
        })
    }

    suspend fun launchPurchaseFlow(activity: Activity): Result<Unit> {
        if (!billingClient.isReady) {
            val connected = connectBillingClient()
            if (!connected) return Result.failure(Exception("Could not connect to Google Play"))
        }

        val productDetails = queryProductDetails()
            ?: return Result.failure(Exception("Could not load subscription details"))

        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
            ?: return Result.failure(Exception("No subscription offer available"))

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        val result = billingClient.launchBillingFlow(activity, billingFlowParams)
        return if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("Purchase flow failed: ${result.debugMessage}"))
        }
    }

    suspend fun restorePurchases(): Result<Unit> {
        if (!billingClient.isReady) {
            val connected = connectBillingClient()
            if (!connected) return Result.failure(Exception("Could not connect to Google Play"))
        }

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        val purchasesResult = billingClient.queryPurchasesAsync(params)

        if (purchasesResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            val activePurchases = purchasesResult.purchasesList
            if (activePurchases.isNotEmpty()) {
                handlePurchaseUpdate(activePurchases)
            } else {
                updateProStatus(false, SubscriptionState.NOT_SUBSCRIBED)
            }
            return Result.success(Unit)
        }

        return Result.failure(Exception("Could not query purchases"))
    }

    suspend fun handlePurchaseUpdate(purchases: List<Purchase>) {
        for (purchase in purchases) {
            if (purchase.products.contains(PRODUCT_ID)) {
                when (purchase.purchaseState) {
                    Purchase.PurchaseState.PURCHASED -> {
                        if (!purchase.isAcknowledged) {
                            acknowledgePurchase(purchase)
                        }
                        updateProStatus(true, SubscriptionState.SUBSCRIBED)
                    }
                    Purchase.PurchaseState.PENDING -> {
                        // Pending purchase — don't grant Pro yet
                    }
                    else -> {
                        updateProStatus(false, SubscriptionState.EXPIRED)
                    }
                }
            }
        }
    }

    suspend fun checkSubscriptionStatus(): SubscriptionState {
        restorePurchases()
        return _proSubscriptionState.value
    }

    private suspend fun acknowledgePurchase(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.acknowledgePurchase(params)
    }

    private suspend fun queryProductDetails(): com.android.billingclient.api.ProductDetails? {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        val result: ProductDetailsResult = billingClient.queryProductDetails(params)

        return if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            result.productDetailsList?.firstOrNull()
        } else {
            null
        }
    }

    private suspend fun updateProStatus(isPro: Boolean, state: SubscriptionState) {
        _isProUser.value = isPro
        _proSubscriptionState.value = state
        proStatusPreferences.setProCached(isPro)
        if (isPro) {
            // Cache for 30 days for offline access
            proStatusPreferences.setProExpiresAt(
                System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
            )
        } else {
            proStatusPreferences.setProExpiresAt(0)
        }
        proStatusPreferences.setLastVerifiedAt(System.currentTimeMillis())
    }
}
