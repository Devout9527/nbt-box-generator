package cn.nbtgen.app

import org.json.JSONArray
import org.json.JSONObject

// ---------- JSON 取值辅助 ----------
fun JSONObject.asStr(k: String): String {
    val o = opt(k)
    return if (o == null || o === JSONObject.NULL) "" else o.toString()
}
fun JSONObject.asInt(k: String, def: Int = 0): Int {
    return when (val o = opt(k)) {
        null, JSONObject.NULL -> def
        is Number -> o.toInt()
        is String -> o.trim().toIntOrNull() ?: def
        is Boolean -> if (o) 1 else 0
        else -> def
    }
}
fun JSONObject.asBool(k: String, def: Boolean = false): Boolean {
    return when (val o = opt(k)) {
        null, JSONObject.NULL -> def
        is Boolean -> o
        is Number -> o.toInt() != 0
        is String -> o == "true" || o == "1"
        else -> def
    }
}

internal const val NETEASE_TICK = "##netease::minecraft:tick_world"
internal const val MC_VERSION = 18168865

// ---------- 底层构件 ----------
fun makeMinecart(command: String, customName: String, ticking: Int,
                 tickWorld: Boolean, radius: Int): TCompound {
    val defs = mutableListOf<Tag>(TString("+minecraft:bee"))
    if (tickWorld) defs.add(TString("+$NETEASE_TICK"))
    val save = LinkedHashMap<String, Tag>()
    save["Command"] = TString(command)
    save["CustomName"] = TString(customName)
    save["Persistent"] = TByte(1)
    save["Pos"] = TList(emptyList())
    save["Ticking"] = TByte(ticking)
    save["definitions"] = TList(defs)
    save["identifier"] = TString("minecraft:command_block_minecart")
    save["ignoreHurt"] = TByte(1)
    if (tickWorld) {
        // 与 Python json.dumps 默认格式一致：{"never_despawn": true, "radius": 4}
        save["neteaseComponents"] = comp("minecraft:tick_world" to
            TString("{\"never_despawn\": true, \"radius\": $radius}"))
    }
    return comp(
        "ActorIdentifier" to TString("minecraft:command_block_minecart<>"),
        "SaveData" to TCompound(save),
        "TicksLeftToStay" to TInt(0)
    )
}

fun makeMovingBlockItem(occupants: List<Tag>, slot: Int, count: Int,
                        displayName: String, lore: List<String>?,
                        unbreakable: Boolean, keepOnDeath: Boolean,
                        itemLock: Boolean, ench: Boolean, version: Int): TCompound {
    val display = LinkedHashMap<String, Tag>()
    if (!lore.isNullOrEmpty()) display["Lore"] = TList(lore.map { TString(it) })
    if (displayName.isNotEmpty()) display["Name"] = TString(displayName)
    display["ShowInHand"] = TByte(1)

    val tag = LinkedHashMap<String, Tag>()
    tag["Damage"] = TInt(0)
    tag["ItemCustomTips"] = TString("")
    tag["ItemExtraID"] = TString("")
    tag["ModId"] = TString("")
    tag["ModItemId"] = TString("")
    tag["RepairCost"] = TInt(0)
    tag["movingBlock"] = comp(
        "name" to TString("minecraft:beehive"),
        "states" to TCompound(LinkedHashMap()),
        "val" to TShort(0),
        "version" to TInt(version)
    )
    tag["movingEntity"] = comp(
        "Occupants" to TList(occupants),
        "id" to TString("Beehive")
    )
    tag["pistonPosX"] = TInt(0)
    tag["pistonPosY"] = TInt(0)
    tag["pistonPosZ"] = TInt(0)
    if (unbreakable) tag["Unbreakable"] = TByte(1)
    tag["display"] = TCompound(display)
    if (ench) tag["ench"] = TList(listOf(comp("id" to TShort(28), "lvl" to TShort(1))))
    tag["minecraft:item_lock"] = TByte(if (itemLock) 1 else 0)
    tag["minecraft:keep_on_death"] = TByte(if (keepOnDeath) 1 else 0)

    val cd = listOf("minecraft:grass_block", "minecraft:dirt",
        "minecraft:beehive", "minecraft:coarse_dirt")
    return comp(
        "Block" to comp(
            "name" to TString("minecraft:moving_block"),
            "states" to TCompound(LinkedHashMap()),
            "val" to TShort(0),
            "version" to TInt(version)
        ),
        "CanDestroy" to TList(cd.map { TString(it) }),
        "CanPlaceOn" to TList(cd.map { TString(it) }),
        "Count" to TByte(count),
        "Damage" to TShort(0),
        "Name" to TString("minecraft:moving_block"),
        "Slot" to TByte(slot),
        "WasPickedUp" to TByte(0),
        "tag" to TCompound(tag)
    )
}

