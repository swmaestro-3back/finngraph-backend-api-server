package com.finngraph.auth.model

import java.security.SecureRandom

@JvmInline
value class VerificationCode private constructor(val value: String) {

    companion object {
        const val LENGTH: Int = 6

        private const val BOUND: Int = 1_000_000

        private val FORMAT = Regex("[0-9]{$LENGTH}")
        private val RANDOM = SecureRandom()

        fun of(raw: String): VerificationCode {
            val trimmed = raw.trim()
            require(FORMAT.matches(trimmed)) { "인증 코드는 숫자 ${LENGTH}자리여야 합니다" }
            return VerificationCode(trimmed)
        }

        fun generate(): VerificationCode =
            of(RANDOM.nextInt(BOUND).toString().padStart(LENGTH, '0'))
    }
}
