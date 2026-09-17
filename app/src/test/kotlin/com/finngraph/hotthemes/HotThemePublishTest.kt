package com.finngraph.hotthemes

import com.finngraph.support.HotThemeSeed
import com.finngraph.support.TestContainers
import io.micrometer.core.instrument.MeterRegistry
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.DockerClientFactory
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
class HotThemePublishTest {

    @Autowired
    lateinit var publishService: HotThemePublishService

    @Autowired
    lateinit var scheduler: HotThemeScheduler

    @Autowired
    lateinit var redis: StringRedisTemplate

    @Autowired
    lateinit var mapper: ObjectMapper

    @Autowired
    lateinit var registry: MeterRegistry

    @Test
    fun `핫테마를 활성 종목 전량과 함께 원자적 SET과 TTL로 발행한다`() {
        HotThemeSeed.seed()
        try {
            val outcome = publishService.publish()

            assertIs<HotThemePublishOutcome.Published>(outcome)
            assertEquals(3, outcome.themes)
            assertEquals(LocalDate.parse("2026-07-31"), outcome.tradeDate)

            val json = assertNotNull(redis.opsForValue().get(RedisHotThemePublisher.KEY))
            val payload = mapper.readValue(json, Map::class.java)

            assertEquals("2026-07-31", payload["tradeDate"])
            assertNotNull(payload["generatedAt"])
            assertEquals(3, payload["count"])

            val themes = payload["themes"] as List<*>
            val names = themes.map { (it as Map<*, *>)["name"] }
            assertEquals(listOf("시드급등테마", "시드상승테마", "시드하락테마"), names)

            val first = themes.first() as Map<*, *>
            assertEquals(9201, (first["id"] as Number).toInt())
            assertEquals(5.0, (first["change"] as Number).toDouble())
            val stocks = (first["stocks"] as List<*>).map { it as Map<*, *> }
            assertEquals(listOf("900001", "900002"), stocks.map { it["ticker"] })
            assertEquals(listOf("시드대장", "시드동료"), stocks.map { it["name"] })

            val ttl: Long = redis.getExpire(RedisHotThemePublisher.KEY)
            assertTrue(ttl in (47 * 3600L)..(48 * 3600L), "TTL이 48h 근방이 아님: $ttl")
        } finally {
            HotThemeSeed.cleanup()
        }
    }

    @Test
    fun `테마가 없으면 발행을 건너뛰고 기존 키를 덮지 않는다`() {
        redis.delete(RedisHotThemePublisher.KEY)

        val outcome = publishService.publish()

        assertIs<HotThemePublishOutcome.Skipped>(outcome)
        assertNull(redis.opsForValue().get(RedisHotThemePublisher.KEY))
    }

    @Test
    fun `전 테마 change가 null이면 발행을 건너뛴다`() {
        HotThemeSeed.seedNullChange()
        redis.delete(RedisHotThemePublisher.KEY)
        try {
            val outcome = publishService.publish()

            assertIs<HotThemePublishOutcome.Skipped>(outcome)
            assertNull(redis.opsForValue().get(RedisHotThemePublisher.KEY))
        } finally {
            HotThemeSeed.cleanup()
        }
    }

    @Test
    fun `redis가 죽어도 스케줄 발행 실패는 밖으로 전파되지 않는다`() {
        HotThemeSeed.seed()
        val docker = DockerClientFactory.instance().client()
        val containerId = TestContainers.redis.containerId
        val failuresBefore = failureCount()

        docker.pauseContainerCmd(containerId).exec()
        try {
            scheduler.publishSafely()
            assertTrue(failureCount() > failuresBefore)
        } finally {
            docker.unpauseContainerCmd(containerId).exec()
            HotThemeSeed.cleanup()
        }
    }

    private fun failureCount() =
        registry.counter("hot.themes.publish", "result", "failure").count()

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) = TestContainers.register(registry)
    }
}
