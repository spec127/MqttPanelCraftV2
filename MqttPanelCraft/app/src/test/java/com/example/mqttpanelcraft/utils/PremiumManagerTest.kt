package com.example.mqttpanelcraft.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumManagerTest {
    @Test
    fun playPurchaseOrDebugSkipUnlocksPremium() {
        assertFalse(PremiumManager.isEntitled(playOwned = false, debugSkip = false))
        assertTrue(PremiumManager.isEntitled(playOwned = true, debugSkip = false))
        assertTrue(PremiumManager.isEntitled(playOwned = false, debugSkip = true))
        assertTrue(PremiumManager.isEntitled(playOwned = true, debugSkip = true))
    }

    @Test
    fun premiumProductIdIsNonConsumableUnlock() {
        assertEquals("premium_unlock", PlayBillingConfig.PRODUCT_ID)
    }
}
