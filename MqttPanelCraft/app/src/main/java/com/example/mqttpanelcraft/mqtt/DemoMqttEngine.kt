package com.example.mqttpanelcraft.mqtt

import com.example.mqttpanelcraft.MqttRepository

object DemoMqttEngine {
    fun publish(topic: String, payload: String, timestamp: String) {
        MqttRepository.processMessage(topic, payload, timestamp)
        MqttRepository.addLog("TX [$topic]: $payload", timestamp)
    }
}
