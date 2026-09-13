package com.example.mqttpanelcraft.utils

import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.model.Project
import java.util.Locale

object ArduinoExportSupport {
    const val CFG_LEN = 128
    const val PORT_LEN = 8
    const val AUTH_LEN = 64

    fun escapeCString(value: String): String {
        val out = StringBuilder(value.length)
        value.forEach { ch ->
            when (ch) {
                '\\' -> out.append("\\\\")
                '"' -> out.append("\\\"")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (ch.code < 32 || ch.code == 127) {
                    out.append("\\").append(ch.code.toString(8).padStart(3, '0'))
                } else out.append(ch)
            }
        }
        // Also safe when the template uses this value inside a C++ comment.
        return out.toString().replace("*/", "*\\057")
    }

    fun escapeComment(value: String): String = value
            .replace("*/", "* /").replace('\\', '/')
            .map { if (it.isISOControl()) ' ' else it }.joinToString("")

    /** One pass: user data containing {{...}} must never become another template. */
    fun fillTemplate(template: String, values: Map<String, String>): String =
            Regex("\\{\\{([A-Z0-9_]+)\\}\\}").replace(template) {
                values[it.groupValues[1]].orEmpty()
            }

    fun fallbackTopicSuffix(component: ComponentData): String {
        val fromLabel =
                component.label
                        .trim()
                        .lowercase(Locale.ROOT)
                        .replace("\\s+".toRegex(), "_")
                        .replace(Regex("(?<=[a-z])(?=\\d)"), "_")
                        .replace("[^a-z0-9_]".toRegex(), "")
        if (fromLabel.isNotEmpty()) return fromLabel
        return component.type.lowercase(Locale.ROOT) + "_" + component.id
    }

    fun resolveComponentTopic(project: Project, component: ComponentData): String {
        val configured = component.topicConfig.trim()
        if (configured.isNotEmpty()) return configured
        return TopicHelper.formatBaseTopic(project.name, project.id) +
                "/" +
                fallbackTopicSuffix(component)
    }

    fun lengthWarning(field: String, value: String, max: Int): String? {
        val bytes = value.toByteArray(Charsets.UTF_8).size
        if (bytes >= max) {
            return "$field exceeds ${max - 1} UTF-8 bytes ($bytes)"
        }
        return null
    }

    fun parseTemplates(json: String): ArduinoTemplateBundle {
        val root = JsonObjectReader(json).readObject()
        val base = root.obj("base")
        val components = root.obj("components").objects.mapValues { (_, value) ->
            ArduinoComponentTemplate(
                    type = value.str("type"),
                    indexKey = value.str("index_key", value.str("type")),
                    varDecl = value.str("var_decl"),
                    setupSub = value.str("setup_sub"),
                    loopLogic = value.str("loop_logic"),
                    receiverLogic = value.str("receiver_logic")
            )
        }
        return ArduinoTemplateBundle(
                header = base.str("header"),
                globals = base.str("globals"),
                setupStart = base.str("setup_start"),
                setupMid = base.str("setup_mid"),
                setupEnd = base.str("setup_end"),
                loopStart = base.str("loop_start"),
                loopEnd = base.str("loop_end"),
                receiverHead = base.str("receiver_head"),
                receiverTail = base.str("receiver_tail"),
                components = components,
                mappings = root.obj("mappings").strings
        )
    }
}

data class ArduinoTemplateBundle(
        val header: String,
        val globals: String,
        val setupStart: String,
        val setupMid: String,
        val setupEnd: String,
        val loopStart: String,
        val loopEnd: String,
        val receiverHead: String,
        val receiverTail: String,
        val components: Map<String, ArduinoComponentTemplate>,
        val mappings: Map<String, String>
)

data class ArduinoComponentTemplate(
        val type: String,
        val indexKey: String,
        val varDecl: String,
        val setupSub: String,
        val loopLogic: String,
        val receiverLogic: String
)

private class JsonNode(
        val strings: Map<String, String> = emptyMap(),
        val objects: Map<String, JsonNode> = emptyMap()
) {
    fun str(key: String, default: String = ""): String = strings[key] ?: default
    fun obj(key: String): JsonNode = objects[key] ?: JsonNode()
}

private class JsonObjectReader(private val src: String) {
    private var i = 0

    fun readObject(): JsonNode {
        skipWs()
        expect('{')
        val strings = linkedMapOf<String, String>()
        val objects = linkedMapOf<String, JsonNode>()
        skipWs()
        if (peek() == '}') {
            i++
            return JsonNode(strings, objects)
        }
        while (true) {
            skipWs()
            val key = readString()
            skipWs()
            expect(':')
            skipWs()
            when (peek()) {
                '{' -> objects[key] = readObject()
                '"' -> strings[key] = readString()
                else -> skipValue()
            }
            skipWs()
            when (peek()) {
                ',' -> i++
                '}' -> {
                    i++
                    return JsonNode(strings, objects)
                }
                else -> error("Invalid JSON at $i")
            }
        }
    }

    private fun readString(): String {
        expect('"')
        val out = StringBuilder()
        while (i < src.length) {
            val ch = src[i++]
            when (ch) {
                '"' -> return out.toString()
                '\\' -> {
                    val esc = src[i++]
                    out.append(
                            when (esc) {
                                'n' -> '\n'
                                'r' -> '\r'
                                't' -> '\t'
                                '"' -> '"'
                                '\\' -> '\\'
                                '/' -> '/'
                                'u' -> {
                                    val hex = src.substring(i, i + 4)
                                    i += 4
                                    hex.toInt(16).toChar()
                                }
                                else -> esc
                            }
                    )
                }
                else -> out.append(ch)
            }
        }
        error("Unterminated string")
    }

    private fun skipValue() {
        if (peek() == '[') {
            var depth = 0
            while (i < src.length) {
                when (src[i++]) {
                    '[' -> depth++
                    ']' -> {
                        depth--
                        if (depth == 0) return
                    }
                    '"' -> {
                        i--
                        readString()
                    }
                }
            }
        } else {
            while (i < src.length && src[i] !in ",}") i++
        }
    }

    private fun skipWs() {
        while (i < src.length && src[i].isWhitespace()) i++
    }

    private fun peek(): Char = src[i]

    private fun expect(ch: Char) {
        if (src[i] != ch) error("Expected $ch at $i")
        i++
    }
}
