package com.finngraph.auth.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PasswordPolicyTest {

    @Test
    fun `영문과 숫자를 섞은 8~128자는 통과`() {
        assertNull(PasswordPolicy.violation("abcdefg1"))
        assertNull(PasswordPolicy.violation("a".repeat(127) + "1"))
    }

    @Test
    fun `길이 위반은 가입 때와 같은 문구`() {
        assertEquals("must be 8-128 characters", PasswordPolicy.violation("abc1"))
        assertEquals("must be 8-128 characters", PasswordPolicy.violation("a".repeat(128) + "1"))
    }

    @Test
    fun `공백 문자 숫자 규칙을 순서대로 검사`() {
        assertEquals("must not contain whitespace", PasswordPolicy.violation("abcd efg1"))
        assertEquals("must contain a letter", PasswordPolicy.violation("12345678"))
        assertEquals("must contain a digit", PasswordPolicy.violation("abcdefgh"))
    }

    @Test
    fun `멀티바이트 문자도 문자로 인정`() {
        assertNull(PasswordPolicy.violation("비밀번호입니다1"))
    }
}
