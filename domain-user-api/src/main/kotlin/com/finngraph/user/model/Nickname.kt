package com.finngraph.user.model

@JvmInline
value class Nickname private constructor(val value: String) {

    companion object {
        const val MIN_LENGTH: Int = 2
        const val MAX_LENGTH: Int = 20

        fun of(raw: String): Nickname {
            val trimmed = raw.trim()
            require(trimmed.length in MIN_LENGTH..MAX_LENGTH) {
                "nickname은 공백을 제외하고 ${MIN_LENGTH}~${MAX_LENGTH}자여야 합니다."
            }
            return Nickname(trimmed)
        }
    }
}
