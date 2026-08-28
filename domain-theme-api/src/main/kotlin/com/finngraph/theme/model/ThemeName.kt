package com.finngraph.theme.model

@JvmInline
value class ThemeName(val value: String) {
    init {
        require(value.isNotBlank()) { "테마 이름은 비어 있을 수 없습니다" }
    }
}
