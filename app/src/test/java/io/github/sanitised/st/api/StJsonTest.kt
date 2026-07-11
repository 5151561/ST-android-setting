package io.github.sanitised.st.api

import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * StJson 边缘 case 测试,重点覆盖从 SnakeYAML 迁移到 kotlinx.serialization
 * 时容易出现行为差异的两类:数字分型和特殊字符。
 */
class StJsonTest {

    // ---- 数字分型:对齐旧 SnakeYAML 路径的 Int/Long/BigInteger/Double ----

    @Test
    fun `small integer parses as Int`() {
        val map = StJson.parse("""{"n": 42}""") as Map<*, *>
        assertEquals(42, map["n"])
        assertTrue(map["n"] is Int)
    }

    @Test
    fun `negative integer at Int boundary parses as Int`() {
        val map = StJson.parse("""{"n": ${Int.MIN_VALUE}}""") as Map<*, *>
        assertEquals(Int.MIN_VALUE, map["n"])
    }

    @Test
    fun `integer beyond Int range parses as Long`() {
        val map = StJson.parse("""{"n": 2147483648}""") as Map<*, *>
        assertEquals(2147483648L, map["n"])
        assertTrue(map["n"] is Long)
    }

    @Test
    fun `integer beyond Long range parses as BigInteger`() {
        val map = StJson.parse("""{"n": 9223372036854775808}""") as Map<*, *>
        assertEquals(BigInteger("9223372036854775808"), map["n"])
    }

    @Test
    fun `decimal parses as Double`() {
        val map = StJson.parse("""{"n": 1.0}""") as Map<*, *>
        assertEquals(1.0, map["n"])
        assertTrue(map["n"] is Double)
    }

    @Test
    fun `scientific notation parses by value`() {
        // 与旧 SnakeYAML 路径的已知差异:YAML 1.1 float 正则要求小数点,"1e3" 会落成
        // String;kotlinx 按 JSON 语义解析,整数值科学计数法落 Int,小数落 Double。
        val map = StJson.parse("""{"a": 1e3, "b": 2.5E-2}""") as Map<*, *>
        assertEquals(1000, map["a"])
        assertEquals(0.025, map["b"])
    }

    // ---- 特殊字符 ----

    @Test
    fun `escaped characters in string round-trip`() {
        val raw = "line1\nline2\t\"quoted\" \\backslash\\ 中文 emoji🙂"
        val encoded = StJson.encodeObject("s" to raw)
        val decoded = StJson.parse(encoded) as Map<*, *>
        assertEquals(raw, decoded["s"])
    }

    @Test
    fun `unicode escapes decode`() {
        val map = StJson.parse("""{"s": "中文A"}""") as Map<*, *>
        assertEquals("中文A", map["s"])
    }

    @Test
    fun `numeric-looking string stays String`() {
        val map = StJson.parse("""{"s": "42", "t": "1.0", "u": "true"}""") as Map<*, *>
        assertEquals("42", map["s"])
        assertEquals("1.0", map["t"])
        assertEquals("true", map["u"])
    }

    // ---- 结构与防御式行为 ----

    @Test
    fun `null boolean and nesting parse`() {
        val map = StJson.parse("""{"a": null, "b": true, "c": [1, {"d": false}]}""") as Map<*, *>
        assertNull(map["a"])
        assertEquals(true, map["b"])
        val list = map["c"] as List<*>
        assertEquals(1, list[0])
        assertEquals(false, (list[1] as Map<*, *>)["d"])
    }

    @Test
    fun `blank or invalid input returns null`() {
        assertNull(StJson.parse(""))
        assertNull(StJson.parse("   "))
        assertNull(StJson.parse("{not json"))
    }

    @Test
    fun `encode keeps explicit null and escapes strings`() {
        val json = StJson.encodeObject("a" to null, "b" to "x\"y\n")
        assertEquals("""{"a":null,"b":"x\"y\n"}""", json)
    }

    @Test
    fun `encode of map list tree round-trips`() {
        val tree = mapOf("list" to listOf(1, 2.5, "s", null, mapOf("k" to true)))
        assertEquals(tree, StJson.parse(StJson.encode(tree)))
    }
}
