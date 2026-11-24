package com.soloadmin.hub.domain.logic

/**
 * A tiny, dependency-free JSON tree.
 *
 * Why not `kotlinx.serialization`? The backup codec performs tolerant, hand-written
 * traversal of two competing backup dialects and is unit-tested on the plain JVM.
 * Keeping the tree local means no serialization compiler plugin, no extra runtime
 * artifact, and identical behaviour in unit tests and on device.
 *
 * [Obj.fields] and [Arr.items] are ordered: exports stay diff-friendly, and import
 * order of attendance days is preserved exactly as the legacy app wrote it.
 */
sealed class JsonNode {

    data class Obj(val fields: Map<String, JsonNode> = LinkedHashMap()) : JsonNode() {
        operator fun get(key: String): JsonNode? = fields[key]
        fun containsKey(key: String): Boolean = fields.containsKey(key)
        val entries: Map.Entry<String, JsonNode>? get() = null.let { null }

        /** Ordered key/value view, as a list of pairs. */
        val entriesList: List<Pair<String, JsonNode>> get() = fields.entries.map { it.key to it.value }
    }

    data class Arr(val items: List<JsonNode> = emptyList()) : JsonNode() {
        val size: Int get() = items.size
        fun isEmpty(): Boolean = items.isEmpty()
        fun add(value: JsonNode): Arr = Arr(items + value)
        fun <T> mapNotNull(block: (JsonNode) -> T?): List<T> = items.mapNotNull(block)
    }

    /** A scalar. [raw] is the exact source text; [isString] records whether it was quoted. */
    data class Prim(val raw: String, val isString: Boolean = true) : JsonNode() {
        val intOrNull: Int? get() = raw.trim().toIntOrNull()
        val longOrNull: Long? get() = raw.trim().toLongOrNull()
        val doubleOrNull: Double? get() = raw.trim().toDoubleOrNull()
        val booleanOrNull: Boolean?
            get() = when (raw.trim().lowercase()) {
                "true" -> true
                "false" -> false
                else -> null
            }
        /** Unquoted text; `null` for a literal JSON null (which the reader stores as an empty string). */
        val contentOrNull: String? get() = raw.ifEmpty { null }
    }
}

/** Builds an object without the ceremony of a full builder DSL. */
inline fun jsonObject(block: ObjBuilder.() -> Unit): JsonNode.Obj = ObjBuilder().apply(block).build()

inline fun jsonArray(block: ArrBuilder.() -> Unit): JsonNode.Arr = ArrBuilder().apply(block).build()

class ObjBuilder {
    private val fields = LinkedHashMap<String, JsonNode>()

    fun put(key: String, value: String) { fields[key] = JsonNode.Prim(value, true) }
    fun put(key: String, value: Int) { fields[key] = JsonNode.Prim(value.toString(), false) }
    fun put(key: String, value: Long) { fields[key] = JsonNode.Prim(value.toString(), false) }
    fun put(key: String, value: Double) { fields[key] = JsonNode.Prim(value.toString(), false) }
    fun put(key: String, value: Boolean) { fields[key] = JsonNode.Prim(value.toString(), false) }
    fun put(key: String, value: JsonNode) { fields[key] = value }

    fun build(): JsonNode.Obj = JsonNode.Obj(fields)
}

class ArrBuilder {
    private val items = mutableListOf<JsonNode>()

    fun add(value: JsonNode) { items += value }
    fun add(value: String) { items += JsonNode.Prim(value, true) }
    fun add(value: Int) { items += JsonNode.Prim(value.toString(), false) }
    fun add(value: Long) { items += JsonNode.Prim(value.toString(), false) }
    fun add(value: Double) { items += JsonNode.Prim(value.toString(), false) }

    fun build(): JsonNode.Arr = JsonNode.Arr(items)
}

/** Convenience casts shared by the codec and its callers. */
fun JsonNode?.asObjectOrNull(): JsonNode.Obj? = this as? JsonNode.Obj
fun JsonNode?.asArrayOrNull(): JsonNode.Arr? = this as? JsonNode.Arr
