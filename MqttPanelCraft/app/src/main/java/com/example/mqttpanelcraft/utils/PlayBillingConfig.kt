package com.example.mqttpanelcraft.utils

/**
 * Play Console product that must exist before real purchases work.
 *
 * Console steps (not done in git):
 * 1. Set [applicationId] to the final package name, then create the Play app with that id.
 * 2. Create a one-time **non-consumable** in-app product with id [PRODUCT_ID], price US$1.99.
 * 3. Publish the app to the internal testing track and add license testers.
 *
 * Do not call consumeAsync on this product.
 */
object PlayBillingConfig {
    const val PRODUCT_ID = "premium_unlock"
}
