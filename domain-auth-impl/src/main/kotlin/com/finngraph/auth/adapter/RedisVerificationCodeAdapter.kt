package com.finngraph.auth.adapter

import com.finngraph.auth.model.CodePolicy
import com.finngraph.auth.model.Email
import com.finngraph.auth.model.VerificationCode
import com.finngraph.auth.model.VerificationResult
import com.finngraph.auth.port.VerificationCodePort
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64

@Component
class RedisVerificationCodeAdapter(
    private val redis: StringRedisTemplate,
) : VerificationCodePort {

    override fun issue(email: Email, code: VerificationCode, policy: CodePolicy) {
        redis.execute(
            ISSUE,
            listOf(codeKey(email)),
            code.value,
            policy.codeTtl.seconds.toString(),
        )
    }

    override fun confirm(email: Email, presented: VerificationCode, policy: CodePolicy): VerificationResult {
        val grant = newGrant()
        val result = redis.execute(
            CONFIRM,
            listOf(codeKey(email), verifiedKey(email)),
            presented.value,
            policy.maxAttempts.toString(),
            policy.verifiedTtl.seconds.toString(),
            grant,
        ) ?: return VerificationResult.Expired

        val outcome = result.getOrNull(0)?.toString()
        val remaining = result.getOrNull(1)?.toString()?.toIntOrNull() ?: 0

        return when (outcome) {
            VERIFIED -> VerificationResult.Verified(grant)
            MISMATCH -> VerificationResult.Mismatch(remaining)
            else -> VerificationResult.Expired
        }
    }

    override fun discard(email: Email, code: VerificationCode): Boolean =
        redis.execute(DISCARD, listOf(codeKey(email)), code.value) == 1L

    override fun consumeVerified(email: Email, grant: String): Boolean =
        redis.execute(CONSUME_VERIFIED, listOf(verifiedKey(email)), grant) == 1L

    override fun restoreVerified(email: Email, grant: String, ttl: Duration): Boolean =
        redis.execute(RESTORE_VERIFIED, listOf(verifiedKey(email)), grant, ttl.seconds.toString()) == 1L

    private fun newGrant(): String {
        val bytes = ByteArray(GRANT_BYTES)
        RANDOM.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun codeKey(email: Email) = CODE_PREFIX + email.value
    private fun verifiedKey(email: Email) = VERIFIED_PREFIX + email.value

    private companion object {
        const val CODE_PREFIX = "auth:verify:"
        const val VERIFIED_PREFIX = "auth:verified:"

        const val VERIFIED = "VERIFIED"
        const val MISMATCH = "MISMATCH"

        const val GRANT_BYTES = 32

        val RANDOM = SecureRandom()

        val ISSUE: RedisScript<Long> = DefaultRedisScript(
            """
            redis.call('DEL', KEYS[1])
            redis.call('HSET', KEYS[1], 'code', ARGV[1], 'attempts', '0')
            redis.call('EXPIRE', KEYS[1], ARGV[2])
            return 1
            """.trimIndent(),
            Long::class.java,
        )

        @Suppress("UNCHECKED_CAST")
        val CONFIRM: RedisScript<List<Any>> = DefaultRedisScript(
            """
            local stored = redis.call('HGET', KEYS[1], 'code')
            if not stored then return {'EXPIRED', '0'} end
            if stored == ARGV[1] then
                redis.call('DEL', KEYS[1])
                redis.call('SET', KEYS[2], ARGV[4], 'EX', ARGV[3])
                return {'VERIFIED', '0'}
            end
            local attempts = redis.call('HINCRBY', KEYS[1], 'attempts', 1)
            local remaining = tonumber(ARGV[2]) - attempts
            if remaining <= 0 then
                redis.call('DEL', KEYS[1])
                return {'MISMATCH', '0'}
            end
            return {'MISMATCH', tostring(remaining)}
            """.trimIndent(),
            List::class.java as Class<List<Any>>,
        )

        val DISCARD: RedisScript<Long> = DefaultRedisScript(
            """
            if redis.call('HGET', KEYS[1], 'code') == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """.trimIndent(),
            Long::class.java,
        )

        val CONSUME_VERIFIED: RedisScript<Long> = DefaultRedisScript(
            """
            local stored = redis.call('GET', KEYS[1])
            if not stored then return 0 end
            if stored ~= ARGV[1] then return 0 end
            redis.call('DEL', KEYS[1])
            return 1
            """.trimIndent(),
            Long::class.java,
        )

        val RESTORE_VERIFIED: RedisScript<Long> = DefaultRedisScript(
            """
            if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end
            redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2])
            return 1
            """.trimIndent(),
            Long::class.java,
        )
    }
}
