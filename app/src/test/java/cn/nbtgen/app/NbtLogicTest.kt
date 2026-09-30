package cn.nbtgen.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** 与 Python 版对拍：三种模式的生成结果必须完全一致。 */
class NbtLogicTest {

    private fun loadData(): JSONObject {
        val text = javaClass.getResourceAsStream("/expected.json")!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        return JSONObject(text)
    }

    @Test
    fun commandBoxMatchesPython() = check("cmd")

    @Test
    fun equipmentBoxMatchesPython() = check("equip")

    @Test
    fun villagerBucketMatchesPython() = check("vill")

    private fun check(key: String) {
        val data = loadData().getJSONObject(key)
        val cfg = data.getJSONObject("cfg")
        val expected = data.getString("out")
        val actual = buildSnbt(cfg).snbt
        assertEquals("模式 $key 输出与 Python 不一致", expected, actual)
    }

    @Test
    fun parseRoundTripCommand() {
        val data = loadData().getJSONObject("cmd")
        val snbt = data.getString("out")
        val model = ModelParser.parseBoxModel(snbt)
        assertEquals("command", model.getString("mode"))
        assertEquals("§a盒", model.getString("box_name"))
        val items = model.getJSONArray("items")
        assertEquals(1, items.length())
        assertEquals(2, items.getJSONObject(0).getJSONArray("cmds").length())
        assertEquals("/weather clear",
            items.getJSONObject(0).getJSONArray("cmds").getJSONObject(0).getString("command"))
    }

    @Test
    fun parseRoundTripEquipment() {
        val data = loadData().getJSONObject("equip")
        val model = ModelParser.parseBoxModel(data.getString("out"))
        assertEquals("equipment", model.getString("mode"))
        val it = model.getJSONArray("items").getJSONObject(0)
        assertEquals("minecraft:netherite_helmet", it.getString("name"))
        assertEquals("5:3,0:4", it.getString("ench"))
        assertEquals("神器", it.getString("lore").split("\n")[0])
    }

    @Test
    fun parseRoundTripVillager() {
        val data = loadData().getJSONObject("vill")
        val model = ModelParser.parseBoxModel(data.getString("out"))
        assertEquals("villager", model.getString("mode"))
        val t = model.getJSONArray("trades").getJSONObject(0)
        assertEquals("minecraft:dirt", t.getString("buy"))
        assertEquals("minecraft:netherite_block", t.getString("sell"))
        assertEquals(64, t.getInt("sell_count"))
    }
}
