package com.finngraph.auth

import com.finngraph.auth.model.Email
import com.finngraph.auth.port.CredentialPort
import com.finngraph.composition.account.AccountWriter
import com.finngraph.support.TestContainers
import com.finngraph.user.model.Nickname
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
class CredentialPortTest {

    @Autowired
    lateinit var credentials: CredentialPort

    @Autowired
    lateinit var accounts: AccountWriter

    @Test
    fun `가입된 이메일은 존재로, 미가입은 부재로 판정한다`() {
        val registered = Email.of("cred-${System.nanoTime()}@test.com")
        accounts.createEmailAccount(registered, "hash", Nickname.of("존재"))

        assertTrue(credentials.existsByEmail(registered))
        assertFalse(credentials.existsByEmail(Email.of("absent-${System.nanoTime()}@test.com")))
    }

    @Test
    fun `이메일 계정은 비밀번호 해시를 조회하고 갱신한다`() {
        val userId = accounts.createEmailAccount(
            Email.of("hash-${System.nanoTime()}@test.com"),
            "old-hash",
            Nickname.of("해시"),
        )

        assertEquals("old-hash", credentials.findPasswordHashByUserId(userId))
        assertTrue(credentials.updatePasswordHash(userId, "new-hash"))
        assertEquals("new-hash", credentials.findPasswordHashByUserId(userId))
    }

    @Test
    fun `카카오 계정은 비밀번호 해시가 없고 갱신도 하지 않는다`() {
        val userId = accounts.linkOrFindKakao("kakao-hash-${System.nanoTime()}", Nickname.of("카카오")).userId

        assertNull(credentials.findPasswordHashByUserId(userId))
        assertFalse(credentials.updatePasswordHash(userId, "new-hash"))
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) = TestContainers.register(registry)
    }
}
