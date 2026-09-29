package com.finngraph.verification

import com.finngraph.auth.DuplicateCredentialException
import com.finngraph.auth.model.Email
import com.finngraph.auth.model.VerificationCode
import com.finngraph.auth.model.VerificationResult
import com.finngraph.composition.account.EmailVerificationComposer
import com.finngraph.composition.account.RateLimitedException
import com.finngraph.composition.port.MailDeliveryFailedException
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

@Component
class EmailVerificationService(
    private val composer: EmailVerificationComposer,
    registry: MeterRegistry,
) {

    private val sendCounters = SEND_RESULTS.associateWith { counter(registry, SEND_METRIC, it) }
    private val confirmCounters = CONFIRM_RESULTS.associateWith { counter(registry, CONFIRM_METRIC, it) }

    fun send(email: Email) {
        try {
            composer.send(email)
        } catch (e: DuplicateCredentialException) {
            sendCounters.getValue(DUPLICATE).increment()
            throw e
        } catch (e: RateLimitedException) {
            sendCounters.getValue(RATE_LIMITED).increment()
            throw e
        } catch (e: MailDeliveryFailedException) {
            sendCounters.getValue(FAILED).increment()
            throw e
        }
        sendCounters.getValue(SENT).increment()
    }

    fun confirm(email: Email, code: VerificationCode, clientIp: String): VerificationResult {
        val result = try {
            composer.confirm(email, code, clientIp)
        } catch (e: RateLimitedException) {
            confirmCounters.getValue(RATE_LIMITED).increment()
            throw e
        }
        val outcome = when (result) {
            is VerificationResult.Verified -> VERIFIED
            is VerificationResult.Mismatch -> MISMATCH
            VerificationResult.Expired -> EXPIRED
        }
        confirmCounters.getValue(outcome).increment()
        return result
    }

    private fun counter(registry: MeterRegistry, metric: String, result: String): Counter =
        Counter.builder(metric).tag("result", result).register(registry)

    companion object {
        const val SEND_METRIC = "email.verification.send"
        const val CONFIRM_METRIC = "email.verification.confirm"

        private const val SENT = "sent"
        private const val DUPLICATE = "duplicate"
        private const val RATE_LIMITED = "rate_limited"
        private const val FAILED = "failed"
        private const val VERIFIED = "verified"
        private const val MISMATCH = "mismatch"
        private const val EXPIRED = "expired"

        private val SEND_RESULTS = listOf(SENT, DUPLICATE, RATE_LIMITED, FAILED)
        private val CONFIRM_RESULTS = listOf(VERIFIED, MISMATCH, EXPIRED, RATE_LIMITED)
    }
}
