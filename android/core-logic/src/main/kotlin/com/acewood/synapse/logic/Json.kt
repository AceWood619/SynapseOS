package com.acewood.synapse.logic

/**
 * Tiny dependency-free JSON reader/writer, so the same code runs in JVM unit tests and on Android.
 * Values map to: null, Boolean, Double (all numbers), String, List<Any?>, Map<String, Any?>.
 */
object Json {
    fun parse(text: String): Any? = Parser(text).run {
        val v = readValue()
        skipWs()
        if (pos != text.length) error("trailing data at $pos")
        v
    }

    @Suppress("UNCHECKED_CAST")
    fun parseObject(text: String): Map<String, Any?> =
        parse(text) as? Map<String, Any?> ?: throw IllegalArgumentException("JSON root is not an object")

    fun write(value: Any?): String = StringBuilder().also { writeTo(it, value) }.toString()

    private fun writeTo(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is Boolean -> sb.append(v)
            is Int, is Long, is Short, is Byte -> sb.append(v)
            is Number -> {
                val d = v.toDouble()
                if (d.isNaN() || d.isInfinite()) sb.append("null")
                else if (d == Math.rint(d) && kotlin.math.abs(d) < 1e15) sb.append(d.toLong())
                else sb.append(d)
            }
            is String -> quote(sb, v)
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, x) in v) {
                    if (!first) sb.append(',')
                    first = false
                    quote(sb, k.toString()); sb.append(':'); writeTo(sb, x)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (x in v) {
                    if (!first) sb.append(',')
                    first = false
                    writeTo(sb, x)
                }
                sb.append(']')
            }
            is Array<*> -> writeTo(sb, v.asList())
            else -> quote(sb, v.toString())
        }
    }

    private fun quote(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
        }
        sb.append('"')
    }

    private class Parser(val s: String) {
        var pos = 0
        fun skipWs() { while (pos < s.length && s[pos].isWhitespace()) pos++ }
        fun error(msg: String): Nothing = throw IllegalArgumentException("JSON: $msg")
        fun readValue(): Any? {
            skipWs()
            if (pos >= s.length) error("unexpected end")
            return when (val c = s[pos]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) readNumber() else error("unexpected '$c' at $pos")
            }
        }
        fun literal(word: String, v: Any?): Any? {
            if (!s.startsWith(word, pos)) error("bad literal at $pos")
            pos += word.length
            return v
        }
        fun readNumber(): Double {
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] in "+-.eE")) pos++
            return s.substring(start, pos).toDoubleOrNull() ?: error("bad number at $start")
        }
        fun readString(): String {
            pos++ // opening quote
            val sb = StringBuilder()
            while (true) {
                if (pos >= s.length) error("unterminated string")
                val c = s[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (pos >= s.length) error("bad escape")
                        when (val e = s[pos++]) {
                            '"', '\\', '/' -> sb.append(e)
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000c')
                            'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > s.length) error("bad unicode escape")
                                sb.append(s.substring(pos, pos + 4).toInt(16).toChar()); pos += 4
                            }
                            else -> error("bad escape \\$e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }
        fun readArray(): List<Any?> {
            pos++
            val out = ArrayList<Any?>()
            skipWs()
            if (pos < s.length && s[pos] == ']') { pos++; return out }
            while (true) {
                out.add(readValue()); skipWs()
                if (pos >= s.length) error("unterminated array")
                when (s[pos++]) { ',' -> continue; ']' -> return out; else -> error("expected , or ] at ${pos - 1}") }
            }
        }
        fun readObject(): Map<String, Any?> {
            pos++
            val out = LinkedHashMap<String, Any?>()
            skipWs()
            if (pos < s.length && s[pos] == '}') { pos++; return out }
            while (true) {
                skipWs()
                if (pos >= s.length || s[pos] != '"') error("expected key at $pos")
                val k = readString(); skipWs()
                if (pos >= s.length || s[pos++] != ':') error("expected : after key")
                out[k] = readValue(); skipWs()
                if (pos >= s.length) error("unterminated object")
                when (s[pos++]) { ',' -> continue; '}' -> return out; else -> error("expected , or } at ${pos - 1}") }
            }
        }
    }
}
