package com.finngraph.web.common

import com.finngraph.auth.DuplicateCredentialException
import com.finngraph.auth.model.AuthProvider
import com.finngraph.composition.port.KakaoAuthFailedException
import com.finngraph.composition.port.KakaoUnavailableException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleNotFound(e: ResourceNotFoundException): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.NOT_FOUND, e.code, e.message ?: "요청한 자원이 없습니다")

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

    @ExceptionHandler(AuthenticationFailedException::class)
    fun handleAuthenticationFailed(e: AuthenticationFailedException): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.UNAUTHORIZED, e.code, e.message ?: "인증 실패했습니다.")

    @ExceptionHandler(DuplicateCredentialException::class)
    fun handleDuplicateCredential(e: DuplicateCredentialException): ResponseEntity<ErrorResponse> =
        when (e.provider) {
            AuthProvider.EMAIL -> respond(HttpStatus.CONFLICT, ErrorCode.EMAIL_DUPLICATE, "이미 가입된 이메일 입니다.")
            AuthProvider.KAKAO -> {
                log.error("카카오 자격증명 중복", e)
                respond(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "요청을 처리하지 못 했습니다.")
            }
        }

    @ExceptionHandler(KakaoAuthFailedException::class)
    fun handleKakaoAuthFailed(e: KakaoAuthFailedException): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.UNAUTHORIZED, ErrorCode.KAKAO_AUTH_FAILED, e.message ?: "카카오 인증에 실패했습니다.")

    @ExceptionHandler(KakaoUnavailableException::class)
    fun handleKakaoUnavailable(e: KakaoUnavailableException): ResponseEntity<ErrorResponse> {
        log.error("외부 서비스 호출 실패", e)
        return respond(HttpStatus.BAD_GATEWAY, ErrorCode.KAKAO_UNAVAILABLE, e.message ?: "카카오 서비스를 이용할 수 없습니다.")
    }

    @ExceptionHandler(DataAccessException::class, org.jooq.exception.DataAccessException::class)
    fun handleDatabase(e: Exception): ResponseEntity<ErrorResponse> {
        log.error("DB 조회 실패", e)
        return respond(
            HttpStatus.SERVICE_UNAVAILABLE,
            ErrorCode.DATABASE_ERROR,
            "일시적으로 데이터를 조회할 수 없습니다",
        )
    }

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResource(e: NoResourceFoundException): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "요청한 경로가 없습니다")

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
