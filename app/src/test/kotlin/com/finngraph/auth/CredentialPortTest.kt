package com.finngraph.auth

import com.finngraph.auth.model.Email
import com.finngraph.auth.port.CredentialPort
import com.finngraph.composition.AccountWriter
import com.finngraph.support.TestContainers
import com.finngraph.user.model.Nickname
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import kotlin.test.assertFalse
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

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) = TestContainers.register(registry)
    }
}
