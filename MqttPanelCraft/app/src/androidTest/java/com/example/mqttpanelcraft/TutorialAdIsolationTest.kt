package com.example.mqttpanelcraft

import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
import android.view.View
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mqttpanelcraft.utils.AdManager
import com.example.mqttpanelcraft.utils.AdPrivacy
import com.example.mqttpanelcraft.utils.PremiumManager
import com.example.mqttpanelcraft.utils.SaveGate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TutorialAdIsolationTest {
    @Test fun tutorialBlocksEveryAdEntryPointIncludingApplicationContextWithoutConsentOrDialogs() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext
        instrumentation.runOnMainSync {
            // Intentionally unattached: the tutorial flag must be sufficient before repository/UI setup.
            val tutorial = Activity().apply {
                intent = Intent().putExtra(ProjectViewActivity.EXTRA_SHOW_TUTORIAL, true)
            }
            assertTrue(AdManager.isTutorial(ContextWrapper(tutorial)))
            AdManager.enterTutorial()
            try {
                assertTrue(AdManager.isSuppressed(app))
                assertFalse(AdPrivacy.allowed(app))
                val banner = FrameLayout(app)
                banner.addView(View(app))
                AdManager.loadBannerAd(tutorial, banner)
                assertEquals(View.GONE, banner.visibility)
                AdManager.loadInterstitial(app)
                AdManager.loadRewarded(app)
                assertFalse(AdManager.isRewardedReady())
                var rewarded = 0
                var closed = 0
                AdManager.showRewarded(tutorial, { rewarded++ }, { closed++ })
                assertEquals(1, rewarded)
                assertEquals(1, closed)
                var consentCallback = false
                AdPrivacy.gather(tutorial) { consentCallback = true }
                assertFalse(consentCallback)
                var saved = 0
                SaveGate.show(tutorial) { saved++ }
                assertEquals("Tutorial saving must complete immediately without a dialog", 1, saved)
                var purchaseCallback = false
                PremiumManager.showPremiumDialog(tutorial) { purchaseCallback = true }
                assertFalse(purchaseCallback)
            } finally {
                AdManager.onScreenResumed(Activity().apply { intent = Intent() })
            }
            assertFalse(AdManager.isSuppressed(app))
        }
    }
}
