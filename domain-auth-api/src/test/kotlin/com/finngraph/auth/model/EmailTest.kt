package com.finngraph.auth.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EmailTest {

    @Test
    fun `대소문자와 앞뒤 공백 정규화`() {
        assertEquals("foo@bar.com", Email.of("  Foo@Bar.COM  ").value)
    }

    @Test
    fun `대소문자만 다른 주소는 같은 값이 됨`() {
        assertEquals(Email.of("foo@bar.com"), Email.of("FOO@BAR.COM"))
    }

    @Test
    fun `형식이 어긋나면 거부함`() {
        assertFailsWith<IllegalArgumentException> { Email.of("foo") }
        assertFailsWith<IllegalArgumentException> { Email.of("foo@bar") }
        assertFailsWith<IllegalArgumentException> { Email.of("foo bar@baz.com") }
        assertFailsWith<IllegalArgumentException> { Email.of("@bar.com") }
        assertFailsWith<IllegalArgumentException> { Email.of("   ") }
    }

    @Test
    fun `RFC 5321 상한을 넘으면 거부함`() {
        val local = "a".repeat(Email.MAX_LENGTH - "@bar.com".length)
        assertEquals(Email.MAX_LENGTH, Email.of("$local@bar.com").value.length)
        assertFailsWith<IllegalArgumentException> { Email.of("a$local@bar.com") }
    }
}
