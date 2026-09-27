package com.finngraph.auth.adapter

import com.finngraph.auth.model.ConfirmRatePolicy
import com.finngraph.auth.model.RateLimitDecision
import com.finngraph.auth.model.SendRatePolicy
import com.finngraph.auth.model.ThrottleScope
import com.finngraph.auth.port.RateLimitPort
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class RedisRateLimitAdapter(
    private val redis: StringRedisTemplate,
) : RateLimitPort {

    override fun reserveSend(bucket: String, policy: SendRatePolicy): RateLimitDecision {
        val result = redis.execute(
            RESERVE_SEND,
            listOf(cooldownKey(bucket), emailKey(bucket), GLOBAL_KEY),
            policy.cooldown.seconds.toString(),
            policy.emailLimit.toString(),
            policy.emailWindow.seconds.toString(),
            policy.globalLimit.toString(),
            policy.globalWindow.seconds.toString(),
        ) ?: return RateLimitDecision.Denied(ThrottleScope.COOLDOWN, policy.cooldown)

        return decide(result, policy.cooldown)
    }

    override fun reserveConfirm(clientIp: String, policy: ConfirmRatePolicy): RateLimitDecision {
        val result = redis.execute(
            RESERVE_CONFIRM,
            listOf(confirmKey(clientIp)),
            policy.ipLimit.toString(),
            policy.ipWindow.seconds.toString(),
        ) ?: return RateLimitDecision.Denied(ThrottleScope.CONFIRM, policy.ipWindow)

        return decide(result, policy.ipWindow)
    }

    private fun decide(result: List<Any>, fallback: Duration): RateLimitDecision {
        val outcome = result.getOrNull(0)?.toString()
        if (outcome == OK) return RateLimitDecision.Allowed

        val seconds = result.getOrNull(1)?.toString()?.toLongOrNull()
        val retryAfter = if (seconds != null && seconds > 0) Duration.ofSeconds(seconds) else fallback
        val scope = SCOPES[outcome] ?: ThrottleScope.COOLDOWN

        return RateLimitDecision.Denied(scope, retryAfter)
    }

    private fun cooldownKey(bucket: String) = COOLDOWN_PREFIX + bucket
    private fun emailKey(bucket: String) = EMAIL_PREFIX + bucket
    private fun confirmKey(clientIp: String) = CONFIRM_PREFIX + clientIp

    private companion object {
        const val COOLDOWN_PREFIX = "auth:vrl:cool:"
        const val EMAIL_PREFIX = "auth:vrl:send:"
        const val CONFIRM_PREFIX = "auth:vrl:cfm:"
        const val GLOBAL_KEY = "auth:vrl:global"

        const val OK = "OK"

        val SCOPES = mapOf(
            "COOLDOWN" to ThrottleScope.COOLDOWN,
            "EMAIL" to ThrottleScope.EMAIL,
            "GLOBAL" to ThrottleScope.GLOBAL,
            "CONFIRM" to ThrottleScope.CONFIRM,
        )

        @Suppress("UNCHECKED_CAST")
        val RESERVE_SEND: RedisScript<List<Any>> = DefaultRedisScript(
            """
            if redis.call('EXISTS', KEYS[1]) == 1 then
                return {'COOLDOWN', tostring(redis.call('TTL', KEYS[1]))}
            end
            if tonumber(redis.call('GET', KEYS[2]) or '0') >= tonumber(ARGV[2]) then
                return {'EMAIL', tostring(redis.call('TTL', KEYS[2]))}
            end
            if tonumber(redis.call('GET', KEYS[3]) or '0') >= tonumber(ARGV[4]) then
                return {'GLOBAL', tostring(redis.call('TTL', KEYS[3]))}
            end
            if tonumber(ARGV[1]) > 0 then
                redis.call('SET', KEYS[1], '1', 'EX', ARGV[1])
            end
            if redis.call('INCR', KEYS[2]) == 1 then redis.call('EXPIRE', KEYS[2], ARGV[3]) end
            if redis.call('INCR', KEYS[3]) == 1 then redis.call('EXPIRE', KEYS[3], ARGV[5]) end
            return {'OK', '0'}
            """.trimIndent(),
            List::class.java as Class<List<Any>>,
        )

        @Suppress("UNCHECKED_CAST")
        val RESERVE_CONFIRM: RedisScript<List<Any>> = DefaultRedisScript(
            """
            if tonumber(redis.call('GET', KEYS[1]) or '0') >= tonumber(ARGV[1]) then
                return {'CONFIRM', tostring(redis.call('TTL', KEYS[1]))}
            end
            if redis.call('INCR', KEYS[1]) == 1 then redis.call('EXPIRE', KEYS[1], ARGV[2]) end
            return {'OK', '0'}
            """.trimIndent(),
            List::class.java as Class<List<Any>>,
        )
    }
}
