package com.finngraph.web.security

import com.finngraph.web.common.ErrorBody
import com.finngraph.web.common.ErrorCode
import com.finngraph.web.common.ErrorResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler
import tools.jackson.databind.ObjectMapper

class JsonAuthenticationEntryPoint(private val mapper: ObjectMapper) : AuthenticationEntryPoint {

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        val code = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE) as? String
            ?: ErrorCode.UNAUTHORIZED
        val message =
            if (code == ErrorCode.TOKEN_EXPIRED) "access토큰이 만료되었습니다."
            else "인증이 필요합니다."
        writeError(mapper, response, HttpServletResponse.SC_UNAUTHORIZED, code, message)
    }
}

class JsonAccessDeniedHandler(private val mapper: ObjectMapper) : AccessDeniedHandler {

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException,
    ) {
        writeError(
            mapper,
            response,
            HttpServletResponse.SC_FORBIDDEN,
            ErrorCode.FORBIDDEN,
            "접근 권한이 없습니다.",
        )
    }
}

private fun writeError(
    mapper: ObjectMapper,
    response: HttpServletResponse,
    status: Int,
    code: String,
    message: String,
) {
    response.status = status
    response.contentType = MediaType.APPLICATION_JSON_VALUE
    response.characterEncoding = Charsets.UTF_8.name()
    mapper.writeValue(response.writer, ErrorResponse(ErrorBody(code, message)))
}
