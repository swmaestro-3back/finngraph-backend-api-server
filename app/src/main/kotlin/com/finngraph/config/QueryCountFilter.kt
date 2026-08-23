package com.finngraph.config

import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.HandlerMapping

@Component
class QueryCountFilter(private val registry: MeterRegistry) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        RequestQueryCounter.begin()
        try {
            filterChain.doFilter(request, response)
        } finally {
            val count = RequestQueryCounter.end()
            DistributionSummary.builder(METRIC)
                .description("요청당 jOOQ 실행 수")
                .tag("uri", uriPattern(request))
                .register(registry)
                .record(count.toDouble())
        }
    }

    private fun uriPattern(request: HttpServletRequest): String =
        request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) as? String ?: "UNKNOWN"

    companion object {
        const val METRIC = "finngraph.query.count"
    }
}
