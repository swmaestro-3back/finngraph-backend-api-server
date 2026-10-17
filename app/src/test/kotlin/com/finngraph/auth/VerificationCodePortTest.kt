package com.finngraph.auth

import com.finngraph.auth.model.CodePolicy
import com.finngraph.auth.model.Email
import com.finngraph.auth.model.VerificationCode
import com.finngraph.auth.model.VerificationResult
import com.finngraph.auth.port.VerificationCodePort
import com.finngraph.support.TestContainers
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@SpringBootTest
class VerificationCodePortTest {

    @Autowired
    lateinit var codes: VerificationCodePort

    private val policy = CodePolicy(
        codeTtl = Duration.ofMinutes(5),
        maxAttempts = 5,
        verifiedTtl = Duration.ofMinutes(30),
    )

    @Test
    fun `V-01 발급한 코드로 확인하면 grant를 돌려준다`() {
        val email = uniqueEmail()
        val code = VerificationCode.generate()

        codes.issue(email, code, policy)
        val result = codes.confirm(email, code, policy)

        val verified = assertIs<VerificationResult.Verified>(result)
        assertTrue(verified.grant.isNotBlank())
    }

    @Test
    fun `V-02 발급하지 않은 주소는 만료로 응답한다`() {
        assertIs<VerificationResult.Expired>(
            codes.confirm(uniqueEmail(), VerificationCode.generate(), policy),
        )
    }

    @Test
    fun `V-03 불일치는 남은 시도를 줄이고 상한에서 코드를 폐기한다`() {
        val email = uniqueEmail()
        codes.issue(email, VerificationCode.of("111111"), policy)
        val wrong = VerificationCode.of("222222")

        assertEquals(4, assertIs<VerificationResult.Mismatch>(codes.confirm(email, wrong, policy)).remainingAttempts)
        assertEquals(3, assertIs<VerificationResult.Mismatch>(codes.confirm(email, wrong, policy)).remainingAttempts)
        assertEquals(2, assertIs<VerificationResult.Mismatch>(codes.confirm(email, wrong, policy)).remainingAttempts)
        assertEquals(1, assertIs<VerificationResult.Mismatch>(codes.confirm(email, wrong, policy)).remainingAttempts)
        assertEquals(0, assertIs<VerificationResult.Mismatch>(codes.confirm(email, wrong, policy)).remainingAttempts)

        assertIs<VerificationResult.Expired>(codes.confirm(email, VerificationCode.of("111111"), policy))
    }

    @Test
    fun `V-04 재발급은 이전 코드를 무효화하고 시도 횟수를 되돌린다`() {
        val email = uniqueEmail()
        val first = VerificationCode.of("111111")
        val second = VerificationCode.of("333333")
        val wrong = VerificationCode.of("222222")

        codes.issue(email, first, policy)
        repeat(3) { codes.confirm(email, wrong, policy) }

        codes.issue(email, second, policy)

        assertIs<VerificationResult.Mismatch>(codes.confirm(email, first, policy)).also {
            assertEquals(4, it.remainingAttempts)
        }
        assertIs<VerificationResult.Verified>(codes.confirm(email, second, policy))
    }

    @Test
    fun `V-05 grant가 일치할 때만 마커를 소모한다`() {
        val email = uniqueEmail()
        val code = VerificationCode.generate()
        codes.issue(email, code, policy)
        val grant = assertIs<VerificationResult.Verified>(codes.confirm(email, code, policy)).grant

        assertFalse(codes.consumeVerified(email, "wrong-grant"))
        assertTrue(codes.consumeVerified(email, grant), "틀린 grant가 마커를 태워버렸다")
        assertFalse(codes.consumeVerified(email, grant))
    }

    @Test
    fun `V-06 폐기는 내가 발급한 코드일 때만 지운다`() {
        val email = uniqueEmail()
        val mine = VerificationCode.of("111111")
        val newer = VerificationCode.of("333333")

        codes.issue(email, mine, policy)
        codes.issue(email, newer, policy)

        assertFalse(codes.discard(email, mine), "이미 교체된 코드를 지웠다")
        assertIs<VerificationResult.Verified>(codes.confirm(email, newer, policy))
    }

    @Test
    fun `V-07 복원은 마커가 없을 때만 동작한다`() {
        val email = uniqueEmail()
        val ttl = Duration.ofMinutes(30)

        assertTrue(codes.restoreVerified(email, "grant-a", ttl))
        assertFalse(codes.restoreVerified(email, "grant-b", ttl))
        assertTrue(codes.consumeVerified(email, "grant-a"))
    }

    @Test
    fun `V-08 동시 요청에서도 시도 상한을 넘지 못한다`() {
        val email = uniqueEmail()
        codes.issue(email, VerificationCode.of("111111"), policy)
        val wrong = VerificationCode.of("222222")

        val threads = 20
        val ready = CountDownLatch(threads)
        val fire = CountDownLatch(1)
        val mismatches = AtomicInteger()
        val pool = Executors.newFixedThreadPool(threads)

        try {
            repeat(threads) {
                pool.submit {
                    ready.countDown()
                    fire.await()
                    if (codes.confirm(email, wrong, policy) is VerificationResult.Mismatch) {
                        mismatches.incrementAndGet()
                    }
                }
            }
            ready.await(10, TimeUnit.SECONDS)
            fire.countDown()
            pool.shutdown()
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))
        } finally {
            pool.shutdownNow()
        }

        assertEquals(policy.maxAttempts, mismatches.get(), "시도 상한이 동시 요청에서 뚫렸다")
    }

    private fun uniqueEmail(): Email = Email.of("v-${counter.incrementAndGet()}-${System.nanoTime()}@test.com")

    private companion object {
        val counter = AtomicInteger()

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) = TestContainers.register(registry)
    }
}
