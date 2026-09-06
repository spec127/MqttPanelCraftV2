package com.example.mqttpanelcraft

import android.app.NotificationManager
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mqttpanelcraft.data.ProjectRepository
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.model.Project
import com.example.mqttpanelcraft.model.ProjectType
import com.example.mqttpanelcraft.mqtt.MqttConnectionState
import com.example.mqttpanelcraft.mqtt.MqttSessionClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MqttSessionServiceInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val backgroundId = "codex_mqtt_background_test"
    private val foregroundOnlyId = "codex_mqtt_foreground_test"
    private val topicRoot = "mqttpanelcraft/codex/android36/session_test"

    @Before
    fun setUp() {
        ProjectRepository.initialize(context)
        ProjectRepository.deleteProject(backgroundId)
        ProjectRepository.deleteProject(foregroundOnlyId)
        MqttSessionClient.stop(context)
        waitUntil(5_000) { MqttRepository.activeProjectId == null }
    }

    @After
    fun tearDown() {
        MqttSessionClient.stop(context)
        ProjectRepository.deleteProject(backgroundId)
        ProjectRepository.deleteProject(foregroundOnlyId)
    }

    @Test
    fun deniedPermissionAndBackgroundSessionBehaveCorrectly() {
        if (Build.VERSION.SDK_INT >= 33) {
            assertEquals(
                PackageManager.PERMISSION_DENIED,
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            )
        }
        ProjectRepository.addProject(testProject(foregroundOnlyId, keepInBackground = false))
        MqttSessionClient.activate(context, foregroundOnlyId)
        waitUntil(35_000) { MqttRepository.connectionState.value == MqttConnectionState.CONNECTED }
        MqttSessionClient.setVisible(context, foregroundOnlyId, false)
        waitUntil(5_000) { MqttRepository.activeProjectId == null }
        assertEquals(MqttConnectionState.STOPPED, MqttRepository.connectionState.value)

        setNotificationPermission(granted = true)
        val clockTopic = "$topicRoot/clock"
        val stateTopic = "$topicRoot/state"
        ProjectRepository.addProject(
            testProject(
                id = backgroundId,
                keepInBackground = true,
                components = mutableListOf(
                    ComponentData(
                        1, "CLOCK", 0f, 0f, 160, 100, "clock1", "",
                        mutableMapOf(
                            "clock_mode" to "COUNTDOWN",
                            "countdown_seconds" to "3",
                            "linked_components" to "2",
                            "trigger_value" to "TRIGGER"
                        )
                    ),
                    ComponentData(
                        2, "BUTTON", 0f, 120f, 120, 70, "target", clockTopic,
                        mutableMapOf("payload" to "CLOCK_OK")
                    ),
                    ComponentData(3, "TEXT", 140f, 120f, 120, 70, "state", stateTopic)
                )
            )
        )

        MqttRepository.markUiDetached(backgroundId)
        MqttSessionClient.activate(context, backgroundId)
        waitUntil(35_000) { MqttRepository.connectionState.value == MqttConnectionState.CONNECTED }
        assertEquals(backgroundId, MqttRepository.activeProjectId)

        if (Build.VERSION.SDK_INT >= 23) {
            waitUntil(5_000) {
                context.getSystemService(NotificationManager::class.java)
                    .activeNotifications.any { it.id == 1 }
            }
        }

        MqttSessionClient.setVisible(context, backgroundId, false)
        Thread.sleep(1_500)
        assertEquals(MqttConnectionState.CONNECTED, MqttRepository.connectionState.value)

        MqttSessionClient.publish(context, stateTopic, "LATEST_BACKGROUND_VALUE")
        val snapshots = mutableListOf<com.example.mqttpanelcraft.mqtt.MqttSnapshot>()
        waitUntil(10_000) {
            snapshots += MqttRepository.consumeBackgroundSnapshots(backgroundId)
            snapshots.any { it.topic == stateTopic && it.payload == "LATEST_BACKGROUND_VALUE" } &&
                snapshots.any { it.topic == clockTopic && it.payload == "CLOCK_OK" }
        }

        MqttSessionClient.stop(context)
        waitUntil(5_000) { MqttRepository.activeProjectId == null }
        assertEquals(MqttConnectionState.STOPPED, MqttRepository.connectionState.value)
    }

    private fun testProject(
        id: String,
        keepInBackground: Boolean,
        components: MutableList<ComponentData> = mutableListOf()
    ) = Project(
        id = id,
        name = "Codex Android 36 MQTT Test",
        broker = "10.0.2.2",
        port = 1884,
        type = ProjectType.HOME,
        components = components,
        keepMqttInBackground = keepInBackground
    )

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(100)
        }
        throw AssertionError("Condition not met within ${timeoutMs}ms; state=${MqttRepository.connectionState.value}")
    }

    private fun setNotificationPermission(granted: Boolean) {
        if (Build.VERSION.SDK_INT < 33) return
        val operation = if (granted) "grant" else "revoke"
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("pm $operation ${context.packageName} android.permission.POST_NOTIFICATIONS")
            .close()
        Thread.sleep(200)
    }
}
