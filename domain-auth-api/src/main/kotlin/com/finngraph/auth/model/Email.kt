package com.finngraph.auth.model

@JvmInline
value class Email private constructor(val value: String) {

    companion object {
        const val MAX_LENGTH: Int = 254

        private val FORMAT = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        fun of(raw: String): Email {
            val normalized = raw.trim().lowercase()
            require(normalized.length <= MAX_LENGTH) { "email은 ${MAX_LENGTH}자 이하여야 합니다" }
            require(FORMAT.matches(normalized)) { "email 형식이 올바르지 않습니다" }
            return Email(normalized)
        }
    }
}
