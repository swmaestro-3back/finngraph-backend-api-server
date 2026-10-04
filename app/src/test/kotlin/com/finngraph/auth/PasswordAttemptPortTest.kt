package com.finngraph.auth

import com.finngraph.auth.model.PasswordAttemptDecision
import com.finngraph.auth.model.PasswordAttemptPolicy
import com.finngraph.auth.port.PasswordAttemptPort
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
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@SpringBootTest
class PasswordAttemptPortTest {

    @Autowired
    lateinit var attempts: PasswordAttemptPort

    @Autowired
    lateinit var configured: PasswordAttemptPolicy

    private val policy = PasswordAttemptPolicy(limit = 5, window = Duration.ofMinutes(15))

    @Test
    fun `설정된 정책은 15분 5회`() {
        assertEquals(PasswordAttemptPolicy(limit = 5, window = Duration.ofMinutes(15)), configured)
    }

    @Test
    fun `한도까지 남은 횟수를 줄이며 허용하고 이후 거부한다`() {
        val userId = uniqueUserId()

        val remaining = (1..5).map {
            assertIs<PasswordAttemptDecision.Allowed>(attempts.reserve(userId, policy)).remaining
        }
        assertEquals(listOf(4, 3, 2, 1, 0), remaining)

        val denied = assertIs<PasswordAttemptDecision.Denied>(attempts.reserve(userId, policy))
        assertTrue(denied.retryAfter.seconds in 1..policy.window.seconds, "retryAfter=${denied.retryAfter}")
    }

    @Test
    fun `reset하면 다시 한도만큼 허용한다`() {
        val userId = uniqueUserId()
        repeat(5) { attempts.reserve(userId, policy) }

        attempts.reset(userId)

        assertEquals(4, assertIs<PasswordAttemptDecision.Allowed>(attempts.reserve(userId, policy)).remaining)
    }

    @Test
    fun `사용자끼리 간섭하지 않는다`() {
        val exhausted = uniqueUserId()
        repeat(5) { attempts.reserve(exhausted, policy) }

        assertIs<PasswordAttemptDecision.Allowed>(attempts.reserve(uniqueUserId(), policy))
    }

    @Test
    fun `거부는 창을 연장하지 않는다`() {
        val userId = uniqueUserId()
        val short = PasswordAttemptPolicy(limit = 1, window = Duration.ofSeconds(120))

        attempts.reserve(userId, short)
        val first = assertIs<PasswordAttemptDecision.Denied>(attempts.reserve(userId, short)).retryAfter
        repeat(5) { attempts.reserve(userId, short) }
        val last = assertIs<PasswordAttemptDecision.Denied>(attempts.reserve(userId, short)).retryAfter

        assertTrue(last <= first, "거부가 창을 밀었다: $first → $last")
    }

    @Test
    fun `동시 요청에서도 창당 허용 횟수가 한도를 넘지 않는다`() {
        val userId = uniqueUserId()
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
                    if (attempts.reserve(userId, policy) is PasswordAttemptDecision.Allowed) allowed.incrementAndGet()
                }
            }
            ready.await(10, TimeUnit.SECONDS)
            fire.countDown()
            pool.shutdown()
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))
        } finally {
            pool.shutdownNow()
        }

        assertEquals(policy.limit, allowed.get(), "동시 요청이 시도 한도를 뚫었다")
    }

    private fun uniqueUserId(): Long = System.nanoTime() + counter.incrementAndGet()

    private companion object {
        val counter = AtomicLong()

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) = TestContainers.register(registry)
    }
}
