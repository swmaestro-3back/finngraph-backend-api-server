package com.finngraph.web.security

import com.finngraph.security.InternalApiProperties
import com.finngraph.web.common.ErrorBody
import com.finngraph.web.common.ErrorCode
import com.finngraph.web.common.ErrorResponse
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest

class InternalTokenFilter(
    private val properties: InternalApiProperties,
    private val mapper: ObjectMapper,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val presented = request.getHeader(TOKEN_HEADER)
        val authorized = properties.token.isNotBlank() &&
            presented != null &&
            MessageDigest.isEqual(properties.token.toByteArray(), presented.toByteArray())

        if (!authorized) {
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.characterEncoding = Charsets.UTF_8.name()
            mapper.writeValue(response.writer, ErrorResponse(ErrorBody(ErrorCode.UNAUTHORIZED, "인증이 필요합니다.")))
            return
        }
        filterChain.doFilter(request, response)
    }

    companion object {
        const val TOKEN_HEADER = "X-Internal-Token"
    }
}
