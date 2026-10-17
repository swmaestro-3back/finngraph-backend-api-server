package com.finngraph.user.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NicknameTest {

    @Test
    fun `앞뒤 공백 제거`() {
        assertEquals("하이", Nickname.of("  하이  ").value)
    }

    @Test
    fun `공백을 제외한 길이로 판정`() {
        assertFailsWith<IllegalArgumentException> { Nickname.of(" a") }
        assertFailsWith<IllegalArgumentException> { Nickname.of("   ") }
    }

    @Test
    fun `경계 길이 허용`() {
        assertEquals(Nickname.MIN_LENGTH, Nickname.of("ab").value.length)
        assertEquals(Nickname.MAX_LENGTH, Nickname.of("a".repeat(Nickname.MAX_LENGTH)).value.length)
    }

    @Test
    fun `경계를 벗어나면 거부함`() {
        assertFailsWith<IllegalArgumentException> { Nickname.of("a") }
        assertFailsWith<IllegalArgumentException> { Nickname.of("a".repeat(Nickname.MAX_LENGTH + 1)) }
    }

    @Test
    fun `길이는 바이트가 아니라 문자 기준`() {
        assertEquals("가".repeat(Nickname.MAX_LENGTH), Nickname.of("가".repeat(Nickname.MAX_LENGTH)).value)
    }
}
