package com.finngraph.favorite.model

@JvmInline
value class FolderName private constructor(val value: String) {

    companion object {
        const val MAX_LENGTH: Int = 20

        fun of(raw: String): FolderName {
            val trimmed = raw.trim()
            require(trimmed.isNotEmpty() && trimmed.codePointCount(0, trimmed.length) <= MAX_LENGTH) {
                "폴더 이름은 1~${MAX_LENGTH}자여야 합니다"
            }
            return FolderName(trimmed)
        }
    }
}
