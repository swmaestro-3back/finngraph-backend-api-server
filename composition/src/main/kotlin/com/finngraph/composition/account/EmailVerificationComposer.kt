package com.finngraph.composition.account

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
import java.time.Clock
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    private val clock: Clock = Clock.system(KST),
) {

    fun send(email: Email) {
        reserve(rateLimits.reserveSend(bucketOf(email), policies.send))
        if (credentials.existsByEmail(email)) throw DuplicateCredentialException(AuthProvider.EMAIL)

        val code = VerificationCode.generate()
        codes.issue(email, code, policies.code)
        try {
            val expiresAt = LocalTime.now(clock).plus(policies.code.codeTtl).format(EXPIRES_AT)
            mail.send(email.value, SUBJECT, textOf(code, expiresAt), htmlOf(code, expiresAt))
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

    private fun textOf(code: VerificationCode, expiresAt: String): String =
        """
        Finngraph 이메일 인증 코드예요: ${code.value}
        ${policies.code.codeTtl.toMinutes()}분 동안 쓸 수 있어요 · ${expiresAt}까지
        가입 화면에 이 코드를 입력하면 이메일 인증이 끝나요.
        본인이 요청하지 않았다면 이 메일은 무시해도 돼요. 코드는 다른 사람에게 알려 주지 마세요.
        """.trimIndent()

    private fun htmlOf(code: VerificationCode, expiresAt: String): String =
        HTML_TEMPLATE
            .replace("{{code}}", code.value)
            .replace("{{ttlMinutes}}", policies.code.codeTtl.toMinutes().toString())
            .replace("{{expiresAt}}", expiresAt)

    companion object {
        const val SUBJECT = "[finngraph] 이메일 인증 코드"

        private val KST: ZoneId = ZoneId.of("Asia/Seoul")
        private val EXPIRES_AT: DateTimeFormatter = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)
        private val HTML_TEMPLATE: String =
            checkNotNull(EmailVerificationComposer::class.java.getResource("/mail/email-verification.html")) {
                "mail/email-verification.html 템플릿이 없습니다"
            }.readText()
    }
}
