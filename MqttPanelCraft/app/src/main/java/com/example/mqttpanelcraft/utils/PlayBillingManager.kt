package com.example.mqttpanelcraft.utils

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.example.mqttpanelcraft.R

object PlayBillingManager : PurchasesUpdatedListener {
    private const val TAG = "PlayBilling"
    private val mainHandler = Handler(Looper.getMainLooper())

    private var appContext: Context? = null
    private var billingClient: BillingClient? = null
    private var productDetails: ProductDetails? = null
    private var purchaseCallback: ((Boolean) -> Unit)? = null

    fun initialize(context: Context) {
        val app = context.applicationContext
        appContext = app
        val existing = billingClient
        if (existing != null) {
            if (existing.isReady) {
                queryProductDetails()
                queryOwnedPurchases(null)
            }
            return
        }
        val client = BillingClient.newBuilder(app)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .enableAutoServiceReconnection()
            .build()
        billingClient = client
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProductDetails()
                    queryOwnedPurchases(null)
                } else {
                    Log.w(TAG, "setup ${result.responseCode} ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "disconnected")
            }
        })
    }

    fun refreshPurchases(context: Context, onDone: ((Boolean) -> Unit)?) {
        appContext = context.applicationContext
        val client = billingClient
        if (client == null) {
            initialize(context)
            onDone?.invoke(PremiumManager.hasPlayEntitlement(context))
            return
        }
        if (!client.isReady) {
            onDone?.invoke(PremiumManager.hasPlayEntitlement(context))
            return
        }
        queryOwnedPurchases(onDone)
    }

    fun launchPurchase(activity: Activity, callback: (Boolean) -> Unit) {
        purchaseCallback = callback
        val client = billingClient
        if (client == null || !client.isReady) {
            initialize(activity)
            toast(activity, R.string.premium_billing_unavailable)
            finishPurchase(false)
            return
        }
        val details = productDetails
        if (details == null) {
            queryProductDetails {
                val loaded = productDetails
                if (loaded == null) {
                    toast(activity, R.string.premium_product_unavailable)
                    finishPurchase(false)
                } else {
                    startFlow(activity, client, loaded)
                }
            }
            return
        }
        startFlow(activity, client, details)
    }

    fun restorePurchases(context: Context, callback: (Boolean) -> Unit) {
        refreshPurchases(context) { owned ->
            toast(
                context,
                if (owned) R.string.premium_already else R.string.premium_no_purchase
            )
            callback(owned)
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                processPurchases(purchases.orEmpty()) { owned, pending ->
                    when {
                        pending -> {
                            appContext?.let { toast(it, R.string.premium_purchase_pending) }
                            finishPurchase(false)
                        }
                        owned -> {
                            appContext?.let { toast(it, R.string.premium_unlocked) }
                            finishPurchase(true)
                        }
                        else -> finishPurchase(false)
                    }
                }
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                queryOwnedPurchases { owned ->
                    appContext?.let {
                        toast(it, if (owned) R.string.premium_already else R.string.premium_no_purchase)
                    }
                    finishPurchase(owned)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> finishPurchase(false)
            else -> {
                Log.w(TAG, "purchase ${result.responseCode} ${result.debugMessage}")
                finishPurchase(false)
            }
        }
    }

    private fun startFlow(activity: Activity, client: BillingClient, details: ProductDetails) {
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        val launched = client.launchBillingFlow(activity, flowParams)
        if (launched.responseCode == BillingClient.BillingResponseCode.OK) return
        if (launched.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            queryOwnedPurchases { owned ->
                toast(activity, if (owned) R.string.premium_already else R.string.premium_no_purchase)
                finishPurchase(owned)
            }
            return
        }
        toast(activity, R.string.premium_billing_unavailable)
        finishPurchase(false)
    }

    private fun queryProductDetails(onDone: (() -> Unit)? = null) {
        val client = billingClient ?: run {
            onDone?.invoke()
            return
        }
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PlayBillingConfig.PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()
        client.queryProductDetailsAsync(params) { result, detailsResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetails = detailsResult.productDetailsList.firstOrNull {
                    it.productId == PlayBillingConfig.PRODUCT_ID
                }
            } else {
                Log.w(TAG, "products ${result.responseCode} ${result.debugMessage}")
            }
            onDone?.invoke()
        }
    }

    private fun queryOwnedPurchases(onDone: ((Boolean) -> Unit)?) {
        val client = billingClient ?: run {
            onDone?.invoke(false)
            return
        }
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "owned ${result.responseCode} ${result.debugMessage}")
                onDone?.invoke(PremiumManager.hasPlayEntitlement(requireContext()))
                return@queryPurchasesAsync
            }
            processPurchases(purchases) { owned, pending ->
                if (pending) {
                    appContext?.let { toast(it, R.string.premium_purchase_pending) }
                }
                onDone?.invoke(owned)
            }
        }
    }

    private fun processPurchases(
        purchases: List<Purchase>,
        onDone: (owned: Boolean, pending: Boolean) -> Unit
    ) {
        val context = appContext
        var owned = false
        var pending = false
        purchases.forEach { purchase ->
            if (!purchase.products.contains(PlayBillingConfig.PRODUCT_ID)) return@forEach
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PENDING -> pending = true
                Purchase.PurchaseState.PURCHASED -> {
                    owned = true
                    acknowledgeIfNeeded(purchase)
                }
            }
        }
        if (context != null) {
            PremiumManager.applyPlayEntitlement(context, owned)
        }
        onDone(owned, pending && !owned)
    }

    private fun acknowledgeIfNeeded(purchase: Purchase, attempt: Int = 0) {
        if (purchase.isAcknowledged) return
        val client = billingClient ?: return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        client.acknowledgePurchase(params) { result ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) return@acknowledgePurchase
            Log.w(TAG, "ack ${result.responseCode} ${result.debugMessage}")
            if (attempt >= 3) return@acknowledgePurchase
            val delayMs = 2_000L * (attempt + 1)
            mainHandler.postDelayed({ acknowledgeIfNeeded(purchase, attempt + 1) }, delayMs)
        }
    }

    private fun finishPurchase(success: Boolean) {
        val callback = purchaseCallback
        purchaseCallback = null
        if (callback != null) {
            mainHandler.post { callback(success) }
        }
    }

    private fun requireContext(): Context =
        appContext ?: error("PlayBillingManager was not initialized")

    private fun toast(context: Context, messageRes: Int) {
        mainHandler.post {
            Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show()
        }
    }
}
