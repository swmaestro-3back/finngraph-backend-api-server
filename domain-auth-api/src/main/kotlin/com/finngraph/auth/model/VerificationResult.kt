package com.finngraph.auth.model

sealed interface VerificationResult {

    data class Verified(val grant: String) : VerificationResult
    data class Mismatch(val remainingAttempts: Int) : VerificationResult
    data object Expired : VerificationResult
}