fun makeEquipmentItem(name: String, slot: Int, count: Int, enchPairs: List<Pair<Int, Int>>?,
                      unbreakable: Boolean, keepOnDeath: Boolean, itemLock: Boolean,
                      lore: List<String>?, displayName: String, version: Int): TCompound {
    val tag = LinkedHashMap<String, Tag>()
    tag["Damage"] = TInt(0)
    tag["ItemCustomTips"] = TString("")
    tag["ItemExtraID"] = TString("")
    tag["ModId"] = TString("")
    tag["ModItemId"] = TString("")
    if (unbreakable) tag["Unbreakable"] = TByte(1)
    val display = LinkedHashMap<String, Tag>()
    if (!lore.isNullOrEmpty()) display["Lore"] = TList(lore.map { TString(it) })
    if (displayName.isNotEmpty()) display["Name"] = TString(displayName)
    display["ShowInHand"] = TByte(1)
    tag["display"] = TCompound(display)
    if (!enchPairs.isNullOrEmpty()) {
        tag["ench"] = TList(enchPairs.map {
            comp("id" to TShort(it.first), "lvl" to TShort(it.second),
                 "modEnchant" to TString(""))
        })
    }
    tag["minecraft:item_lock"] = TByte(if (itemLock) 1 else 0)
    tag["minecraft:keep_on_death"] = TByte(if (keepOnDeath) 1 else 0)
    return comp(
        "Count" to TByte(count),
        "Damage" to TShort(0),
        "Name" to TString(name),
        "Slot" to TByte(slot),
        "WasPickedUp" to TByte(0),
        "tag" to TCompound(tag)
    )
}

fun makeTradeItem(name: String, count: Int): TCompound =
    comp("Count" to TByte(count), "Damage" to TShort(0),
         "Name" to TString(name), "WasPickedUp" to TByte(0))

/** 生物装备槽物品（盔甲/主手/副手），支持自定义NBT */
fun makeMobItem(it: JSONObject?): TCompound {
    val o = it ?: JSONObject()
    val snbt = o.asStr("snbt").trim()
    if (snbt.isNotEmpty()) {
        try {
            val tag = parsedToTag(SnbtParser(snbt).parse())
            if (tag is TCompound) return tag
        } catch (_: Exception) {}
    }
    val name = o.asStr("name").trim()
    val count = o.asInt("count", 1)
    if (name.isEmpty()) {
        return comp("Count" to TByte(0), "Damage" to TShort(0),
                    "Name" to TString(""), "WasPickedUp" to TByte(0))
    }
    val ep = ArrayList<Pair<Int, Int>>()
    for (part in o.asStr("ench").split(",")) {
        val p = part.trim()
        if (p.contains(":")) {
            val a = p.substringBefore(":").trim().toIntOrNull()
            val b = p.substringAfter(":").trim().toIntOrNull()
            if (a != null && b != null) ep.add(a to b)
        }
    }
    val il = o.asStr("lore").split("\n").filter { it.isNotBlank() }
    val dn = o.asStr("iname").trim()
    val tag = LinkedHashMap<String, Tag>()
    tag["Damage"] = TInt(0)
    if (ep.isNotEmpty()) tag["ench"] = TList(ep.map { comp("id" to TShort(it.first), "lvl" to TShort(it.second)) })
    val disp = LinkedHashMap<String, Tag>()
    if (dn.isNotEmpty()) disp["Name"] = TString(dn)
    if (il.isNotEmpty()) disp["Lore"] = TList(il.map { TString(it) })
    if (disp.isNotEmpty()) tag["display"] = TCompound(disp)
    return comp("Count" to TByte(count), "Damage" to TShort(0), "Name" to TString(name),
                "WasPickedUp" to TByte(0), "tag" to TCompound(tag))
}

