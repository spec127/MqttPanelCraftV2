package com.example.mqttpanelcraft.ui

/** Full, case-sensitive MQTT address. Empty disables a component's topic.
 * Presentation (ellipses, labels) must never participate in persistence.
 * This does not add wildcard routing or rewrite existing topics.
 */
object TopicEditorValue {
    fun isValid(value: String): Boolean =
        value.none { it == '\u0000' || it == '\r' || it == '\n' } &&
            value.toByteArray(Charsets.UTF_8).size <= 65535

    fun acceptedOrPrevious(input: String, previous: String): String =
        if (isValid(input)) input else previous
}
