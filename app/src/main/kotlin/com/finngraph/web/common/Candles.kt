package com.finngraph.web.common

enum class CandleInterval { D, W, M }

data class CandleQuery(val interval: CandleInterval, val limit: Int)

private const val MAX_CANDLE_LIMIT = 2500
private const val DEFAULT_DAILY_LIMIT = 65
private const val DEFAULT_WEEKLY_LIMIT = 52
private const val DEFAULT_MONTHLY_LIMIT = 36

fun validateCandleQuery(period: String, limit: Int?): CandleQuery {
    val parsed = CandleInterval.entries.firstOrNull { it.name == period }
    val errors = buildMap {
        if (parsed == null) put("period", "must be one of D, W, M")
        if (limit != null && (limit < 1 || limit > MAX_CANDLE_LIMIT)) {
            put("limit", "must be between 1 and $MAX_CANDLE_LIMIT")
        }
    }
    if (errors.isNotEmpty()) {
        throw InvalidParameterException("캔들 파라미터가 올바르지 않습니다", errors)
    }
    val interval = checkNotNull(parsed)
    return CandleQuery(interval, limit ?: defaultLimit(interval))
}

private fun defaultLimit(interval: CandleInterval): Int = when (interval) {
    CandleInterval.D -> DEFAULT_DAILY_LIMIT
    CandleInterval.W -> DEFAULT_WEEKLY_LIMIT
    CandleInterval.M -> DEFAULT_MONTHLY_LIMIT
}
