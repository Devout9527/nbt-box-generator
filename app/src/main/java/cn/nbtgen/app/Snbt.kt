package cn.nbtgen.app

/**
 * 网易版 Minecraft SNBT 类型系统（带后缀 b/s/f/d）。
 * 移植自 Python 版 nbt_gen。
 */
sealed class Tag {
    abstract fun dump(): String
    override fun toString(): String = dump()
}

class TByte(val v: Int) : Tag() { override fun dump() = "${v}b" }
class TShort(val v: Int) : Tag() { override fun dump() = "${v}s" }
class TInt(val v: Int) : Tag() { override fun dump() = "$v" }
class TFloat(val v: Double) : Tag() { override fun dump() = "${dbl(v)}f" }
class TDouble(val v: Double) : Tag() { override fun dump() = "${dbl(v)}d" }
class TString(val v: String) : Tag() { override fun dump() = quote(v) }

class TList(val items: List<Tag>) : Tag() {
    override fun dump() = "[" + items.joinToString(",") { it.dump() } + "]"
}

class TCompound(val map: LinkedHashMap<String, Tag>) : Tag() {
    override fun dump() =
        "{" + map.entries.joinToString(",") { (k, v) -> quoteKey(k) + ":" + v.dump() } + "}"
}

/** 浮点转字符串：4.0 -> "4.0"，0.05 -> "0.05" */
private fun dbl(v: Double): String {
    if (v == v.toLong().toDouble()) return "${v.toLong()}.0"
    return v.toString()
}

fun quote(s: String): String =
    "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

private val NEED_QUOTE = setOf(' ', '\t', '"', ':', ',', '{', '}', '[', ']')
fun quoteKey(k: String): String {
    if (k.isEmpty()) return quote(k)
    if (k.any { it in NEED_QUOTE } || k[0].isDigit()) return quote(k)
    return k
}

/** 便捷构造复合标签，保持插入顺序 */
fun comp(vararg pairs: Pair<String, Tag>): TCompound {
    val m = LinkedHashMap<String, Tag>()
    for ((k, v) in pairs) m[k] = v
    return TCompound(m)
}
