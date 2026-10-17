package com.finngraph.stock.model

@JvmInline
value class Ticker(val value: String) {
    init {
        require(value.isNotBlank()) { "ticker는 비어 있을 수 없습니다" }
    }
}