private fun buildEffects(effects: JSONArray?): TList {
    val out = ArrayList<Tag>()
    if (effects != null) for (i in 0 until effects.length()) {
        val e = effects.optJSONObject(i) ?: continue
        val eid = e.asInt("id", 1); val amp = e.asInt("amplifier", 1); val dur = e.asInt("duration", 999999)
        out.add(comp(
            "Ambient" to TByte(0), "Amplifier" to TByte(amp),
            "DisplayOnScreenTextureAnimation" to TByte(0),
            "Duration" to TInt(dur), "DurationEasy" to TInt(dur),
            "DurationHard" to TInt(dur), "DurationNormal" to TInt(dur),
            "Id" to TByte(eid), "ShowParticles" to TByte(0)))
    }
    return TList(out)
}

private fun armorList(armor: JSONArray?): TList {
    val out = ArrayList<Tag>()
    for (i in 0 until 4) {
        val it = armor?.optJSONObject(i)
        out.add(makeMobItem(it))
    }
    return TList(out)
}

/** 把解析后的结构（Map/List/String）转回 Tag，用于嵌入自定义 NBT 物品 */
fun parsedToTag(o: Any?): Tag = when (o) {
    is Tag -> o
    is Map<*, *> -> {
        val m = LinkedHashMap<String, Tag>()
        for ((k, v) in o) m[k.toString()] = parsedToTag(v)
        TCompound(m)
    }
    is List<*> -> TList(o.map { parsedToTag(it) })
    is String -> strToTag(o)
    is Boolean -> TByte(if (o) 1 else 0)
    is Number -> TInt(o.toInt())
    else -> TString(o.toString())
}

private fun strToTag(s: String): Tag {
    val t = s.trim()
    if (t.isNotEmpty() && t.last() in "bsfdlL") {
        val core = t.dropLast(1)
        core.toIntOrNull()?.let {
            return when (t.last()) {
                'b' -> TByte(it)
                's' -> TShort(it)
                'f' -> TFloat(core.toDoubleOrNull() ?: 0.0)
                'd' -> TDouble(core.toDoubleOrNull() ?: 0.0)
                else -> TInt(it)  // l / L
            }
        }
    }
    t.toIntOrNull()?.let { return TInt(it) }
    t.toDoubleOrNull()?.let { return TDouble(it) }
    return TString(s)
}

fun makeRecipe(buyName: String, buyCount: Int, sellTag: Tag,
               maxUses: Int, traderExp: Int): TCompound =
    comp(
        "buyA" to makeTradeItem(buyName, buyCount),
        "buyCountA" to TInt(buyCount),
        "buyCountB" to TInt(0),
        "demand" to TInt(0),
        "maxUses" to TInt(maxUses),
        "priceMultiplierA" to TFloat(0.05),
        "priceMultiplierB" to TFloat(0.0),
        "rewardExp" to TByte(1),
        "sell" to sellTag,
        "tier" to TInt(0),
        "traderExp" to TInt(traderExp),
        "uses" to TInt(0)
    )

private fun chestRoot(count: Int, direction: String, tag: TCompound): TCompound =
    comp(
        "Block" to comp(
            "name" to TString("minecraft:chest"),
            "states" to comp("minecraft:cardinal_direction" to TString(direction)),
            "val" to TShort(2),
            "version" to TInt(MC_VERSION)
        ),
        "Count" to TByte(count),
        "Damage" to TShort(0),
        "Name" to TString("minecraft:chest"),
        "WasPickedUp" to TByte(0),
        "tag" to tag
    )

