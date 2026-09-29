package com.carom.core.level

/** Thrown when level text is not valid JSON or does not describe a valid level. */
class LevelFormatException(message: String) : RuntimeException(message)

/**
 * A small JSON reader, so the core needs no third-party dependency and parses levels identically
 * on the device and in tests. Objects become Map<String, Any?>, arrays List<Any?>, numbers Double.
 * `//` line comments are allowed so level files can be annotated.
 */
internal class JsonReader private constructor(private val text: String) {
    private var pos = 0

    companion object {
        fun parse(text: String): Any? {
            val reader = JsonReader(text)
            val value = reader.readValue()
            reader.skipWhitespace()
            if (reader.pos != text.length) reader.fail("unexpected trailing content")
            return value
        }
    }

    private fun readValue(): Any? {
        skipWhitespace()
        if (pos >= text.length) fail("unexpected end of input")
        return when (val c = text[pos]) {
            '{' -> readObject()
            '[' -> readArray()
            '"' -> readString()
            't' -> readLiteral("true", true)
            'f' -> readLiteral("false", false)
            'n' -> readLiteral("null", null)
            else -> if (c == '-' || c in '0'..'9') readNumber() else fail("unexpected '$c'")
        }
    }

    private fun readObject(): Map<String, Any?> {
        val result = LinkedHashMap<String, Any?>()
        pos++ // {
        skipWhitespace()
        if (peek() == '}') {
            pos++
            return result
        }
        while (true) {
            skipWhitespace()
            if (peek() != '"') fail("expected a quoted key")
            val key = readString()
            skipWhitespace()
            expect(':')
            result[key] = readValue()
            skipWhitespace()
            when (peek()) {
                ',' -> pos++
                '}' -> {
                    pos++
                    return result
                }
                else -> fail("expected ',' or '}'")
            }
        }
    }

    private fun readArray(): List<Any?> {
        val result = ArrayList<Any?>()
        pos++ // [
        skipWhitespace()
        if (peek() == ']') {
            pos++
            return result
        }
        while (true) {
            result.add(readValue())
            skipWhitespace()
            when (peek()) {
                ',' -> pos++
                ']' -> {
                    pos++
                    return result
                }
                else -> fail("expected ',' or ']'")
            }
        }
    }

    private fun readString(): String {
        pos++ // opening quote
        val sb = StringBuilder()
        while (true) {
            if (pos >= text.length) fail("unterminated string")
            val c = text[pos++]
            when (c) {
                '"' -> return sb.toString()
                '\\' -> {
                    if (pos >= text.length) fail("unterminated escape")
                    when (val e = text[pos++]) {
                        '"', '\\', '/' -> sb.append(e)
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (pos + 4 > text.length) fail("bad unicode escape")
                            val code = text.substring(pos, pos + 4).toIntOrNull(16) ?: fail("bad unicode escape")
                            sb.append(code.toChar())
                            pos += 4
                        }
                        else -> fail("bad escape '\\$e'")
                    }
                }
                else -> sb.append(c)
            }
        }
    }

    private fun readNumber(): Double {
        val start = pos
        if (peek() == '-') pos++
        while (pos < text.length && (text[pos].isDigit() || text[pos] in ".eE+-")) pos++
        return text.substring(start, pos).toDoubleOrNull() ?: fail("bad number '${text.substring(start, pos)}'")
    }

    private fun readLiteral(word: String, value: Any?): Any? {
        if (!text.startsWith(word, pos)) fail("unexpected token")
        pos += word.length
        return value
    }

    fun skipWhitespace() {
        while (pos < text.length) {
            val c = text[pos]
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                pos++
            } else if (c == '/' && pos + 1 < text.length && text[pos + 1] == '/') {
                while (pos < text.length && text[pos] != '\n') pos++
            } else {
                return
            }
        }
    }

    private fun peek(): Char = if (pos < text.length) text[pos] else '\u0000'

    private fun expect(c: Char) {
        if (peek() != c) fail("expected '$c'")
        pos++
    }

    private fun fail(message: String): Nothing {
        val line = text.substring(0, minOf(pos, text.length)).count { it == '\n' } + 1
        throw LevelFormatException("JSON error at line $line: $message")
    }
}
