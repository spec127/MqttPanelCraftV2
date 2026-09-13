package com.example.mqttpanelcraft.utils

/** Limited static analysis, never executes user JavaScript or invents a Topic prefix. */
internal class ArduinoWebTopics(private val baseTopic: String) {
    private val variables = mutableMapOf<String, String>()
    private val literal = Regex("""^(?:"((?:\\.|[^"\\])*)"|'((?:\\.|[^'\\])*)')$""")

    fun readVariables(html: String) {
        Regex("(?:const|let|var)\\s+(\\w+)\\s*=\\s*([^;\\r\\n]+)").findAll(html).forEach {
            resolve(it.groupValues[2])?.let { topic -> variables[it.groupValues[1]] = topic }
        }
    }

    fun resolve(expression: String): String? {
        val value = expression.trim()
        variables[value]?.let { return it }
        if (value == "window.app.getBaseTopic()" || value == "app.getBaseTopic()" ||
                value.startsWith("(window.app) ? window.app.getBaseTopic() : ")) return baseTopic
        literal.matchEntire(value)?.let { match ->
            val raw = match.groups[1]?.value ?: match.groupValues[2]
            val out = StringBuilder()
            var i = 0
            while (i < raw.length) {
                val ch = raw[i++]
                if (ch != '\\') { out.append(ch); continue }
                if (i >= raw.length) return null
                when (val escaped = raw[i++]) {
                    '\\', '\'', '"', '/' -> out.append(escaped)
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    't' -> out.append('\t')
                    'u', 'x' -> {
                        val count = if (escaped == 'u') 4 else 2
                        if (i + count > raw.length) return null
                        val code = raw.substring(i, i + count).toIntOrNull(16) ?: return null
                        out.append(code.toChar()); i += count
                    }
                    else -> return null
                }
            }
            return out.toString()
        }
        val concat = Regex("^(\\w+)\\s*\\+\\s*(.+)$").matchEntire(value) ?: return null
        val prefix = variables[concat.groupValues[1]] ?: return null
        return resolve(concat.groupValues[2])?.let { prefix + it }
    }
}
