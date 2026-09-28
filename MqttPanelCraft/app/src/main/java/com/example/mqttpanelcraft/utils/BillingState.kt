package com.example.mqttpanelcraft.utils

enum class BillingState { OWNED, NOT_OWNED, PENDING, UNAVAILABLE, INVALID_RECEIPT }

/** Only a successful complete query may remove an existing entitlement. */
object BillingEntitlementPolicy {
    fun reduce(previous: Boolean, state: BillingState, completeQuery: Boolean): Boolean = when (state) {
        BillingState.OWNED -> true
        BillingState.NOT_OWNED, BillingState.PENDING -> if (completeQuery) false else previous
        BillingState.UNAVAILABLE, BillingState.INVALID_RECEIPT -> previous
    }
}
