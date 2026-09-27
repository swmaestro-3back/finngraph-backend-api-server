package com.finngraph.composition

import com.finngraph.auth.DuplicateCredentialException
import com.finngraph.auth.model.AuthProvider
import com.finngraph.auth.model.CodePolicy
import com.finngraph.auth.model.ConfirmRatePolicy
import com.finngraph.auth.model.Email
import com.finngraph.auth.model.RateLimitDecision
import com.finngraph.auth.model.SendRatePolicy
import com.finngraph.auth.model.ThrottleScope
import com.finngraph.auth.model.VerificationCode
import com.finngraph.auth.model.VerificationResult
import com.finngraph.auth.port.CredentialPort
import com.finngraph.auth.port.RateLimitPort
import com.finngraph.auth.port.VerificationCodePort
import com.finngraph.composition.port.MailSenderPort
import org.springframework.stereotype.Component
import java.time.Duration

class RateLimitedException(val scope: ThrottleScope, val retryAfter: Duration) :
    RuntimeException("요청이 너무 잦습니다: $scope, ${retryAfter.seconds}초 후 재시도")

data class VerificationPolicies(
    val code: CodePolicy,
    val send: SendRatePolicy,
    val confirm: ConfirmRatePolicy,
)

@Component
class EmailVerificationComposer(
    private val rateLimits: RateLimitPort,
    private val credentials: CredentialPort,
    private val codes: VerificationCodePort,
    private val mail: MailSenderPort,
    private val policies: VerificationPolicies,
) {

    fun send(email: Email) {
        reserve(rateLimits.reserveSend(bucketOf(email), policies.send))
        if (credentials.existsByEmail(email)) throw DuplicateCredentialException(AuthProvider.EMAIL)

        val code = VerificationCode.generate()
        codes.issue(email, code, policies.code)
        try {
            mail.send(email.value, SUBJECT, bodyOf(code))
        } catch (e: Exception) {
            codes.discard(email, code)
            throw e
        }
    }

    fun confirm(email: Email, presented: VerificationCode, clientIp: String): VerificationResult {
        reserve(rateLimits.reserveConfirm(clientIp, policies.confirm))
        return codes.confirm(email, presented, policies.code)
    }

    private fun reserve(decision: RateLimitDecision) {
        when (decision) {
            RateLimitDecision.Allowed -> Unit
            is RateLimitDecision.Denied -> throw RateLimitedException(decision.scope, decision.retryAfter)
        }
    }

    private fun bucketOf(email: Email): String =
        email.value.substringBefore('@').substringBefore('+') + "@" + email.value.substringAfter('@')

    private fun bodyOf(code: VerificationCode): String =
        """
        인증 코드는 ${code.value} 입니다.
        ${policies.code.codeTtl.toMinutes()}분 안에 입력해 주세요.
        본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다.
        """.trimIndent()

    companion object {
        const val SUBJECT = "[finngraph] 이메일 인증 코드"
    }
}
