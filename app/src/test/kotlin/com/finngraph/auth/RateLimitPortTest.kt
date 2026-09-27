package com.finngraph.auth

import com.finngraph.auth.model.ConfirmRatePolicy
import com.finngraph.auth.model.RateLimitDecision
import com.finngraph.auth.model.SendRatePolicy
import com.finngraph.auth.model.ThrottleScope
import com.finngraph.auth.port.RateLimitPort
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

@SpringBootTest
class RateLimitPortTest {

    @Autowired
    lateinit var limits: RateLimitPort

    @Test
    fun `V-09 쿨다운은 첫 발송 직후 두 번째를 막는다`() {
        val bucket = uniqueBucket()
        val policy = sendPolicy(cooldown = Duration.ofSeconds(60))

        assertIs<RateLimitDecision.Allowed>(limits.reserveSend(bucket, policy))

        val denied = assertIs<RateLimitDecision.Denied>(limits.reserveSend(bucket, policy))
        assertEquals(ThrottleScope.COOLDOWN, denied.scope)
        assertTrue(denied.retryAfter.seconds in 1..60, "retryAfter=${denied.retryAfter}")
    }

    @Test
    fun `V-10 쿨다운이 없으면 이메일 상한까지 허용한다`() {
        val bucket = uniqueBucket()
        val policy = sendPolicy(cooldown = Duration.ZERO, emailLimit = 5)

        repeat(5) { assertIs<RateLimitDecision.Allowed>(limits.reserveSend(bucket, policy)) }

        assertEquals(ThrottleScope.EMAIL, assertIs<RateLimitDecision.Denied>(limits.reserveSend(bucket, policy)).scope)
    }

    @Test
    fun `V-11 이메일 버킷이 다르면 서로 간섭하지 않는다`() {
        val policy = sendPolicy(cooldown = Duration.ZERO, emailLimit = 1)

        assertIs<RateLimitDecision.Allowed>(limits.reserveSend(uniqueBucket(), policy))
        assertIs<RateLimitDecision.Allowed>(limits.reserveSend(uniqueBucket(), policy))
    }

    @Test
    fun `V-12 confirm 축은 IP별로 상한을 건다`() {
        val ip = uniqueBucket()
        val policy = ConfirmRatePolicy(ipLimit = 3, ipWindow = Duration.ofHours(1))

        repeat(3) { assertIs<RateLimitDecision.Allowed>(limits.reserveConfirm(ip, policy)) }

        assertEquals(ThrottleScope.CONFIRM, assertIs<RateLimitDecision.Denied>(limits.reserveConfirm(ip, policy)).scope)
        assertIs<RateLimitDecision.Allowed>(limits.reserveConfirm(uniqueBucket(), policy))
    }

    @Test
    fun `V-13 거부는 창을 연장하지 않는다`() {
        val bucket = uniqueBucket()
        val policy = sendPolicy(cooldown = Duration.ZERO, emailLimit = 1, emailWindow = Duration.ofSeconds(120))

        limits.reserveSend(bucket, policy)
        val first = assertIs<RateLimitDecision.Denied>(limits.reserveSend(bucket, policy)).retryAfter
        repeat(5) { limits.reserveSend(bucket, policy) }
        val last = assertIs<RateLimitDecision.Denied>(limits.reserveSend(bucket, policy)).retryAfter

        assertTrue(last <= first, "거부가 창을 밀었다: $first → $last")
    }

    @Test
    fun `V-14 동시 요청에서도 이메일 상한을 넘지 못한다`() {
        val bucket = uniqueBucket()
        val limit = 5
        val policy = sendPolicy(cooldown = Duration.ZERO, emailLimit = limit)

        val threads = 20
        val ready = CountDownLatch(threads)
        val fire = CountDownLatch(1)
        val allowed = AtomicInteger()
        val pool = Executors.newFixedThreadPool(threads)

        try {
            repeat(threads) {
                pool.submit {
                    ready.countDown()
                    fire.await()
                    if (limits.reserveSend(bucket, policy) is RateLimitDecision.Allowed) allowed.incrementAndGet()
                }
            }
            ready.await(10, TimeUnit.SECONDS)
            fire.countDown()
            pool.shutdown()
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))
        } finally {
            pool.shutdownNow()
        }

        assertEquals(limit, allowed.get(), "발송 상한이 동시 요청에서 뚫렸다")
    }

    private fun sendPolicy(
        cooldown: Duration,
        emailLimit: Int = 5,
        emailWindow: Duration = Duration.ofHours(1),
    ) = SendRatePolicy(
        cooldown = cooldown,
        emailLimit = emailLimit,
        emailWindow = emailWindow,
        globalLimit = 1_000_000,
        globalWindow = Duration.ofHours(1),
    )

    private fun uniqueBucket(): String = "t-${counter.incrementAndGet()}-${System.nanoTime()}"

    private companion object {
        val counter = AtomicInteger()

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) = TestContainers.register(registry)
    }
}