// ---------- 三种模式 ----------
class Built(val snbt: String, val count: Int)

fun buildCommandBox(cfg: JSONObject): Built {
    val items = cfg.optJSONArray("items") ?: throw IllegalArgumentException("没有格子")
    if (items.length() == 0) throw IllegalArgumentException("没有格子")
    val ticking = cfg.asInt("ticking", 60)
    val tickWorld = cfg.asBool("tick_world", true)
    val radius = cfg.asInt("radius", 4)
    val version = cfg.asInt("version", MC_VERSION)
    val boxName = cfg.asStr("box_name").ifEmpty { "生成盒子" }
    val boxLore = (cfg.asStr("box_lore")).split("\n").filter { it.isNotBlank() }

    val built = ArrayList<Tag>()
    for (i in 0 until items.length()) {
        val it = items.optJSONObject(i) ?: continue
        val iname = it.asStr("iname").trim().ifEmpty { "§a物品${i + 1}" }
        val ilore = it.asStr("lore").split("\n").filter { it.isNotBlank() }
        val cmds = it.optJSONArray("cmds") ?: JSONArray()
        val occupants = ArrayList<Tag>()
        for (j in 0 until cmds.length()) {
            val c = cmds.optJSONObject(j) ?: continue
            val command = c.asStr("command").trim()
            if (command.isEmpty()) continue
            val mname = c.asStr("mname").trim().ifEmpty { "§a命令${j + 1}" }
            occupants.add(makeMinecart(command, mname, ticking, tickWorld, radius))
        }
        if (occupants.isEmpty()) continue
        built.add(makeMovingBlockItem(
            occupants, i, 64, iname, ilore.ifEmpty { null },
            cfg.asBool("unbreakable", true), cfg.asBool("keep_on_death", true),
            cfg.asBool("item_lock", false), cfg.asBool("ench", true), version))
    }
    if (built.isEmpty()) throw IllegalArgumentException("没有有效命令")

    val tag = LinkedHashMap<String, Tag>()
    tag["Damage"] = TInt(0)
    tag["ItemCustomTips"] = TString("")
    tag["ItemExtraID"] = TString("")
    tag["ModId"] = TString("")
    tag["ModItemId"] = TString("")
    tag["CustomName"] = TString(boxName)
    tag["Items"] = TList(built)
    val disp = LinkedHashMap<String, Tag>()
    if (boxLore.isNotEmpty()) disp["Lore"] = TList(boxLore.map { TString(it) })
    disp["Name"] = TString(boxName)
    disp["ShowInHand"] = TByte(1)
    tag["display"] = TCompound(disp)
    if (cfg.asBool("ench", true)) {
        tag["ench"] = TList(listOf(
            comp("id" to TShort(27), "lvl" to TShort(32767)),
            comp("id" to TShort(28), "lvl" to TShort(32767))
        ))
    }
    tag["minecraft:item_lock"] = TByte(if (cfg.asBool("item_lock", false)) 1 else 0)
    tag["minecraft:keep_on_death"] = TByte(if (cfg.asBool("keep_on_death", true)) 1 else 0)

    val root = chestRoot(cfg.asInt("count", 64), cfg.asStr("direction").ifEmpty { "north" },
                         TCompound(tag))
    return Built(root.dump(), built.size)
}

