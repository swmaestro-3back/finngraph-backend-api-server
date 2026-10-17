package com.finngraph.auth.model

object PasswordPolicy {
    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 128

    fun violation(raw: String): String? = when {
        raw.length !in MIN_LENGTH..MAX_LENGTH -> "must be $MIN_LENGTH-$MAX_LENGTH characters"
        raw.any { it.isWhitespace() } -> "must not contain whitespace"
        raw.none { it.isLetter() } -> "must contain a letter"
        raw.none { it.isDigit() } -> "must contain a digit"
        else -> null
    }
}
