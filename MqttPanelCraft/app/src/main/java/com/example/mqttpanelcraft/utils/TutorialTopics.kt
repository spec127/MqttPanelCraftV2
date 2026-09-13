package com.example.mqttpanelcraft.utils

object TutorialTopics {
    const val COMMAND_SUFFIX = "light/cmd"

    fun sharedCommandTopic(projectName: String, projectId: String): String =
        "${TopicHelper.formatBaseTopic(projectName, projectId)}/$COMMAND_SUFFIX"
}
