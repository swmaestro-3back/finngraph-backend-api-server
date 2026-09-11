package com.finngraph.theme.model

@JvmInline
value class ThemeId(val value: Long) {
    init {
        require(value > 0) { "themeId는 양수여야 합니다: $value" }
    }
}
