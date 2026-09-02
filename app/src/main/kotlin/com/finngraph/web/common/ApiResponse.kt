package com.finngraph.web.common

import com.fasterxml.jackson.annotation.JsonInclude

data class DataResponse<T>(val data: T)

data class PageResponse<T>(
    val data: List<T>,
    val pagination: Pagination,
)

data class Pagination(
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

data class ErrorResponse(val error: ErrorBody)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorBody(
    val code: String,
    val message: String,
    val details: Map<String, Any>? = null,
)

object ErrorCode {
    const val NEWS_NOT_FOUND = "NEWS_NOT_FOUND"
    const val THEME_NOT_FOUND = "THEME_NOT_FOUND"
    const val STOCK_NOT_FOUND = "STOCK_NOT_FOUND"
    const val NOT_FOUND = "NOT_FOUND"
    const val INVALID_PARAMETER = "INVALID_PARAMETER"
    const val DATABASE_ERROR = "DATABASE_ERROR"
    const val INTERNAL_ERROR = "INTERNAL_ERROR"
    const val UNAUTHORIZED = "UNAUTHORIZED"
    const val TOKEN_EXPIRED = "TOKEN_EXPIRED"
    const val INVALID_CREDENTIALS = "INVALID_CREDENTIALS"
    const val KAKAO_AUTH_FAILED = "KAKAO_AUTH_FAILED"
    const val KAKAO_UNAVAILABLE = "KAKAO_UNAVAILABLE"
    const val FORBIDDEN = "FORBIDDEN"
    const val EMAIL_DUPLICATE = "EMAIL_DUPLICATE"
}
