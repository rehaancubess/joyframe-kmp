// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.render.gltf

internal class JsonException(message: String) : Exception(message)

internal sealed interface JsonValue

internal class JsonObject(val members: Map<String, JsonValue>) : JsonValue {
    operator fun contains(name: String) = name in members

    fun obj(name: String): JsonObject? = members[name] as? JsonObject
    fun array(name: String): JsonArray? = members[name] as? JsonArray
    fun string(name: String): String? = (members[name] as? JsonString)?.value
    fun double(name: String): Double? = (members[name] as? JsonNumber)?.value
    fun float(name: String, fallback: Float): Float = double(name)?.toFloat() ?: fallback
    fun int(name: String): Int? = double(name)?.toInt()
    fun int(name: String, fallback: Int): Int = int(name) ?: fallback
    fun boolean(name: String, fallback: Boolean): Boolean = (members[name] as? JsonBoolean)?.value ?: fallback


    fun floats(name: String, expected: Int): FloatArray? {
        val items = array(name)?.items ?: return null
        if (items.size != expected) return null
        return FloatArray(expected) { (items[it] as? JsonNumber)?.value?.toFloat() ?: return null }
    }
}

internal class JsonArray(val items: List<JsonValue>) : JsonValue {
    val size: Int get() = items.size
    fun obj(index: Int): JsonObject? = items.getOrNull(index) as? JsonObject
    fun objects(): List<JsonObject> = items.filterIsInstance<JsonObject>()
    fun ints(): List<Int> = items.mapNotNull { (it as? JsonNumber)?.value?.toInt() }
}

internal class JsonString(val value: String) : JsonValue
internal class JsonNumber(val value: Double) : JsonValue
internal class JsonBoolean(val value: Boolean) : JsonValue
internal object JsonNull : JsonValue

internal object Json {

    fun parse(text: String): JsonValue {
        val reader = Reader(text)
        reader.skipWhitespace()
        val value = reader.readValue()
        reader.skipWhitespace()
        if (!reader.exhausted) reader.fail("trailing content after the top-level value")
        return value
    }

    private class Reader(private val text: String) {
        private var index = 0

        val exhausted: Boolean get() = index >= text.length

        fun fail(reason: String): Nothing = throw JsonException("JSON at offset $index: $reason")

        fun skipWhitespace() {
            while (index < text.length) {
                when (text[index]) {
                    ' ', '\t', '\n', '\r' -> index++
                    else -> return
                }
            }
        }

        fun readValue(): JsonValue {
            if (exhausted) fail("expected a value, found the end of the input")
            return when (val c = text[index]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> JsonString(readString())
                't' -> literal("true", JsonBoolean(true))
                'f' -> literal("false", JsonBoolean(false))
                'n' -> literal("null", JsonNull)
                else -> if (c == '-' || c in '0'..'9') readNumber() else fail("unexpected character '$c'")
            }
        }

        private fun literal(word: String, value: JsonValue): JsonValue {
            if (!text.startsWith(word, index)) fail("expected '$word'")
            index += word.length
            return value
        }

        private fun readObject(): JsonObject {
            index++ // '{'
            val members = LinkedHashMap<String, JsonValue>()
            skipWhitespace()
            if (!exhausted && text[index] == '}') {
                index++
                return JsonObject(members)
            }
            while (true) {
                skipWhitespace()
                if (exhausted || text[index] != '"') fail("expected a member name")
                val name = readString()
                skipWhitespace()
                if (exhausted || text[index] != ':') fail("expected ':' after the member name")
                index++
                skipWhitespace()
                members[name] = readValue()
                skipWhitespace()
                if (exhausted) fail("unterminated object")
                when (text[index]) {
                    ',' -> index++
                    '}' -> { index++; return JsonObject(members) }
                    else -> fail("expected ',' or '}' in an object")
                }
            }
        }

        private fun readArray(): JsonArray {
            index++ // '['
            val items = ArrayList<JsonValue>()
            skipWhitespace()
            if (!exhausted && text[index] == ']') {
                index++
                return JsonArray(items)
            }
            while (true) {
                skipWhitespace()
                items += readValue()
                skipWhitespace()
                if (exhausted) fail("unterminated array")
                when (text[index]) {
                    ',' -> index++
                    ']' -> { index++; return JsonArray(items) }
                    else -> fail("expected ',' or ']' in an array")
                }
            }
        }

        private fun readString(): String {
            index++ // opening quote
            val builder = StringBuilder()
            while (true) {
                if (exhausted) fail("unterminated string")
                when (val c = text[index]) {
                    '"' -> { index++; return builder.toString() }
                    '\\' -> {
                        index++
                        if (exhausted) fail("unterminated escape")
                        when (val escape = text[index]) {
                            '"' -> builder.append('"')
                            '\\' -> builder.append('\\')
                            '/' -> builder.append('/')
                            'b' -> builder.append('\b')
                            'f' -> builder.append('\u000C')
                            'n' -> builder.append('\n')
                            'r' -> builder.append('\r')
                            't' -> builder.append('\t')
                            'u' -> {
                                if (index + 4 >= text.length) fail("truncated \\u escape")
                                val hex = text.substring(index + 1, index + 5)
                                val code = hex.toIntOrNull(16) ?: fail("invalid \\u escape '$hex'")
                                builder.append(code.toChar())
                                index += 4
                            }
                            else -> fail("unsupported escape '\\$escape'")
                        }
                        index++
                    }
                    else -> { builder.append(c); index++ }
                }
            }
        }

        private fun readNumber(): JsonNumber {
            val start = index
            if (text[index] == '-') index++
            while (index < text.length && text[index] in '0'..'9') index++
            if (index < text.length && text[index] == '.') {
                index++
                while (index < text.length && text[index] in '0'..'9') index++
            }
            if (index < text.length && (text[index] == 'e' || text[index] == 'E')) {
                index++
                if (index < text.length && (text[index] == '+' || text[index] == '-')) index++
                while (index < text.length && text[index] in '0'..'9') index++
            }
            val literal = text.substring(start, index)
            return JsonNumber(literal.toDoubleOrNull() ?: fail("invalid number '$literal'"))
        }
    }
}
