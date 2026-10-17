package com.finngraph.auth.port

import com.finngraph.auth.model.ConfirmRatePolicy
import com.finngraph.auth.model.RateLimitDecision
import com.finngraph.auth.model.SendRatePolicy

interface RateLimitPort {

    fun reserveSend(bucket: String, policy: SendRatePolicy): RateLimitDecision
    fun reserveConfirm(clientIp: String, policy: ConfirmRatePolicy): RateLimitDecision
}
