package com.finngraph.auth.model

import java.time.Duration

data class PasswordAttemptPolicy(
    val limit: Int,
    val window: Duration,
) {
    init {
        require(limit > 0) { "limit은 양수여야 합니다" }
        require(!window.isZero && !window.isNegative) { "window는 양수여야 합니다" }
    }
}

sealed interface PasswordAttemptDecision {

    data class Allowed(val remaining: Int) : PasswordAttemptDecision
    data class Denied(val retryAfter: Duration) : PasswordAttemptDecision
}
