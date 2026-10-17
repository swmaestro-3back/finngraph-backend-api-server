package com.finngraph.web.security

import com.finngraph.security.JwtTokenService
import com.finngraph.security.TokenResolution
import com.finngraph.web.common.ErrorCode
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class JwtAuthenticationFilter(private val tokens: JwtTokenService) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            when (val resolution = tokens.resolve(header.substring(BEARER_PREFIX.length).trim())) {
                is TokenResolution.Valid ->
                    SecurityContextHolder.getContext().authentication =
                        UsernamePasswordAuthenticationToken(
                            resolution.userId,
                            null,
                            listOf(SimpleGrantedAuthority(ROLE_USER)),
                        )

                TokenResolution.Expired -> request.setAttribute(AUTH_ERROR_ATTRIBUTE, ErrorCode.TOKEN_EXPIRED)
                TokenResolution.Invalid -> request.setAttribute(AUTH_ERROR_ATTRIBUTE, ErrorCode.UNAUTHORIZED)
            }
        }
        filterChain.doFilter(request, response)
    }

    companion object {
        const val AUTH_ERROR_ATTRIBUTE = "finngraph.auth.error"
        private const val BEARER_PREFIX = "Bearer "
        private const val ROLE_USER = "ROLE_USER"
    }
}
