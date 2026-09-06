package com.example.mqttpanelcraft.mqtt

data class MqttSnapshot(val topic: String, val payload: String, val sequence: Long)

class MqttSnapshotCache(
    private val maxTopics: Int = 128,
    private val maxPayloadBytes: Int = 64 * 1024
) {
    private val values = object : LinkedHashMap<String, MqttSnapshot>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MqttSnapshot>?): Boolean =
            size > maxTopics
    }
    private var sequence = 0L

    @Synchronized
    fun put(topic: String, payload: String): MqttSnapshot? {
        if (payload.toByteArray(Charsets.UTF_8).size > maxPayloadBytes || isImageTopic(topic)) return null
        val snapshot = MqttSnapshot(topic, payload, ++sequence)
        values[topic] = snapshot
        return snapshot
    }

    @Synchronized fun currentSequence(): Long = sequence

    @Synchronized
    fun since(afterSequence: Long): List<MqttSnapshot> =
        values.values.filter { it.sequence > afterSequence }.sortedBy { it.sequence }

    @Synchronized
    fun clear() {
        values.clear()
        sequence = 0L
    }

    @Synchronized fun size(): Int = values.size

    private fun isImageTopic(topic: String): Boolean =
        topic.split('/').any { it.equals("image", true) || it.equals("image_sensor", true) }
}
