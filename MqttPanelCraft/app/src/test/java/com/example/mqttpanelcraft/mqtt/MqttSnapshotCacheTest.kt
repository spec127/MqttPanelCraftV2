package com.example.mqttpanelcraft.mqtt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MqttSnapshotCacheTest {
    @Test
    fun keepsOnlyLatestValueAndEvictsOldestTopic() {
        val cache = MqttSnapshotCache(maxTopics = 2, maxPayloadBytes = 32)
        cache.put("a/state", "1")
        cache.put("b/state", "2")
        cache.put("a/state", "3")
        cache.put("c/state", "4")

        assertEquals(listOf("a/state", "c/state"), cache.since(0).map { it.topic })
        assertEquals(listOf("3", "4"), cache.since(0).map { it.payload })
    }

    @Test
    fun rejectsImagesAndOversizedPayloads() {
        val cache = MqttSnapshotCache(maxTopics = 4, maxPayloadBytes = 4)
        assertNull(cache.put("project/id/image/1", "abc"))
        assertNull(cache.put("project/id/text/1", "12345"))
        assertEquals(0, cache.size())
    }
}
