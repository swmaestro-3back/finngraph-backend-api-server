package com.finngraph.web.common

import com.finngraph.auth.DuplicateCredentialException
import com.finngraph.auth.model.AuthProvider
import com.finngraph.composition.EmailNotVerifiedException
import com.finngraph.composition.FavoriteTargetNotFoundException
import com.finngraph.composition.RateLimitedException
import com.finngraph.composition.port.KakaoAuthFailedException
import com.finngraph.composition.port.KakaoUnavailableException
import com.finngraph.composition.port.MailDeliveryFailedException
import com.finngraph.web.auth.VerificationFailedException
import com.finngraph.web.favorite.FavoriteLimitExceededException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
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

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(e: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> =
        respond(
            status = HttpStatus.BAD_REQUEST,
            code = ErrorCode.INVALID_PARAMETER,
            message = "요청 본문을 읽을 수 없습니다",
            details = mapOf("fieldErrors" to mapOf("body" to "must be a valid JSON body")),
        )

    @ExceptionHandler(RateLimitedException::class)
    fun handleRateLimited(e: RateLimitedException): ResponseEntity<ErrorResponse> {
        val retryAfterSeconds = maxOf(MIN_RETRY_AFTER_SECONDS, e.retryAfter.seconds)
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, retryAfterSeconds.toString())
            .body(
                ErrorResponse(
                    ErrorBody(
                        ErrorCode.RATE_LIMITED,
                        "요청이 너무 잦습니다. 잠시 후 다시 시도해 주세요.",
                        mapOf("retryAfterSeconds" to retryAfterSeconds),
                    ),
                ),
            )
    }

    @ExceptionHandler(VerificationFailedException::class)
    fun handleVerificationFailed(e: VerificationFailedException): ResponseEntity<ErrorResponse> =
        respond(
            status = HttpStatus.UNAUTHORIZED,
            code = e.code,
            message = when (e.code) {
                ErrorCode.VERIFICATION_CODE_MISMATCH -> "인증 코드가 일치하지 않습니다."
                else -> "인증 코드가 만료됐거나 발급되지 않았습니다."
            },
            details = e.remainingAttempts?.let { mapOf("remainingAttempts" to it) },
        )

    @ExceptionHandler(EmailNotVerifiedException::class)
    fun handleEmailNotVerified(e: EmailNotVerifiedException): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.FORBIDDEN, ErrorCode.EMAIL_NOT_VERIFIED, "이메일 인증이 필요합니다.")

    @ExceptionHandler(MailDeliveryFailedException::class)
    fun handleMailDeliveryFailed(e: MailDeliveryFailedException): ResponseEntity<ErrorResponse> {
        log.error("인증 메일 발송 실패", e)
        return respond(HttpStatus.BAD_GATEWAY, ErrorCode.MAIL_DELIVERY_FAILED, "인증 메일을 보내지 못했습니다.")
    }

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

    @ExceptionHandler(FavoriteTargetNotFoundException::class)
    fun handleFavoriteTargetNotFound(e: FavoriteTargetNotFoundException): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.NOT_FOUND, ErrorCode.FAVORITE_TARGET_NOT_FOUND, "존재하지 않는 종목 또는 테마입니다.")

    @ExceptionHandler(FavoriteLimitExceededException::class)
    fun handleFavoriteLimitExceeded(e: FavoriteLimitExceededException): ResponseEntity<ErrorResponse> =
        respond(
            HttpStatus.CONFLICT,
            ErrorCode.FAVORITE_LIMIT_EXCEEDED,
            e.message ?: "관심 목록 상한을 초과했습니다.",
            mapOf("limit" to e.limit, "count" to e.count),
        )

    @ExceptionHandler(KakaoAuthFailedException::class)
    fun handleKakaoAuthFailed(e: KakaoAuthFailedException): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.UNAUTHORIZED, ErrorCode.KAKAO_AUTH_FAILED, "카카오 인증에 실패했습니다.")

    @ExceptionHandler(KakaoUnavailableException::class)
    fun handleKakaoUnavailable(e: KakaoUnavailableException): ResponseEntity<ErrorResponse> {
        log.error("외부 서비스 호출 실패", e)
        return respond(HttpStatus.BAD_GATEWAY, ErrorCode.KAKAO_UNAVAILABLE, "카카오 서비스를 이용할 수 없습니다.")
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

    private companion object {
        const val MIN_RETRY_AFTER_SECONDS = 1L
    }
}
