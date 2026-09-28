package com.example.mqttpanelcraft.utils

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.widget.Toast
import androidx.work.*
import com.android.billingclient.api.*
import com.example.mqttpanelcraft.BuildConfig
import com.example.mqttpanelcraft.R
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** All mutable connection/purchase state belongs to the main thread. */
object PlayBillingManager : PurchasesUpdatedListener {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var app: Context
    private var client: BillingClient? = null
    private var connecting = false
    private var purchaseRevision = 0L
    private val waiters = mutableListOf<(BillingClient?) -> Unit>()
    private var purchaseCallback: ((Boolean) -> Unit)? = null
    private val connectionTimeout = Runnable { finishConnection(null) }

    data class Offer(val details: ProductDetails, val token: String?, val price: String)

    fun initialize(context: Context) {
        app = context.applicationContext
        main.post { connect { if (it != null) queryOwned(null) } }
    }

    private fun connect(done: (BillingClient?) -> Unit) {
        val current = client
        if (current?.isReady == true) { done(current); return }
        waiters.add(done)
        if (connecting) return
        connecting = true
        // A new client after a failed connection avoids an indefinitely disconnected instance.
        current?.endConnection()
        val created = BillingClient.newBuilder(app).setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection().build()
        client = created
        main.postDelayed(connectionTimeout, 20_000L)
        created.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                main.post {
                    if (client !== created || !connecting) return@post
                    finishConnection(created.takeIf { result.responseCode == BillingClient.BillingResponseCode.OK })
                }
            }
            override fun onBillingServiceDisconnected() = Unit
        })
    }

    private fun finishConnection(ready: BillingClient?) {
        main.removeCallbacks(connectionTimeout)
        connecting = false
        if (ready == null) { client?.endConnection(); client = null }
        val callbacks = waiters.toList()
        waiters.clear()
        callbacks.forEach { it(ready) }
    }

    fun refreshPurchases(context: Context, onDone: ((Boolean) -> Unit)?) {
        refreshState(context) { onDone?.invoke(PremiumManager.hasPlayEntitlement(context)) }
    }

    fun refreshState(context: Context, done: (BillingState) -> Unit) {
        app = context.applicationContext
        main.post { connect { if (it == null) done(BillingState.UNAVAILABLE) else queryOwned(done) } }
    }

    fun loadOffer(context: Context, done: (Offer?) -> Unit) {
        app = context.applicationContext
        main.post {
            if (BuildConfig.PLAY_BILLING_PUBLIC_KEY.isBlank()) { done(null); return@post }
            connect { billing ->
                if (billing == null) { done(null); return@connect }
                val product = QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PlayBillingConfig.PRODUCT_ID).setProductType(BillingClient.ProductType.INAPP).build()
                billing.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()) { result, data ->
                    main.post {
                        val details = data.productDetailsList.firstOrNull { it.productId == PlayBillingConfig.PRODUCT_ID }
                        val options = details?.oneTimePurchaseOfferDetailsList
                        val selected = options?.firstOrNull()
                        val legacy = if (options.isNullOrEmpty()) details?.oneTimePurchaseOfferDetails else null
                        val price = selected?.formattedPrice ?: legacy?.formattedPrice
                        done(if (result.responseCode == BillingClient.BillingResponseCode.OK && details != null && price != null)
                            Offer(details, selected?.offerToken, price) else null)
                    }
                }
            }
        }
    }

    /** Offer is the freshly displayed offer, never a process-global stale ProductDetails cache. */
    fun launchPurchase(activity: Activity, offer: Offer, callback: (Boolean) -> Unit) {
        main.post {
            if (AdManager.isSuppressed(activity) || purchaseCallback != null || activity.isFinishing || activity.isDestroyed) { callback(false); return@post }
            purchaseCallback = callback
            connect { billing ->
                if (AdManager.isSuppressed(activity)) { finishPurchase(false); return@connect }
                if (billing == null || activity.isFinishing || activity.isDestroyed) {
                    toast(R.string.premium_billing_unavailable); finishPurchase(false); return@connect
                }
                val product = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(offer.details)
                offer.token?.let { product.setOfferToken(it) }
                val result = billing.launchBillingFlow(activity,
                    BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(product.build())).build())
                when (result.responseCode) {
                    BillingClient.BillingResponseCode.OK -> Unit
                    BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> queryOwned { finishPurchase(it == BillingState.OWNED) }
                    else -> { toast(R.string.premium_billing_unavailable); finishPurchase(false) }
                }
            }
        }
    }

    fun restorePurchases(context: Context, callback: (Boolean) -> Unit) {
        refreshState(context) { state ->
            toast(when (state) {
                BillingState.OWNED -> R.string.premium_already
                BillingState.NOT_OWNED -> R.string.premium_no_purchase
                BillingState.PENDING -> R.string.premium_purchase_pending
                else -> R.string.premium_billing_unavailable
            })
            callback(state == BillingState.OWNED)
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        main.post {
            when (result.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    purchaseRevision++
                    val state = process(purchases.orEmpty(), completeQuery = false)
                    when (state) {
                        BillingState.OWNED -> toast(R.string.premium_unlocked)
                        BillingState.PENDING -> toast(R.string.premium_purchase_pending)
                        BillingState.INVALID_RECEIPT -> toast(R.string.premium_billing_unavailable)
                        else -> Unit
                    }
                    finishPurchase(state == BillingState.OWNED)
                }
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> queryOwned { finishPurchase(it == BillingState.OWNED) }
                BillingClient.BillingResponseCode.USER_CANCELED -> finishPurchase(false)
                else -> { toast(R.string.premium_billing_unavailable); finishPurchase(false) }
            }
        }
    }

    private fun queryOwned(done: ((BillingState) -> Unit)?) {
        val billing = client ?: run { done?.invoke(BillingState.UNAVAILABLE); return }
        val revision = purchaseRevision
        billing.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { result, purchases ->
            main.post {
                val state = if (result.responseCode == BillingClient.BillingResponseCode.OK && revision == purchaseRevision)
                    process(purchases, completeQuery = true) else BillingState.UNAVAILABLE
                done?.invoke(state)
            }
        }
    }

    private fun process(purchases: List<Purchase>, completeQuery: Boolean): BillingState {
        val relevant = purchases.filter { PlayBillingConfig.PRODUCT_ID in it.products }
        val owned = relevant.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        val verified = owned.filter { verify(it) }
        val state = when {
            owned.size != verified.size -> BillingState.INVALID_RECEIPT
            verified.isNotEmpty() -> BillingState.OWNED
            relevant.any { it.purchaseState == Purchase.PurchaseState.PENDING } -> BillingState.PENDING
            else -> BillingState.NOT_OWNED
        }
        val previous = PremiumManager.hasPlayEntitlement(app)
        PremiumManager.applyPlayEntitlement(app, BillingEntitlementPolicy.reduce(previous, state, completeQuery))
        verified.filterNot { it.isAcknowledged }.forEach { enqueueAcknowledgement(it.purchaseToken) }
        return state
    }

    internal fun verify(purchase: Purchase): Boolean = try {
        if (purchase.packageName != app.packageName || PlayBillingConfig.PRODUCT_ID !in purchase.products ||
            purchase.purchaseState != Purchase.PurchaseState.PURCHASED ||
            BuildConfig.PLAY_BILLING_PUBLIC_KEY.isBlank()) false
        else {
            PurchaseSignatureVerifier.verify(
                Base64.decode(BuildConfig.PLAY_BILLING_PUBLIC_KEY, Base64.DEFAULT),
                purchase.originalJson, Base64.decode(purchase.signature, Base64.DEFAULT))
        }
    } catch (_: Exception) { false }

    private fun enqueueAcknowledgement(token: String) {
        val hash = MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
        val work = OneTimeWorkRequestBuilder<PurchaseAcknowledgementWorker>()
            .setInputData(workDataOf("token" to token))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(app).enqueueUniqueWork("play-ack-$hash", ExistingWorkPolicy.KEEP, work)
    }

    /** Re-query and re-verify; never acknowledge an arbitrary persisted token without ownership. */
    internal fun acknowledge(context: Context, token: String, done: (Boolean) -> Unit) {
        app = context.applicationContext
        main.post {
            connect { billing ->
                if (billing == null) { done(false); return@connect }
                billing.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { result, purchases ->
                    main.post {
                        if (result.responseCode != BillingClient.BillingResponseCode.OK) { done(false); return@post }
                        val purchase = purchases.firstOrNull { it.purchaseToken == token }
                        if (purchase == null || purchase.isAcknowledged) { done(true); return@post }
                        if (!verify(purchase)) { done(false); return@post }
                        billing.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()) {
                            resultAck -> done(resultAck.responseCode == BillingClient.BillingResponseCode.OK)
                        }
                    }
                }
            }
        }
    }

    private fun finishPurchase(success: Boolean) {
        val callback = purchaseCallback
        purchaseCallback = null
        callback?.invoke(success)
    }

    private fun toast(message: Int) { Toast.makeText(app, message, Toast.LENGTH_SHORT).show() }
}
