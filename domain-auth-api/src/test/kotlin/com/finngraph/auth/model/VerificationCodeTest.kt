package com.finngraph.auth.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VerificationCodeTest {

    @Test
    fun `앞뒤 공백을 제거함`() {
        assertEquals("123456", VerificationCode.of("  123456  ").value)
    }

    @Test
    fun `여섯 자리가 아니면 거부함`() {
        assertFailsWith<IllegalArgumentException> { VerificationCode.of("12345") }
        assertFailsWith<IllegalArgumentException> { VerificationCode.of("1234567") }
        assertFailsWith<IllegalArgumentException> { VerificationCode.of("") }
    }

    @Test
    fun `숫자가 아니면 거부함`() {
        assertFailsWith<IllegalArgumentException> { VerificationCode.of("12345a") }
        assertFailsWith<IllegalArgumentException> { VerificationCode.of("12 456") }
        assertFailsWith<IllegalArgumentException> { VerificationCode.of("１２３４５６") }
    }

    @Test
    fun `생성한 코드는 항상 여섯 자리이고 스스로의 형식 검증을 통과함`() {
        repeat(1000) {
            val generated = VerificationCode.generate()
            assertEquals(VerificationCode.LENGTH, generated.value.length)
            assertEquals(generated, VerificationCode.of(generated.value))
        }
    }

    @Test
    fun `선행 0을 배제하지 않음`() {
        assertEquals("000000", VerificationCode.of("000000").value)
    }

    @Test
    fun `생성 공간이 충분히 넓음`() {
        val generated = List(1000) { VerificationCode.generate().value }
        assertTrue(generated.toSet().size >= 990, "유일값 ${generated.toSet().size}건")
    }
}
