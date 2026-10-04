package com.finngraph.auth.adapter

import com.finngraph.auth.model.PasswordAttemptDecision
import com.finngraph.auth.model.PasswordAttemptPolicy
import com.finngraph.auth.port.PasswordAttemptPort
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class RedisPasswordAttemptAdapter(
    private val redis: StringRedisTemplate,
) : PasswordAttemptPort {

    override fun reserve(userId: Long, policy: PasswordAttemptPolicy): PasswordAttemptDecision {
        val result = redis.execute(
            RESERVE,
            listOf(key(userId)),
            policy.limit.toString(),
            policy.window.seconds.toString(),
        ) ?: return PasswordAttemptDecision.Denied(policy.window)

        val outcome = result.getOrNull(0)?.toString()
        val value = result.getOrNull(1)?.toString()?.toLongOrNull()

        if (outcome == OK && value != null) return PasswordAttemptDecision.Allowed(value.toInt())

        val retryAfter = if (value != null && value > 0) Duration.ofSeconds(value) else policy.window
        return PasswordAttemptDecision.Denied(retryAfter)
    }

    override fun reset(userId: Long) {
        redis.delete(key(userId))
    }

    private fun key(userId: Long) = "$PREFIX$userId"

    private companion object {
        const val PREFIX = "auth:pwd:try:"
        const val OK = "OK"

        @Suppress("UNCHECKED_CAST")
        val RESERVE: RedisScript<List<Any>> = DefaultRedisScript(
            """
            local count = tonumber(redis.call('GET', KEYS[1]) or '0')
            if count >= tonumber(ARGV[1]) then
                return {'DENIED', tostring(redis.call('TTL', KEYS[1]))}
            end
            count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[2]) end
            return {'OK', tostring(tonumber(ARGV[1]) - count)}
            """.trimIndent(),
            List::class.java as Class<List<Any>>,
        )
    }
}