fun buildEquipmentBox(cfg: JSONObject): Built {
    val items = cfg.optJSONArray("items") ?: throw IllegalArgumentException("没有装备格")
    val keep = cfg.asBool("keep_on_death", true)
    val lock = cfg.asBool("item_lock", false)
    val unbr = cfg.asBool("unbreakable", true)
    val version = cfg.asInt("version", MC_VERSION)
    val boxName = cfg.asStr("box_name")

    val built = ArrayList<Tag>()
    for (i in 0 until items.length()) {
        val it = items.optJSONObject(i) ?: continue
        val slotRaw = it.opt("slot")
        val slot = if (slotRaw == null || slotRaw === JSONObject.NULL ||
            slotRaw.toString().isEmpty() || slotRaw.toString() == "null") built.size
            else it.asInt("slot", built.size)
        // 自定义 NBT 物品：从文件读入的完整 NBT，直接嵌入
        val snbt = it.asStr("snbt").trim()
        if (snbt.isNotEmpty()) {
            try {
                val tag = parsedToTag(SnbtParser(snbt).parse())
                if (tag is TCompound) {
                    if (slotRaw != null && slotRaw !== JSONObject.NULL &&
                        slotRaw.toString().isNotEmpty() && slotRaw.toString() != "null") {
                        tag.map["Slot"] = TByte(slot)
                    }
                    val cntRaw = it.opt("count")
                    if (cntRaw != null && cntRaw !== JSONObject.NULL &&
                        cntRaw.toString().isNotEmpty() && cntRaw.toString() != "0") {
                        tag.map["Count"] = TByte(it.asInt("count", 1))
                    }
                    built.add(tag)
                    continue
                }
            } catch (_: Exception) {}
        }
        val name = it.asStr("name").trim()
        if (name.isEmpty()) continue
        val cnt = it.asInt("count", 1)
        val ep = ArrayList<Pair<Int, Int>>()
        it.asStr("ench").split(",").forEach { part ->
            val p = part.trim()
            if (p.contains(":")) {
                val a = p.substringBefore(":").trim().toIntOrNull()
                val b = p.substringAfter(":").trim().toIntOrNull()
                if (a != null && b != null) ep.add(a to b)
            }
        }
        val il = it.asStr("lore").split("\n").filter { it.isNotBlank() }
        built.add(makeEquipmentItem(name, slot, cnt, ep.ifEmpty { null }, unbr, keep, lock,
            il.ifEmpty { null }, it.asStr("iname").trim(), version))
    }
    if (built.isEmpty()) throw IllegalArgumentException("没有有效装备")

    val tag = LinkedHashMap<String, Tag>()
    tag["Items"] = TList(built)
    tag["RepairCost"] = TInt(0)
    if (boxName.isNotEmpty()) tag["display"] = comp("Name" to TString(boxName))
    val root = chestRoot(cfg.asInt("count", 64), cfg.asStr("direction").ifEmpty { "north" },
                         TCompound(tag))
    return Built(root.dump(), built.size)
}

