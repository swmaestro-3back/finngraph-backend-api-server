package com.finngraph.favorite.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FavoriteTargetTest {

    @Test
    fun `STOCK 키는 앞뒤 공백을 제거하고 대문자로 정규화`() {
        val target = FavoriteTarget.of("STOCK", " 005930 ")
        assertEquals(FavoriteType.STOCK, target.type)
        assertEquals("005930", target.key)
        assertEquals("A12345", FavoriteTarget.of("STOCK", "a12345").key)
    }

    @Test
    fun `STOCK 키는 영숫자 1~20자만 허용`() {
        assertFailsWith<IllegalArgumentException> { FavoriteTarget.of("STOCK", "   ") }
        assertFailsWith<IllegalArgumentException> { FavoriteTarget.of("STOCK", "0059-30") }
        assertFailsWith<IllegalArgumentException> { FavoriteTarget.of("STOCK", "1".repeat(21)) }
        assertEquals("1".repeat(20), FavoriteTarget.of("STOCK", "1".repeat(20)).key)
    }

    @Test
    fun `THEME 키는 양의 정수 문자열로 정규화`() {
        val target = FavoriteTarget.of("THEME", " 17 ")
        assertEquals(FavoriteType.THEME, target.type)
        assertEquals("17", target.key)
        assertEquals("17", FavoriteTarget.of("THEME", "017").key)
        assertEquals("17", FavoriteTarget.theme(17).key)
    }

    @Test
    fun `THEME 키가 양의 정수가 아니면 거부`() {
        assertFailsWith<IllegalArgumentException> { FavoriteTarget.of("THEME", "abc") }
        assertFailsWith<IllegalArgumentException> { FavoriteTarget.of("THEME", "0") }
        assertFailsWith<IllegalArgumentException> { FavoriteTarget.of("THEME", "-3") }
        assertFailsWith<IllegalArgumentException> { FavoriteTarget.of("THEME", "1.5") }
        assertFailsWith<IllegalArgumentException> { FavoriteTarget.theme(0) }
    }

    @Test
    fun `유형은 대문자 STOCK 또는 THEME만 허용`() {
        assertFailsWith<IllegalArgumentException> { FavoriteTarget.of("stock", "005930") }
        assertFailsWith<IllegalArgumentException> { FavoriteTarget.of("NEWS", "1") }
        assertFailsWith<IllegalArgumentException> { FavoriteTarget.of("", "1") }
    }

    @Test
    fun `같은 유형과 키면 동등`() {
        assertEquals(FavoriteTarget.stock("005930"), FavoriteTarget.of("STOCK", "005930"))
        assertEquals(FavoriteTarget.theme(17), FavoriteTarget.of("THEME", "17"))
    }
}
