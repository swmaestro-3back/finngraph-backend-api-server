package com.finngraph.auth

import com.finngraph.auth.model.Email
import com.finngraph.auth.model.ThrottleScope
import com.finngraph.auth.model.VerificationCode
import com.finngraph.auth.model.VerificationResult
import com.finngraph.composition.account.AccountWriter
import com.finngraph.composition.account.EmailVerificationComposer
import com.finngraph.composition.account.RateLimitedException
import com.finngraph.composition.account.VerificationPolicies
import com.finngraph.composition.port.MailDeliveryFailedException
import com.finngraph.support.CapturingMailConfig
import com.finngraph.support.CapturingMailSender
import com.finngraph.support.TestContainers
import com.finngraph.user.model.Nickname
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@SpringBootTest
@Import(CapturingMailConfig::class)
class EmailVerificationComposerTest {

    @Autowired
    lateinit var composer: EmailVerificationComposer

    @Autowired
    lateinit var accounts: AccountWriter

    @Autowired
    lateinit var mail: CapturingMailSender

    @Autowired
    lateinit var policies: VerificationPolicies

    private val counter = AtomicInteger()

    @Test
    fun `발송하면 메일 본문의 코드로 확인이 성공한다`() {
        val email = uniqueEmail()

        composer.send(email)
        val code = VerificationCode.of(mail.lastCodeFor(email.value))

        assertIs<VerificationResult.Verified>(composer.confirm(email, code, uniqueIp()))
    }

    @Test
    fun `메일은 정본 제목과 코드 유효시간을 담는다`() {
        val email = uniqueEmail()

        composer.send(email)

        val captured = mail.lastTo(email.value)
        assertEquals("[finngraph] 이메일 인증 코드", captured.subject)
        assertTrue(captured.body.contains("${policies.code.codeTtl.toMinutes()}분"), captured.body)
    }

    @Test
    fun `이미 가입된 이메일은 발송하지 않는다`() {
        val email = uniqueEmail()
        accounts.createEmailAccount(email, "hash", Nickname.of("기존"))

        assertThrows(DuplicateCredentialException::class.java) { composer.send(email) }
        assertTrue(mail.sent.none { it.to == email.value })
    }

    @Test
    fun `쿨다운 안의 재발송을 거부한다`() {
        val email = uniqueEmail()
        composer.send(email)

        val denied = assertThrows(RateLimitedException::class.java) { composer.send(email) }

        assertEquals(ThrottleScope.COOLDOWN, denied.scope)
        assertTrue(denied.retryAfter.seconds in 1..policies.send.cooldown.seconds)
    }

    @Test
    fun `서브어드레스가 달라도 같은 버킷으로 제한한다`() {
        val stem = "sub-${counter.incrementAndGet()}-${System.nanoTime()}"
        composer.send(Email.of("$stem+1@test.com"))

        val denied = assertThrows(RateLimitedException::class.java) { composer.send(Email.of("$stem+2@test.com")) }

        assertEquals(ThrottleScope.COOLDOWN, denied.scope)
    }

    @Test
    fun `발송 실패는 코드를 폐기하고 예외를 올린다`() {
        val email = uniqueEmail()
        mail.failNext = true

        assertThrows(MailDeliveryFailedException::class.java) { composer.send(email) }

        assertIs<VerificationResult.Expired>(composer.confirm(email, VerificationCode.of("000000"), uniqueIp()))
    }

    @Test
    fun `확인은 IP 상한을 넘으면 거부한다`() {
        val ip = uniqueIp()
        val email = uniqueEmail()
        repeat(policies.confirm.ipLimit) {
            assertIs<VerificationResult.Expired>(composer.confirm(email, VerificationCode.of("000000"), ip))
        }

        val denied = assertThrows(RateLimitedException::class.java) {
            composer.confirm(email, VerificationCode.of("000000"), ip)
        }

        assertEquals(ThrottleScope.CONFIRM, denied.scope)
    }

    private fun uniqueEmail(): Email = Email.of("ev-${counter.incrementAndGet()}-${System.nanoTime()}@test.com")

    private fun uniqueIp(): String = "10.${counter.incrementAndGet() % 256}.${(System.nanoTime() / 1000) % 256}.${System.nanoTime() % 256}"

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) = TestContainers.register(registry)
    }
}
