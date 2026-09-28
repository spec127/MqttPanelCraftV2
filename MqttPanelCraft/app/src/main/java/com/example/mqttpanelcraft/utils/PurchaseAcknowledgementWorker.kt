package com.example.mqttpanelcraft.utils

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class PurchaseAcknowledgementWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val token = inputData.getString("token") ?: return Result.failure()
        val acknowledged = withTimeoutOrNull(45_000L) {
            suspendCancellableCoroutine<Boolean> { continuation ->
                PlayBillingManager.acknowledge(applicationContext, token) {
                    if (continuation.isActive) continuation.resume(it)
                }
            }
        } ?: false
        return if (acknowledged) Result.success() else Result.retry()
    }
}
