package com.finngraph.support

import com.finngraph.auth.model.Email
import com.finngraph.auth.model.VerificationCode
import com.finngraph.auth.model.VerificationResult
import com.finngraph.auth.port.VerificationCodePort
import com.finngraph.composition.VerificationPolicies
import com.finngraph.web.auth.VerificationCookies
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import kotlin.test.assertIs

class AuthFixtures(
    private val rest: TestRestTemplate,
    private val codes: VerificationCodePort,
    private val policies: VerificationPolicies,
) {

    fun grantFor(email: String): String {
        val normalized = Email.of(email)
        val code = VerificationCode.generate()
        codes.issue(normalized, code, policies.code)
        val result = codes.confirm(normalized, code, policies.code)
        return assertIs<VerificationResult.Verified>(result).grant
    }

    fun verifiedSignup(
        email: String,
        password: String = DEFAULT_PASSWORD,
        nickname: String = "테스터",
    ): ResponseEntity<Map<*, *>> = signup(email, password, nickname, grantFor(email))

    fun signup(
        email: String,
        password: String = DEFAULT_PASSWORD,
        nickname: String = "테스터",
        grant: String? = null,
    ): ResponseEntity<Map<*, *>> {
        val headers = HttpHeaders().apply {
            grant?.let { add(HttpHeaders.COOKIE, "$GRANT_COOKIE=$it") }
        }
        val body = mapOf("email" to email, "password" to password, "nickname" to nickname)
        return rest.exchange("/api/v1/auth/signup", HttpMethod.POST, HttpEntity(body, headers), Map::class.java)
    }

    fun login(email: String, password: String = DEFAULT_PASSWORD): ResponseEntity<Map<*, *>> =
        rest.postForEntity("/api/v1/auth/login", mapOf("email" to email, "password" to password), Map::class.java)

    companion object {
        const val DEFAULT_PASSWORD = "password1234"
        const val GRANT_COOKIE = VerificationCookies.COOKIE_NAME
    }
}

@TestConfiguration
class AuthFixturesConfig {

    @Bean
    fun authFixtures(
        rest: TestRestTemplate,
        codes: VerificationCodePort,
        policies: VerificationPolicies,
    ): AuthFixtures = AuthFixtures(rest, codes, policies)
}
