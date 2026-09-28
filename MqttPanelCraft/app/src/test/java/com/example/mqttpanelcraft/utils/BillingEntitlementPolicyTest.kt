package com.example.mqttpanelcraft.utils

import org.junit.Assert.*
import org.junit.Test

class BillingEntitlementPolicyTest {
    @Test fun incrementalEmptyDoesNotRevoke() {
        assertTrue(BillingEntitlementPolicy.reduce(true, BillingState.NOT_OWNED, false))
        assertTrue(BillingEntitlementPolicy.reduce(true, BillingState.PENDING, false))
    }
    @Test fun authoritativeEmptyRevokes() {
        assertFalse(BillingEntitlementPolicy.reduce(true, BillingState.NOT_OWNED, true))
    }
    @Test fun failuresPreserveAndPendingDoesNotGrant() {
        for (complete in listOf(true, false)) {
            assertTrue(BillingEntitlementPolicy.reduce(true, BillingState.UNAVAILABLE, complete))
            assertTrue(BillingEntitlementPolicy.reduce(true, BillingState.INVALID_RECEIPT, complete))
            assertFalse(BillingEntitlementPolicy.reduce(false, BillingState.PENDING, complete))
            assertTrue(BillingEntitlementPolicy.reduce(false, BillingState.OWNED, complete))
        }
    }
}
