package com.finngraph.auth.adapter

import com.finngraph.auth.model.RotationResult
import com.finngraph.auth.port.TokenPort
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

@Component
class RedisTokenAdapter(
    private val redis: StringRedisTemplate,
) : TokenPort {

    override fun issue(userId: Long, tokenHash: String, ttl: Duration) {
        val familyId = UUID.randomUUID().toString()
        redis.execute(
            ISSUE,
            listOf(tokenKey(tokenHash), familyKey(familyId), userFamilyKey(userId)),
            userId.toString(),
            familyId,
            tokenHash,
            ttl.seconds.toString(),
        )
    }

    override fun rotate(presentedHash: String, newTokenHash: String, ttl: Duration): RotationResult {
        val result = redis.execute(
            ROTATE,
            listOf(tokenKey(presentedHash), tokenKey(newTokenHash)),
            newTokenHash,
            ttl.seconds.toString(),
            TOKEN_PREFIX,
            FAMILY_PREFIX,
            USER_PREFIX,
        ) ?: return RotationResult.Unknown

        val outcome = result.getOrNull(0)?.toString()
        val userId = result.getOrNull(1)?.toString()?.toLongOrNull()

        return when {
            outcome == ROTATED && userId != null -> RotationResult.Rotated(userId)
            outcome == REUSE && userId != null -> RotationResult.ReuseDetected(userId)
            else -> RotationResult.Unknown
        }
    }

    override fun revoke(presentedHash: String): Boolean =
        redis.execute(
            REVOKE,
            listOf(tokenKey(presentedHash)),
            TOKEN_PREFIX,
            FAMILY_PREFIX,
            USER_PREFIX,
        ) == 1L

    override fun revokeAllByUserId(userId: Long): Int =
        (
            redis.execute(
                REVOKE_ALL,
                listOf(userFamilyKey(userId)),
                TOKEN_PREFIX,
                FAMILY_PREFIX,
            ) ?: 0L
            ).toInt()

    private fun tokenKey(tokenHash: String) = TOKEN_PREFIX + tokenHash

    private fun familyKey(familyId: String) = FAMILY_PREFIX + familyId

    private fun userFamilyKey(userId: Long) = "$USER_PREFIX$userId:fam"

    private companion object {
        const val TOKEN_PREFIX = "auth:rt:"
        const val FAMILY_PREFIX = "auth:fam:"
        const val USER_PREFIX = "auth:user:"

        const val ROTATED = "ROTATED"
        const val REUSE = "REUSE"

        val ISSUE: RedisScript<Long> = DefaultRedisScript(
            """
            redis.call('HSET', KEYS[1], 'userId', ARGV[1], 'familyId', ARGV[2], 'status', 'ACTIVE')
            redis.call('EXPIRE', KEYS[1], ARGV[4])
            redis.call('SADD', KEYS[2], ARGV[3])
            redis.call('EXPIRE', KEYS[2], ARGV[4])
            redis.call('SADD', KEYS[3], ARGV[2])
            redis.call('EXPIRE', KEYS[3], ARGV[4])
            return 1
            """.trimIndent(),
            Long::class.java,
        )

        @Suppress("UNCHECKED_CAST")
        val ROTATE: RedisScript<List<Any>> = DefaultRedisScript(
            """
            local data = redis.call('HGETALL', KEYS[1])
            if #data == 0 then return {'UNKNOWN', ''} end
            local entry = {}
            for i = 1, #data, 2 do entry[data[i]] = data[i + 1] end
            local familyKey = ARGV[4] .. entry['familyId']
            local userKey = ARGV[5] .. entry['userId'] .. ':fam'
            if entry['status'] ~= 'ACTIVE' then
                for _, member in ipairs(redis.call('SMEMBERS', familyKey)) do
                    redis.call('DEL', ARGV[3] .. member)
                end
                redis.call('DEL', familyKey)
                redis.call('SREM', userKey, entry['familyId'])
                return {'REUSE', entry['userId']}
            end
            redis.call('HSET', KEYS[1], 'status', 'ROTATED')
            redis.call('HSET', KEYS[2], 'userId', entry['userId'], 'familyId', entry['familyId'], 'status', 'ACTIVE')
            redis.call('EXPIRE', KEYS[2], ARGV[2])
            redis.call('SADD', familyKey, ARGV[1])
            redis.call('EXPIRE', familyKey, ARGV[2])
            redis.call('EXPIRE', userKey, ARGV[2])
            return {'ROTATED', entry['userId']}
            """.trimIndent(),
            List::class.java as Class<List<Any>>,
        )

        val REVOKE: RedisScript<Long> = DefaultRedisScript(
            """
            local data = redis.call('HGETALL', KEYS[1])
            if #data == 0 then return 0 end
            local entry = {}
            for i = 1, #data, 2 do entry[data[i]] = data[i + 1] end
            local familyKey = ARGV[2] .. entry['familyId']
            for _, member in ipairs(redis.call('SMEMBERS', familyKey)) do
                redis.call('DEL', ARGV[1] .. member)
            end
            redis.call('DEL', familyKey, KEYS[1])
            redis.call('SREM', ARGV[3] .. entry['userId'] .. ':fam', entry['familyId'])
            return 1
            """.trimIndent(),
            Long::class.java,
        )

        val REVOKE_ALL: RedisScript<Long> = DefaultRedisScript(
            """
            local families = redis.call('SMEMBERS', KEYS[1])
            for _, family in ipairs(families) do
                local familyKey = ARGV[2] .. family
                for _, member in ipairs(redis.call('SMEMBERS', familyKey)) do
                    redis.call('DEL', ARGV[1] .. member)
                end
                redis.call('DEL', familyKey)
            end
            redis.call('DEL', KEYS[1])
            return #families
            """.trimIndent(),
            Long::class.java,
        )
    }
}
