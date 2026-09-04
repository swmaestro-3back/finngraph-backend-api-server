package com.finngraph.web.security

import com.finngraph.composition.RefreshTokenGenerator
import com.finngraph.security.JwtProperties
import com.finngraph.security.JwtTokenService
import com.finngraph.security.KakaoProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.DelegatingPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import tools.jackson.databind.ObjectMapper

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties::class, KakaoProperties::class)
class SecurityConfig {

    @Bean
    fun passwordEncoder(): PasswordEncoder =
        DelegatingPasswordEncoder(
            ARGON2,
            mapOf(
                ARGON2 to Argon2PasswordEncoder(
                    ARGON2_SALT_BYTES,
                    ARGON2_HASH_BYTES,
                    ARGON2_PARALLELISM,
                    ARGON2_MEMORY_KIB,
                    ARGON2_ITERATIONS,
                ),
                BCRYPT to BCryptPasswordEncoder(),
            ),
        )

    @Bean
    fun refreshTokenGenerator(properties: JwtProperties): RefreshTokenGenerator =
        RefreshTokenGenerator(properties.refreshTtl)

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        tokens: JwtTokenService,
        mapper: ObjectMapper,
        environment: Environment,
    ): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling {
                it.authenticationEntryPoint(JsonAuthenticationEntryPoint(mapper))
                it.accessDeniedHandler(JsonAccessDeniedHandler(mapper))
            }
            .authorizeHttpRequests { registry ->
                registry.requestMatchers(HttpMethod.GET, *PUBLIC_GET).permitAll()
                registry.requestMatchers(HttpMethod.POST, *PUBLIC_POST).permitAll()
                if (environment.acceptsProfiles(Profiles.of("local"))) {
                    registry.requestMatchers(HttpMethod.GET, *LOCAL_DOCS).permitAll()
                }
                registry.anyRequest().authenticated()
            }
            .addFilterBefore(
                JwtAuthenticationFilter(tokens),
                UsernamePasswordAuthenticationFilter::class.java,
            )
        return http.build()
    }

    private companion object {
        const val ARGON2 = "argon2"
        const val BCRYPT = "bcrypt"

        const val ARGON2_SALT_BYTES = 16
        const val ARGON2_HASH_BYTES = 32
        const val ARGON2_PARALLELISM = 1
        const val ARGON2_MEMORY_KIB = 19456
        const val ARGON2_ITERATIONS = 2

        val PUBLIC_GET = arrayOf(
            "/api/v1/themes",
            "/api/v1/themes/{name}",
            "/api/v1/themes/{name}/stocks",
            "/api/v1/themes/{name}/news",
            "/api/v1/stocks",
            "/api/v1/stocks/{ticker}",
            "/api/v1/stocks/{ticker}/candles",
            "/api/v1/stocks/{ticker}/investor-flows",
            "/api/v1/stocks/{ticker}/financials",
            "/api/v1/stocks/{ticker}/news",
            "/api/v1/news",
            "/api/v1/news/{id}",
            "/api/v1/news/{id}/companies",
            "/actuator/health",
            "/actuator/health/readiness",
            "/actuator/health/liveness",
            "/actuator/metrics",
            "/actuator/metrics/*",
            "/actuator/prometheus",
        )

        val PUBLIC_POST = arrayOf(
            "/api/v1/auth/kakao",
            "/api/v1/auth/signup",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
        )

        val LOCAL_DOCS = arrayOf(
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs",
            "/v3/api-docs/**",
        )
    }
}
