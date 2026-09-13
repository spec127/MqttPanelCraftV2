package com.example.mqttpanelcraft.utils

object DemoBroker {
    const val HOST = "demo.local"
    const val PORT = 1883

    fun isLocal(broker: String?): Boolean =
        broker?.trim().equals(HOST, ignoreCase = true)
}