fun buildVillagerBucket(cfg: JSONObject): Built {
    val trades = cfg.optJSONArray("trades") ?: JSONArray()
    val armor = cfg.optJSONArray("armor")
    val mainhand = cfg.optJSONObject("mainhand")
    val offhand = cfg.optJSONObject("offhand")
    val effects = cfg.optJSONArray("effects")
    val entityName = cfg.asStr("entity_name")
    val tradesN = trades.length()
    val armorN = armor?.length() ?: 0
    val mhN = mainhand?.length() ?: 0
    val ohN = offhand?.length() ?: 0
    val effN = effects?.length() ?: 0
    if (tradesN == 0 && armorN == 0 && mhN == 0 && ohN == 0 && effN == 0)
        throw IllegalArgumentException("没有交易、装备或效果")

    val maxUses = cfg.asInt("maxUses", 1000)
    val traderExp = cfg.asInt("traderExp", 1145)
    val tickDelay = cfg.asInt("tickDelay", 250)
    val defs = cfg.asStr("definitions").split(",").map { it.trim() }.filter { it.isNotEmpty() }
        .ifEmpty { listOf("+wandering_trader") }
    val isTrader = defs.any { it.contains("wandering_trader") }
    val bucketName = cfg.asStr("bucket_name").ifEmpty { "minecraft:cod_bucket" }
    val dispName = cfg.asStr("box_name")
    val lore = cfg.asStr("box_lore").split("\n").filter { it.isNotBlank() }

    val recipes = ArrayList<Tag>()
    for (i in 0 until tradesN) {
        val t = trades.optJSONObject(i) ?: continue
        val buy = t.asStr("buy").trim()
        if (buy.isEmpty()) continue
        val buyCount = t.asInt("buy_count", 1)
        val sellSnbt = t.asStr("sell_snbt").trim()
        if (sellSnbt.isNotEmpty()) {
            try {
                val sellTag = parsedToTag(SnbtParser(sellSnbt).parse())
                val sc = t.opt("sell_count")
                val scInt = when (sc) {
                    null, JSONObject.NULL -> 0
                    is Number -> sc.toInt()
                    is String -> sc.trim().toIntOrNull() ?: 0
                    else -> 0
                }
                if (scInt != 0 && sellTag is TCompound) sellTag.map["Count"] = TByte(scInt)
                recipes.add(makeRecipe(buy, buyCount, sellTag, maxUses, traderExp))
                continue
            } catch (_: Exception) {}
        }
        val sell = t.asStr("sell").trim()
        if (sell.isEmpty()) continue
        recipes.add(makeRecipe(buy, buyCount,
            makeTradeItem(sell, t.asInt("sell_count", 64)), maxUses, traderExp))
    }

    val count = cfg.asInt("count", 1)

    // 自定义生物NBT：保留完整实体数据，叠加自定义
    val bioSnbt = cfg.asStr("bio_snbt").trim()
    if (bioSnbt.isNotEmpty()) {
        try {
            val bioTag = parsedToTag(SnbtParser(bioSnbt).parse())
            if (bioTag is TCompound) {
                val tag = bioTag.map
                tag["Armor"] = armorList(armor)
                tag["Mainhand"] = TList(listOf(makeMobItem(mainhand)))
                tag["Offhand"] = TList(listOf(makeMobItem(offhand)))
                if (effN > 0) tag["ActiveEffects"] = buildEffects(effects)
                else tag.remove("ActiveEffects")
                if (defs.isNotEmpty()) tag["definitions"] = TList(defs.map { TString(it) })
                if (entityName.isNotEmpty()) {
                    tag["CustomName"] = TString(entityName)
                    tag["CustomNameVisible"] = TByte(1)
                }
                if (recipes.isNotEmpty()) tag["Offers"] = comp("Recipes" to TList(recipes))
                else tag.remove("Offers")
                val disp = LinkedHashMap<String, Tag>()
                if (lore.isNotEmpty()) disp["Lore"] = TList(lore.map { TString(it) })
                if (dispName.isNotEmpty()) disp["Name"] = TString(dispName)
                if (disp.isNotEmpty()) tag["display"] = TCompound(disp)
                val root = comp(
                    "Count" to TByte(count), "Damage" to TShort(0),
                    "Name" to TString(bucketName), "WasPickedUp" to TByte(0),
                    "tag" to TCompound(tag))
                return Built(root.dump(), maxOf(recipes.size, 1))
            }
        } catch (_: Exception) {}
    }

    // 默认构建（无 bio_snbt）
    val tag = LinkedHashMap<String, Tag>()
    tag["Air"] = TShort(300)
    tag["AppendCustomName"] = TByte(1)
    if (entityName.isNotEmpty()) {
        tag["CustomName"] = TString(entityName)
        tag["CustomNameVisible"] = TByte(1)
    }
    tag["Armor"] = armorList(armor)
    tag["Mainhand"] = TList(listOf(makeMobItem(mainhand)))
    tag["Offhand"] = TList(listOf(makeMobItem(offhand)))
    if (effN > 0) tag["ActiveEffects"] = buildEffects(effects)
    tag["definitions"] = TList(defs.map { TString(it) })
    if (recipes.isNotEmpty()) tag["Offers"] = comp("Recipes" to TList(recipes))
    if (isTrader) {
        tag["ExecuteOnFirstTick"] = TByte(1)
        tag["TickDelay"] = TInt(tickDelay)
        tag["Ticking"] = TByte(0)
        tag["Pos"] = TList(emptyList())
        tag["Tags"] = TList(listOf(TString(""), TString("")))
    }
    val disp = LinkedHashMap<String, Tag>()
    if (lore.isNotEmpty()) disp["Lore"] = TList(lore.map { TString(it) })
    if (dispName.isNotEmpty()) disp["Name"] = TString(dispName)
    if (disp.isNotEmpty()) tag["display"] = TCompound(disp)

    val root = comp(
        "Count" to TByte(count), "Damage" to TShort(0),
        "Name" to TString(bucketName), "WasPickedUp" to TByte(0),
        "tag" to TCompound(tag))
    return Built(root.dump(), maxOf(recipes.size, 1))
}

