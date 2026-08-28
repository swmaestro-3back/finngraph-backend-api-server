package com.finngraph.news.model

@JvmInline
value class NewsId(val value: Long) {
    init {
        require(value > 0) { "newsId는 양수여야 합니다: $value" }
    }
}
