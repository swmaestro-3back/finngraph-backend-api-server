package com.finngraph.web

import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(NewsNotFoundException::class)
    fun handleNotFound(e: NewsNotFoundException): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.NOT_FOUND, ErrorCode.NEWS_NOT_FOUND, e.message ?: "뉴스를 찾을 수 없습니다")

    @ExceptionHandler(InvalidParameterException::class)
    fun handleInvalidParameter(e: InvalidParameterException): ResponseEntity<ErrorResponse> =
        respond(
            status = HttpStatus.BAD_REQUEST,
            code = ErrorCode.INVALID_PARAMETER,
            message = e.message ?: "파라미터가 올바르지 않습니다",
            details = mapOf("fieldErrors" to e.fieldErrors),
        )

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(e: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> =
        respond(
            status = HttpStatus.BAD_REQUEST,
            code = ErrorCode.INVALID_PARAMETER,
            message = "${e.name} 값의 형식이 올바르지 않습니다",
            details = mapOf("fieldErrors" to mapOf(e.name to "type mismatch")),
        )

    @ExceptionHandler(DataAccessException::class, org.jooq.exception.DataAccessException::class)
    fun handleDatabase(e: Exception): ResponseEntity<ErrorResponse> {
        log.error("DB 조회 실패", e)
        return respond(
            HttpStatus.SERVICE_UNAVAILABLE,
            ErrorCode.DATABASE_ERROR,
            "일시적으로 데이터를 조회할 수 없습니다",
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ErrorResponse> {
        log.error("처리하지 못한 예외", e)
        return respond(
            HttpStatus.INTERNAL_SERVER_ERROR,
            ErrorCode.INTERNAL_ERROR,
            "요청을 처리하지 못했습니다",
        )
    }

    private fun respond(
        status: HttpStatus,
        code: String,
        message: String,
        details: Map<String, Any>? = null,
    ): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(status).body(ErrorResponse(ErrorBody(code, message, details)))
}
