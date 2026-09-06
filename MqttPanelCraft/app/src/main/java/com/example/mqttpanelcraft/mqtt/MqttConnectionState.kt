package com.example.mqttpanelcraft.mqtt

enum class MqttConnectionState {
    IDLE,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    FAILED,
    STOPPED
}
