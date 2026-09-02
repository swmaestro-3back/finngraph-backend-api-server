package com.finngraph.auth.port

import com.finngraph.auth.model.RotationResult
import java.time.Duration

interface TokenPort {

    fun issue(userId: Long, tokenHash: String, ttl: Duration)
    fun rotate(presentedHash: String, newTokenHash: String, ttl: Duration): RotationResult
    fun revoke(presentedHash: String): Boolean
    fun revokeAllByUserId(userId: Long): Int
}
