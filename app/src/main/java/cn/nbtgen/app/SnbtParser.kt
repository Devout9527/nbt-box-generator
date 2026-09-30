package cn.nbtgen.app

import org.json.JSONArray
import org.json.JSONObject

/** 递归下降解析网易版 SNBT，返回 Map/List/String 结构。 */
class SnbtParser(private val s: String) {
    private var i = 0
    private val n = s.length

    private fun ws() { while (i < n && s[i].isWhitespace()) i++ }

    fun parse(): Any {
        ws()
        return value()
    }

    private fun value(): Any {
        ws()
        if (i >= n) throw IllegalArgumentException("意外结束")
        return when (s[i]) {
            '{' -> compound()
            '[' -> list()
            '"' -> string()
            else -> bareword()
        }
    }

    private fun compound(): LinkedHashMap<String, Any> {
        i++ // {
        val d = LinkedHashMap<String, Any>()
        ws()
        if (i < n && s[i] == '}') { i++; return d }
        while (true) {
            ws()
            val k = key()
            ws()
            if (i >= n || s[i] != ':') throw IllegalArgumentException("复合标签缺少 ':' 于 $i")
            i++
            d[k] = value()
            ws()
            if (i >= n) throw IllegalArgumentException("复合标签未闭合")
            when (s[i]) {
                ',' -> i++
                '}' -> { i++; break }
                else -> throw IllegalArgumentException("复合标签格式错误 于 $i")
            }
        }
        return d
    }

    private fun key(): String {
        if (s[i] == '"') return string()
        val st = i
        while (i < n && s[i] !in ":,{}[]\"") i++
        return s.substring(st, i).trim()
    }

    private fun list(): ArrayList<Any> {
        i++ // [
        val arr = ArrayList<Any>()
        ws()
        if (i < n && s[i] == ']') { i++; return arr }
        while (true) {
            arr.add(value())
            ws()
            if (i >= n) throw IllegalArgumentException("列表未闭合")
            when (s[i]) {
                ',' -> i++
                ']' -> { i++; break }
                else -> throw IllegalArgumentException("列表格式错误 于 $i")
            }
        }
        return arr
    }

    private fun string(): String {
        i++ // 开头 "
        val sb = StringBuilder()
        while (i < n) {
            val c = s[i]
            if (c == '\\') {
                i++
                if (i < n) { sb.append(s[i]); i++ }
            } else if (c == '"') {
                i++
                break
            } else {
                sb.append(c); i++
            }
        }
        return sb.toString()
    }

    private fun bareword(): String {
        val st = i
        while (i < n && s[i] !in ",{}[]\"") i++
        return s.substring(st, i).trim()
    }
}

// ---------- 值转换 ----------
@Suppress("UNCHECKED_CAST")
private fun asMap(o: Any?): Map<String, Any> = (o as? Map<String, Any>) ?: emptyMap()
private fun asList(o: Any?): List<Any> = (o as? List<Any>) ?: emptyList()
/** 去掉数字后缀 b/s/f/d，返回 Int / Double / 原字符串 */
private fun numTok(o: Any?): Any {
    var t = o?.toString()?.trim() ?: return 0
    if (t.isNotEmpty() && t.last() in "bsfdlL") t = t.dropLast(1)
    return t.toIntOrNull() ?: t.toDoubleOrNull() ?: t
}
private fun numInt(o: Any?, def: Int = 0): Int = (numTok(o) as? Number)?.toInt() ?: def
private fun truthy(o: Any?): Boolean {
    val v = numTok(o)
    return when (v) {
        is Number -> v.toInt() != 0
        is String -> v == "true" || v == "1"
        else -> false
    }
}