fun buildSpawner(cfg: JSONObject): Built {
    val ent = cfg.asStr("entity").ifEmpty { "minecraft:wither" }.trim()
    val count = cfg.asInt("count", 64)
    val spawnCount = cfg.asInt("spawn_count", 100)
    val spawnRange = cfg.asInt("spawn_range", 10)
    val maxNearby = cfg.asInt("max_nearby", 10000)
    val reqRange = cfg.asInt("req_range", 100)
    val minDelay = cfg.asInt("min_delay", 1)
    val maxDelay = cfg.asInt("max_delay", 20)
    val delay = cfg.asInt("delay", 2)
    var scaleF: Float? = null
    try { val s = cfg.asStr("scale").trim(); if (s.isNotEmpty()) scaleF = s.toFloat() } catch (_: Exception) {}
    val dispName = cfg.asStr("box_name")
    val lore = cfg.asStr("box_lore").split("\n").filter { it.isNotBlank() }
    val ep = ArrayList<Pair<Int, Int>>()
    for (part in cfg.asStr("ench").split(",")) {
        val p = part.trim()
        if (p.contains(":")) {
            val a = p.substringBefore(":").trim().toIntOrNull()
            val b = p.substringAfter(":").trim().toIntOrNull()
            if (a != null && b != null) ep.add(a to b)
        }
    }

    val tag = LinkedHashMap<String, Tag>()
    tag["Delay"] = TShort(delay)
    tag["EntityIdentifier"] = TString(ent)
    tag["MaxNearbyEntities"] = TShort(maxNearby)
    tag["MaxSpawnDelay"] = TShort(maxDelay)
    tag["MinSpawnDelay"] = TShort(minDelay)
    tag["RequiredPlayerRange"] = TShort(reqRange)
    tag["SpawnCount"] = TShort(spawnCount)
    tag["SpawnRange"] = TShort(spawnRange)
    if (scaleF != null) {
        val sc = scaleF.toDouble()
        tag["DisplayEntityHeight"] = TFloat(sc)
        tag["DisplayEntityScale"] = TFloat(sc)
        tag["DisplayEntityWidth"] = TFloat(sc)
    }
    if (ep.isNotEmpty()) tag["ench"] = TList(ep.map { comp("id" to TShort(it.first), "lvl" to TShort(it.second)) })
    val disp = LinkedHashMap<String, Tag>()
    if (lore.isNotEmpty()) disp["Lore"] = TList(lore.map { TString(it) })
    if (dispName.isNotEmpty()) disp["Name"] = TString(dispName)
    if (disp.isNotEmpty()) tag["display"] = TCompound(disp)
    tag["minecraft:keep_on_death"] = TByte(1)

    val root = comp(
        "Block" to comp("name" to TString("minecraft:mob_spawner"),
                        "version" to TInt(17879555),
                        "states" to TCompound(LinkedHashMap())),
        "Count" to TByte(count), "Damage" to TShort(0),
        "Name" to TString("minecraft:mob_spawner"), "WasPickedUp" to TByte(0),
        "tag" to TCompound(tag))
    return Built(root.dump(), 1)
}

fun buildSnbt(cfg: JSONObject): Built {
    val mode = cfg.asStr("mode").ifEmpty { "command" }.lowercase()
    return when (mode) {
        "command", "cmd", "命令盒", "命令" -> buildCommandBox(cfg)
        "equipment", "equip", "装备盒", "装备" -> buildEquipmentBox(cfg)
        "villager", "bucket", "村民", "鱼桶", "村民鱼桶" -> buildVillagerBucket(cfg)
        "spawner", "刷怪笼", "刷怪" -> buildSpawner(cfg)
        else -> throw IllegalArgumentException("未知模式: $mode")
    }
}
