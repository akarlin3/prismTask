package com.averycorp.prismtask.data.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResponseListener
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResponseListener
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.averycorp.prismtask.BuildConfig
import com.averycorp.prismtask.data.preferences.ProStatusPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

enum class UserTier {
    FREE,
    PRO,
    PREMIUM,
    ULTRA
}

enum class SubscriptionState {
    NOT_SUBSCRIBED,
    SUBSCRIBED,
    GRACE_PERIOD,
    PAUSED,
    EXPIRED
}

@Singleton
class BillingManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val proStatusPreferences: ProStatusPreferences
) {
    companion object {
        const val PRODUCT_ID_PRO = "prismtask_pro_monthly"
        const val PRODUCT_ID_PREMIUM = "prismtask_premium_monthly"
        const val PRODUCT_ID_ULTRA = "prismtask_ultra_monthly"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _userTier = MutableStateFlow(UserTier.FREE)
    val userTier: StateFlow<UserTier> = _userTier.asStateFlow()

    private val _proSubscriptionState = MutableStateFlow(SubscriptionState.NOT_SUBSCRIBED)
    val proSubscriptionState: StateFlow<SubscriptionState> = _proSubscriptionState.asStateFlow()

    private val _debugTierOverride = MutableStateFlow<UserTier?>(null)
    val debugTierOverride: StateFlow<UserTier?> = _debugTierOverride.asStateFlow()

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private var realTier: UserTier = UserTier.FREE
    private var realState: SubscriptionState = SubscriptionState.NOT_SUBSCRIBED

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            scope.launch {
                try {
                    handlePurchaseUpdate(purchases)
                } catch (
                    e: Exception
                ) {
                    Log.e("BillingManager", "Failed to handle purchase update", e)
                }
            }
        }
    }

    private val billingClient: BillingClient = BillingClient
        .newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        ).build()

    fun initialize(activity: Activity) {
        scope.launch {
            val cachedTier = proStatusPreferences.getCachedTier()
            val expiresAt = proStatusPreferences.tierExpiresAt()
            if (cachedTier != "FREE" && expiresAt > System.currentTimeMillis()) {
                val parsed = try {
                    UserTier.valueOf(cachedTier)
                } catch (_: IllegalArgumentException) {
                    UserTier.FREE
                }
                realTier = parsed
                realState = SubscriptionState.SUBSCRIBED
                if (_debugTierOverride.value == null) {
                    _userTier.value = parsed
                    _proSubscriptionState.value = SubscriptionState.SUBSCRIBED
                }
            }
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

            override fun onBillingServiceDisconnected() {}
        })
    }

    suspend fun launchPurchaseFlow(activity: Activity, tier: UserTier): Result<Unit> {
        if (!billingClient.isReady) {
            val connected = connectBillingClient()
            if (!connected) return Result.failure(Exception("Could not connect to Google Play"))
        }
        val productId = when (tier) {
            UserTier.PRO -> PRODUCT_ID_PRO
            UserTier.PREMIUM -> PRODUCT_ID_PREMIUM
            UserTier.ULTRA -> PRODUCT_ID_ULTRA
            UserTier.FREE -> return Result.failure(Exception("Cannot purchase Free tier"))
        }
        val productDetails = queryProductDetails(productId)
            ?: return Result.failure(Exception("Could not load subscription details"))
        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
            ?: return Result.failure(Exception("No subscription offer available"))
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams
                .newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()
        )
        val billingFlowParams = BillingFlowParams
            .newBuilder()
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
        val params = QueryPurchasesParams
            .newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val (billingResult, purchasesList) = suspendCancellableCoroutine { cont ->
            billingClient.queryPurchasesAsync(
                params,
                PurchasesResponseListener { result, purchases ->
                    cont.resume(Pair(result, purchases))
                }
            )
        }
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            if (purchasesList.isNotEmpty()) {
                handlePurchaseUpdate(purchasesList)
            } else {
                updateTierStatus(UserTier.FREE, SubscriptionState.NOT_SUBSCRIBED)
            }
            return Result.success(Unit)
        }
        return Result.failure(Exception("Could not query purchases"))
    }

    suspend fun handlePurchaseUpdate(purchases: List<Purchase>) {
        var highestTier = UserTier.FREE
        var hasActivePurchase = false
        for (purchase in purchases) {
            val tier = when {
                purchase.products.contains(PRODUCT_ID_ULTRA) -> UserTier.ULTRA
                purchase.products.contains(PRODUCT_ID_PREMIUM) -> UserTier.PREMIUM
                purchase.products.contains(PRODUCT_ID_PRO) -> UserTier.PRO
                else -> continue
            }
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    if (!purchase.isAcknowledged) {
                        acknowledgePurchase(purchase)
                    }
                    if (tier > highestTier) {
                        highestTier = tier
                    }
                    hasActivePurchase = true
                }
                Purchase.PurchaseState.PENDING -> {}
                else -> {}
            }
        }
        if (hasActivePurchase) {
            updateTierStatus(highestTier, SubscriptionState.SUBSCRIBED)
        } else {
            updateTierStatus(UserTier.FREE, SubscriptionState.EXPIRED)
        }
    }

    suspend fun checkSubscriptionStatus(): SubscriptionState {
        restorePurchases()
        return _proSubscriptionState.value
    }

    private suspend fun acknowledgePurchase(purchase: Purchase) {
        val params = AcknowledgePurchaseParams
            .newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        suspendCancellableCoroutine { cont ->
            billingClient.acknowledgePurchase(params) { billingResult ->
                cont.resume(billingResult)
            }
        }
    }

    private suspend fun queryProductDetails(productId: String): ProductDetails? {
        val productList = listOf(
            QueryProductDetailsParams.Product
                .newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )
        val params = QueryProductDetailsParams
            .newBuilder()
            .setProductList(productList)
            .build()
        val (billingResult, productDetailsList) = suspendCancellableCoroutine { cont ->
            billingClient.queryProductDetailsAsync(
                params,
                ProductDetailsResponseListener { result, detailsList ->
                    cont.resume(Pair(result, detailsList))
                }
            )
        }
        return if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            productDetailsList.firstOrNull()
        } else {
            null
        }
    }

    private suspend fun updateTierStatus(tier: UserTier, state: SubscriptionState) {
        realTier = tier
        realState = state
        if (_debugTierOverride.value == null) {
            applyEffectiveTier(tier, state)
        }
        proStatusPreferences.setCachedTier(tier.name)
        if (tier != UserTier.FREE) {
            proStatusPreferences.setTierExpiresAt(
                System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
            )
        } else {
            proStatusPreferences.setTierExpiresAt(0)
        }
        proStatusPreferences.setLastVerifiedAt(System.currentTimeMillis())
    }

    /**
     * Apply the effective tier, taking admin status into account.
     * Admin users always get ULTRA tier.
     */
    private fun applyEffectiveTier(tier: UserTier, state: SubscriptionState) {
        if (_isAdmin.value) {
            _userTier.value = UserTier.ULTRA
            _proSubscriptionState.value = SubscriptionState.SUBSCRIBED
        } else {
            _userTier.value = tier
            _proSubscriptionState.value = state
        }
    }

    /**
     * Set admin status. When admin is true, the user automatically gets
     * the highest tier (ULTRA) regardless of their billing status.
     */
    fun setAdminStatus(isAdmin: Boolean) {
        _isAdmin.value = isAdmin
        if (_debugTierOverride.value == null) {
            applyEffectiveTier(realTier, realState)
        }
    }

    fun setDebugTier(tier: UserTier) {
        if (!BuildConfig.DEBUG && !_isAdmin.value) return
        _debugTierOverride.value = tier
        _userTier.value = tier
        _proSubscriptionState.value = if (tier == UserTier.FREE) {
            SubscriptionState.NOT_SUBSCRIBED
        } else {
            SubscriptionState.SUBSCRIBED
        }
    }

    fun clearDebugTier() {
        if (!BuildConfig.DEBUG && !_isAdmin.value) return
        _debugTierOverride.value = null
        applyEffectiveTier(realTier, realState)
    }
}
