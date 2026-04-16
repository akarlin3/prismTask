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
    PRO
}

enum class BillingPeriod {
    MONTHLY,
    ANNUAL,
    NONE
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
        const val PRODUCT_ID_PRO_MONTHLY = "prismtask_pro_monthly"
        const val PRODUCT_ID_PRO_ANNUAL = "prismtask_pro_annual"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _userTier = MutableStateFlow(UserTier.FREE)
    val userTier: StateFlow<UserTier> = _userTier.asStateFlow()

    private val _billingPeriod = MutableStateFlow(BillingPeriod.NONE)
    val billingPeriod: StateFlow<BillingPeriod> = _billingPeriod.asStateFlow()

    private val _proSubscriptionState = MutableStateFlow(SubscriptionState.NOT_SUBSCRIBED)
    val proSubscriptionState: StateFlow<SubscriptionState> = _proSubscriptionState.asStateFlow()

    private val _debugTierOverride = MutableStateFlow<UserTier?>(null)
    val debugTierOverride: StateFlow<UserTier?> = _debugTierOverride.asStateFlow()

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private var realTier: UserTier = UserTier.FREE
    private var realPeriod: BillingPeriod = BillingPeriod.NONE
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
            val cachedPeriod = proStatusPreferences.getCachedBillingPeriod()
            val expiresAt = proStatusPreferences.tierExpiresAt()
            if (cachedTier != "FREE" && expiresAt > System.currentTimeMillis()) {
                val parsedTier = try {
                    UserTier.valueOf(cachedTier)
                } catch (_: IllegalArgumentException) {
                    UserTier.FREE
                }
                val parsedPeriod = try {
                    BillingPeriod.valueOf(cachedPeriod)
                } catch (_: IllegalArgumentException) {
                    BillingPeriod.NONE
                }
                realTier = parsedTier
                realPeriod = parsedPeriod
                realState = SubscriptionState.SUBSCRIBED
                if (_debugTierOverride.value == null) {
                    _userTier.value = parsedTier
                    _billingPeriod.value = parsedPeriod
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

    suspend fun launchPurchaseFlow(activity: Activity, period: BillingPeriod): Result<Unit> {
        if (!billingClient.isReady) {
            val connected = connectBillingClient()
            if (!connected) return Result.failure(Exception("Could not connect to Google Play"))
        }
        val productId = when (period) {
            BillingPeriod.MONTHLY -> PRODUCT_ID_PRO_MONTHLY
            BillingPeriod.ANNUAL -> PRODUCT_ID_PRO_ANNUAL
            BillingPeriod.NONE -> return Result.failure(Exception("Cannot purchase with no billing period"))
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
                updateTierStatus(UserTier.FREE, BillingPeriod.NONE, SubscriptionState.NOT_SUBSCRIBED)
            }
            return Result.success(Unit)
        }
        return Result.failure(Exception("Could not query purchases"))
    }

    suspend fun handlePurchaseUpdate(purchases: List<Purchase>) {
        var matchedTier = UserTier.FREE
        var matchedPeriod = BillingPeriod.NONE
        var hasActivePurchase = false
        for (purchase in purchases) {
            val period = when {
                purchase.products.contains(PRODUCT_ID_PRO_ANNUAL) -> BillingPeriod.ANNUAL
                purchase.products.contains(PRODUCT_ID_PRO_MONTHLY) -> BillingPeriod.MONTHLY
                else -> continue
            }
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    if (!purchase.isAcknowledged) {
                        acknowledgePurchase(purchase)
                    }
                    matchedTier = UserTier.PRO
                    // Prefer annual over monthly if both are somehow active
                    if (period == BillingPeriod.ANNUAL || matchedPeriod == BillingPeriod.NONE) {
                        matchedPeriod = period
                    }
                    hasActivePurchase = true
                }
                Purchase.PurchaseState.PENDING -> {}
                else -> {}
            }
        }
        if (hasActivePurchase) {
            updateTierStatus(matchedTier, matchedPeriod, SubscriptionState.SUBSCRIBED)
        } else {
            updateTierStatus(UserTier.FREE, BillingPeriod.NONE, SubscriptionState.EXPIRED)
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

    private suspend fun updateTierStatus(
        tier: UserTier,
        period: BillingPeriod,
        state: SubscriptionState
    ) {
        realTier = tier
        realPeriod = period
        realState = state
        if (_debugTierOverride.value == null) {
            applyEffectiveTier(tier, period, state)
        }
        proStatusPreferences.setCachedTier(tier.name)
        proStatusPreferences.setCachedBillingPeriod(period.name)
        if (tier != UserTier.FREE) {
            val renewalWindow = if (period == BillingPeriod.ANNUAL) {
                365L * 24 * 60 * 60 * 1000
            } else {
                30L * 24 * 60 * 60 * 1000
            }
            proStatusPreferences.setTierExpiresAt(System.currentTimeMillis() + renewalWindow)
        } else {
            proStatusPreferences.setTierExpiresAt(0)
        }
        proStatusPreferences.setLastVerifiedAt(System.currentTimeMillis())
    }

    /**
     * Apply the effective tier, taking admin status into account.
     * Admin users always get PRO tier.
     */
    private fun applyEffectiveTier(tier: UserTier, period: BillingPeriod, state: SubscriptionState) {
        if (_isAdmin.value) {
            _userTier.value = UserTier.PRO
            _billingPeriod.value = period
            _proSubscriptionState.value = SubscriptionState.SUBSCRIBED
        } else {
            _userTier.value = tier
            _billingPeriod.value = period
            _proSubscriptionState.value = state
        }
    }

    /**
     * Set admin status. When admin is true, the user automatically gets
     * PRO tier regardless of their billing status.
     */
    fun setAdminStatus(isAdmin: Boolean) {
        _isAdmin.value = isAdmin
        if (_debugTierOverride.value == null) {
            applyEffectiveTier(realTier, realPeriod, realState)
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
        applyEffectiveTier(realTier, realPeriod, realState)
    }
}
