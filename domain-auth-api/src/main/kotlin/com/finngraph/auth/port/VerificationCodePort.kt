package com.finngraph.auth.port

import com.finngraph.auth.model.CodePolicy
import com.finngraph.auth.model.Email
import com.finngraph.auth.model.VerificationCode
import com.finngraph.auth.model.VerificationResult
import java.time.Duration

interface VerificationCodePort {

    fun issue(email: Email, code: VerificationCode, policy: CodePolicy)
    fun confirm(email: Email, presented: VerificationCode, policy: CodePolicy): VerificationResult
    fun discard(email: Email, code: VerificationCode): Boolean
    fun consumeVerified(email: Email, grant: String): Boolean
    fun restoreVerified(email: Email, grant: String, ttl: Duration): Boolean
}
