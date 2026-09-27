package com.finngraph.auth.model

import java.time.Duration

enum class ThrottleScope { COOLDOWN, EMAIL, GLOBAL, CONFIRM }

sealed interface RateLimitDecision {

    data object Allowed : RateLimitDecision
    data class Denied(val scope: ThrottleScope, val retryAfter: Duration) : RateLimitDecision
}
