package com.finngraph.auth.port

import com.finngraph.auth.model.PasswordAttemptDecision
import com.finngraph.auth.model.PasswordAttemptPolicy

interface PasswordAttemptPort {

    fun reserve(userId: Long, policy: PasswordAttemptPolicy): PasswordAttemptDecision
    fun reset(userId: Long)
}