object ModelParser {
    fun parseBoxModel(snbt: String): JSONObject {
        val rootAny = SnbtParser(snbt).parse()
        val root = asMap(rootAny)
        val name = root["Name"]?.toString() ?: ""
        return when {
            name == "minecraft:moving_block" -> parseSingleMovingBlock(root)
            name.contains("bucket") -> {
                val tag = asMap(root["tag"])
                val offers = asMap(tag["Offers"])
                if (asList(offers["Recipes"]).isNotEmpty()) parseVillager(root)
                else throw IllegalArgumentException("这是实体桶（装怪物/生物的桶），暂不支持导入编辑")
            }
            name == "minecraft:mob_spawner" -> throw IllegalArgumentException("刷怪笼物品暂不支持导入编辑")
            name == "minecraft:chest" -> {
                val tag = asMap(root["tag"])
                val items = asList(tag["Items"])
                val isCmd = items.any { it is Map<*, *> && (it as Map<*, *>)["Name"] == "minecraft:moving_block" }
                when {
                    isCmd -> parseCommand(root, tag, items)
                    items.isNotEmpty() -> parseEquipment(root, tag, items)
                    else -> throw IllegalArgumentException("空箱子（没有 Items）")
                }
            }
            else -> throw IllegalArgumentException("未识别的物品类型：$name（仅支持命令盒/装备盒/村民鱼桶）")
        }
    }

    private fun dirOf(root: Map<String, Any>): String {
        val states = asMap(asMap(root["Block"])["states"])
        return states["minecraft:cardinal_direction"]?.toString() ?: "north"
    }
    private fun countOf(root: Map<String, Any>): Int = numInt(root["Count"], 64)
    private fun versionOf(root: Map<String, Any>): Int = numInt(asMap(root["Block"])["version"], MC_VERSION)

    private fun parseCommand(root: Map<String, Any>, tag: Map<String, Any>, items: List<Any>): JSONObject {
        val boxName = tag["CustomName"]?.toString() ?: ""
        val boxLore = asList(asMap(tag["display"])["Lore"]).joinToString("\n") { it.toString() }
        val keeps = truthy(tag["minecraft:keep_on_death"])
        val locks = truthy(tag["minecraft:item_lock"])

        val itemsArr = JSONArray()
        for (it in items) {
            val m = asMap(it)
            val itTag = asMap(m["tag"])
            val disp = asMap(itTag["display"])
            val iname = disp["Name"]?.toString() ?: ""
            val ilore = asList(disp["Lore"]).joinToString("\n") { x -> x.toString() }
            val occ = asList(asMap(itTag["movingEntity"])["Occupants"])
            val cmds = JSONArray()
            for (o in occ) {
                val sd = asMap(asMap(o)["SaveData"])
                cmds.put(JSONObject()
                    .put("mname", sd["CustomName"]?.toString() ?: "")
                    .put("command", sd["Command"]?.toString() ?: ""))
            }
            itemsArr.put(JSONObject().put("iname", iname).put("lore", ilore).put("cmds", cmds))
        }

        var ticking = 60; var tickWorld = false; var radius = 4
        val firstItem = asMap(items.firstOrNull())
        val firstOcc = asList(asMap(firstItem["tag"]).let { asMap(it["movingEntity"]) }["Occupants"]).firstOrNull()
        val sd0 = asMap(asMap(firstOcc)["SaveData"])
        ticking = numInt(sd0["Ticking"], 60)
        val nc = asMap(sd0["neteaseComponents"])
        if (sd0.containsKey("neteaseComponents")) {
            tickWorld = true
            val tw = nc["minecraft:tick_world"]
            if (tw != null) {
                radius = runCatching {
                    JSONObject(tw.toString()).optInt("radius", 4)
                }.getOrDefault(4)
            }
        }

        return JSONObject()
            .put("mode", "command")
            .put("box_name", boxName)
            .put("box_lore", boxLore)
            .put("direction", dirOf(root))
            .put("count", countOf(root))
            .put("ticking", ticking)
            .put("radius", radius)
            .put("tick_world", tickWorld)
            .put("ench", tag.containsKey("ench"))
            .put("keep_on_death", keeps)
            .put("item_lock", locks)
            .put("unbreakable", true)
            .put("version", versionOf(root))
            .put("items", itemsArr)
    }

