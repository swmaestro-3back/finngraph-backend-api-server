package com.finngraph.hotthemes

import com.finngraph.composition.HotThemeSnapshot
import com.finngraph.composition.port.HotThemePublisherPort
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Component
class RedisHotThemePublisher(
    private val redis: StringRedisTemplate,
    private val mapper: ObjectMapper,
) : HotThemePublisherPort {

    override fun publish(snapshot: HotThemeSnapshot) {
        val payload = mapOf(
            "tradeDate" to snapshot.tradeDate,
            "generatedAt" to OffsetDateTime.now(SEOUL).truncatedTo(ChronoUnit.SECONDS),
            "count" to snapshot.themes.size,
            "themes" to snapshot.themes,
        )
        redis.opsForValue().set(KEY, mapper.writeValueAsString(payload), TTL)
    }

    companion object {
        const val KEY = "etl:hot-themes"
        val TTL: Duration = Duration.ofHours(48)
        private val SEOUL = ZoneId.of("Asia/Seoul")
    }
}