    private fun parseSingleMovingBlock(root: Map<String, Any>): JSONObject {
        val tag = asMap(root["tag"])
        val disp = asMap(tag["display"])
        val iname = disp["Name"]?.toString() ?: ""
        val ilore = asList(disp["Lore"]).joinToString("\n") { it.toString() }
        val occ = asList(asMap(tag["movingEntity"])["Occupants"])
        val cmds = JSONArray()
        for (o in occ) {
            val sd = asMap(asMap(o)["SaveData"])
            cmds.put(JSONObject()
                .put("mname", sd["CustomName"]?.toString() ?: "")
                .put("command", sd["Command"]?.toString() ?: ""))
        }
        val sd0 = asMap(asMap(occ.firstOrNull())["SaveData"])
        var tickWorld = sd0.containsKey("neteaseComponents")
        var radius = 4
        if (tickWorld) {
            val tw = asMap(sd0["neteaseComponents"])["minecraft:tick_world"]
            if (tw != null) radius = runCatching { JSONObject(tw.toString()).optInt("radius", 4) }.getOrDefault(4)
        }
        val item = JSONObject().put("iname", iname).put("lore", ilore).put("cmds", cmds)
        return JSONObject()
            .put("mode", "command")
            .put("box_name", iname)
            .put("box_lore", ilore)
            .put("direction", "north")
            .put("count", numInt(root["Count"], 64))
            .put("ticking", numInt(sd0["Ticking"], 60))
            .put("radius", radius)
            .put("tick_world", tickWorld)
            .put("ench", tag.containsKey("ench"))
            .put("keep_on_death", truthy(tag["minecraft:keep_on_death"]))
            .put("item_lock", truthy(tag["minecraft:item_lock"]))
            .put("unbreakable", tag.containsKey("Unbreakable"))
            .put("version", versionOf(root))
            .put("items", JSONArray().put(item))
    }

    private fun parseEquipment(root: Map<String, Any>, tag: Map<String, Any>, items: List<Any>): JSONObject {
        val boxName = asMap(tag["display"])["Name"]?.toString() ?: ""
        val itemsArr = JSONArray()
        var keep = true; var lock = true
        for (it in items) {
            val m = asMap(it)
            val itTag = asMap(m["tag"])
            val disp = asMap(itTag["display"])
            val ep = ArrayList<String>()
            for (e in asList(itTag["ench"])) {
                val em = asMap(e)
                ep.add("${numInt(em["id"])}:${numInt(em["lvl"])}")
            }
            keep = truthy(itTag["minecraft:keep_on_death"])
            lock = truthy(itTag["minecraft:item_lock"])
            itemsArr.put(JSONObject()
                .put("name", m["Name"]?.toString() ?: "")
                .put("slot", numInt(m["Slot"]))
                .put("count", numInt(m["Count"], 1))
                .put("ench", ep.joinToString(","))
                .put("iname", disp["Name"]?.toString() ?: "")
                .put("lore", asList(disp["Lore"]).joinToString("\n") { x -> x.toString() }))
        }
        return JSONObject()
            .put("mode", "equipment")
            .put("box_name", boxName)
            .put("direction", dirOf(root))
            .put("count", countOf(root))
            .put("keep_on_death", keep)
            .put("item_lock", lock)
            .put("unbreakable", true)
            .put("version", versionOf(root))
            .put("items", itemsArr)
    }

    private fun parseVillager(root: Map<String, Any>): JSONObject {
        val tag = asMap(root["tag"])
        val disp = asMap(tag["display"])
        val recipes = asList(asMap(tag["Offers"])["Recipes"])
        val trades = JSONArray()
        for (r in recipes) {
            val rm = asMap(r)
            val buy = asMap(rm["buyA"])
            val sell = asMap(rm["sell"])
            trades.put(JSONObject()
                .put("buy", buy["Name"]?.toString() ?: "")
                .put("buy_count", numInt(buy["Count"], 1))
                .put("sell", sell["Name"]?.toString() ?: "")
                .put("sell_count", numInt(sell["Count"], 1)))
        }
        val firstRecipe = asMap(recipes.firstOrNull())
        return JSONObject()
            .put("mode", "villager")
            .put("bucket_name", root["Name"]?.toString() ?: "minecraft:cod_bucket")
            .put("box_name", disp["Name"]?.toString() ?: "")
            .put("box_lore", asList(disp["Lore"]).joinToString("\n") { it.toString() })
            .put("count", numInt(root["Count"], 1))
            .put("maxUses", numInt(firstRecipe["maxUses"], 1000))
            .put("traderExp", numInt(firstRecipe["traderExp"], 1145))
            .put("tickDelay", numInt(tag["TickDelay"], 250))
            .put("definitions", asList(tag["definitions"]).joinToString(",") { it.toString() })
            .put("trades", trades)
    }
}
