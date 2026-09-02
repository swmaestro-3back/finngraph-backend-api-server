package com.finngraph.web.auth

import com.finngraph.support.TestContainers
import com.finngraph.web.common.ErrorCode
import com.finngraph.web.security.KakaoOAuthClient
import com.finngraph.web.security.KakaoUser
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class AuthApiTest {

    @Autowired
    lateinit var rest: TestRestTemplate

    @Autowired
    lateinit var passwordEncoder: PasswordEncoder

    @MockitoBean
    lateinit var kakaoClient: KakaoOAuthClient

    @Test
    fun `비밀번호는 OWASP 권고 파라미터의 Argon2id로 해싱됨.`() {
        val encoded = assertNotNull(passwordEncoder.encode(DEFAULT_PASSWORD))

        assertTrue(encoded.startsWith(ARGON2ID_PREFIX), encoded)
        assertTrue(passwordEncoder.matches(DEFAULT_PASSWORD, encoded))
        assertNotEquals(encoded, passwordEncoder.encode(DEFAULT_PASSWORD))
    }

    @Test
    fun `U-01 이메일 가입은 201과 access토큰 및 refresh쿠키를 반환한다.`() {
        val response = signup("u01@finngraph.test")

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val data = response.data()
        assertNotNull(data["accessToken"])
        assertEquals(true, data["isNewUser"])
        assertEquals(1800, (data["expiresIn"] as Number).toInt())

        val user = data["user"] as Map<*, *>
        assertEquals("테스터", user["nickname"])
        assertEquals("u01@finngraph.test", user["email"])
        assertEquals("EMAIL", user["provider"])

        val cookie = response.headers[HttpHeaders.SET_COOKIE]!!.first { it.startsWith(COOKIE_NAME) }
        assertTrue(cookie.contains("HttpOnly"))
        assertTrue(cookie.contains("SameSite=Strict"))
        assertTrue(cookie.contains("Path=/api/v1/auth"))
    }

    @Test
    fun `멀티바이트 비밀번호로 가입하고 같은 비밀번호로 로그인.`() {
        val password = "한글비밀번호입니다1234567890abcdef"
        assertEquals(HttpStatus.CREATED, signup("u02@finngraph.test", password).statusCode)

        val login = login("u02@finngraph.test", password)
        assertEquals(HttpStatus.OK, login.statusCode)
        assertEquals(false, login.data()["isNewUser"])
    }

    @Test
    fun `가입 검증 실패는 400과 fieldErrors를 줌.`() {
        val response = post(
            "/api/v1/auth/signup",
            mapOf("email" to "not-an-email", "password" to "short", "nickname" to "ا"),
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.INVALID_PARAMETER, response.errorCode())

        val fieldErrors = (response.errorDetails()["fieldErrors"] as Map<*, *>)
        assertEquals(setOf("email", "password", "nickname"), fieldErrors.keys)
    }

    @Test
    fun `이메일 중복 가입은 409 EMAIL_DUPLICATE 반환`() {
        assertEquals(HttpStatus.CREATED, signup("u04@finngraph.test").statusCode)

        val duplicate = signup("u04@finngraph.test")
        assertEquals(HttpStatus.CONFLICT, duplicate.statusCode)
        assertEquals(ErrorCode.EMAIL_DUPLICATE, duplicate.errorCode())
    }

    @Test
    fun `이메일은 대소문자를 정규화해 같은 계정으로 취급(근데 이 부분은 조금 더 고려)`() {
        assertEquals(HttpStatus.CREATED, signup("U05@FinnGraph.Test").statusCode)

        assertEquals(HttpStatus.CONFLICT, signup("u05@finngraph.test").statusCode)
        assertEquals(HttpStatus.OK, login("u05@FINNGRAPH.test").statusCode)
    }

    @Test
    fun `자격 불일치는 401 INVALID_CREDENTIALS로 구분 없이 응답.`() {
        signup("u06@finngraph.test")

        val wrongPassword = login("u06@finngraph.test", "wrongpassword1")
        val unknownEmail = login("nobody@finngraph.test", DEFAULT_PASSWORD)

        assertEquals(HttpStatus.UNAUTHORIZED, wrongPassword.statusCode)
        assertEquals(HttpStatus.UNAUTHORIZED, unknownEmail.statusCode)
        assertEquals(ErrorCode.INVALID_CREDENTIALS, wrongPassword.errorCode())
        assertEquals(ErrorCode.INVALID_CREDENTIALS, unknownEmail.errorCode())
    }

    @Test
    fun `refresh는 재발급된 새 쿠키와 새 액세스토큰을 발급`() {
        val issued = signup("u07@finngraph.test")
        val firstCookie = issued.refreshCookie()

        val refreshed = postWithCookie("/api/v1/auth/refresh", firstCookie)

        assertEquals(HttpStatus.OK, refreshed.statusCode)
        assertNotNull(refreshed.data()["accessToken"])
        assertNotEquals(firstCookie, refreshed.refreshCookie())
    }

    @Test
    fun `카카오는 최초에 가입하고 두 번째부터 로그인.`() {
        given(kakaoClient.exchange(anyString())).willReturn(KakaoUser("kakao-u08", "카카오사용자"))

        val first = post("/api/v1/auth/kakao", mapOf("code" to "authorization-code"))
        assertEquals(HttpStatus.OK, first.statusCode)
        assertEquals(true, first.data()["isNewUser"])
        assertEquals("KAKAO", (first.data()["user"] as Map<*, *>)["provider"])

        val second = post("/api/v1/auth/kakao", mapOf("code" to "authorization-code"))
        assertEquals(HttpStatus.OK, second.statusCode)
        assertEquals(false, second.data()["isNewUser"])
    }

    @Test
    fun `재발급 된 토큰을 재사용하면 401`() {
        val issued = signup("u09@finngraph.test")
        val stale = issued.refreshCookie()
        val rotated = postWithCookie("/api/v1/auth/refresh", stale).refreshCookie()

        val reuse = postWithCookie("/api/v1/auth/refresh", stale)
        assertEquals(HttpStatus.UNAUTHORIZED, reuse.statusCode)
        assertEquals(ErrorCode.UNAUTHORIZED, reuse.errorCode())

        val afterReuse = postWithCookie("/api/v1/auth/refresh", rotated)
        assertEquals(HttpStatus.UNAUTHORIZED, afterReuse.statusCode)
    }

    @Test
    fun `U-09b 같은 토큰을 동시에 제시하면 정확히 하나만 회전한다`() {
        val cookie = signup("u09b@finngraph.test").refreshCookie()
        val pool = Executors.newFixedThreadPool(2)

        val statuses = try {
            pool.invokeAll(
                listOf(
                    Callable { postWithCookie("/api/v1/auth/refresh", cookie).statusCode },
                    Callable { postWithCookie("/api/v1/auth/refresh", cookie).statusCode },
                ),
            ).map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdown()
        }

        assertEquals(1, statuses.count { it == HttpStatus.OK })
    }

    @Test
    fun `없는 refresh 토큰과 쿠키 부재는 모두 401 UNAUTHORIZED`() {
        val unknown = postWithCookie("/api/v1/auth/refresh", "$COOKIE_NAME=not-a-real-token")
        val missing = postWithCookie("/api/v1/auth/refresh", null)

        assertEquals(HttpStatus.UNAUTHORIZED, unknown.statusCode)
        assertEquals(HttpStatus.UNAUTHORIZED, missing.statusCode)
        assertEquals(ErrorCode.UNAUTHORIZED, unknown.errorCode())
    }

    @Test
    fun `재발급된 stale 쿠키로 로그아웃해도 family 전체가 폐기.`() {
        val issued = signup("u11@finngraph.test")
        val stale = issued.refreshCookie()
        val active = postWithCookie("/api/v1/auth/refresh", stale).refreshCookie()

        val logout = rest.exchange(
            "/api/v1/auth/logout",
            HttpMethod.POST,
            HttpEntity<Void>(HttpHeaders().apply { add(HttpHeaders.COOKIE, stale) }),
            Void::class.java,
        )
        assertEquals(HttpStatus.NO_CONTENT, logout.statusCode)

        assertEquals(
            HttpStatus.UNAUTHORIZED,
            postWithCookie("/api/v1/auth/refresh", active).statusCode,
        )
    }

    @Test
    fun `쿠키 없는 로그아웃도 204로 멱등 유지`() {
        val logout = rest.exchange(
            "/api/v1/auth/logout",
            HttpMethod.POST,
            HttpEntity<Void>(HttpHeaders()),
            Void::class.java,
        )
        assertEquals(HttpStatus.NO_CONTENT, logout.statusCode)
    }

    private fun signup(
        email: String,
        password: String = DEFAULT_PASSWORD,
        nickname: String = "테스터",
    ) = post(
        "/api/v1/auth/signup",
        mapOf("email" to email, "password" to password, "nickname" to nickname),
    )

    private fun login(email: String, password: String = DEFAULT_PASSWORD) =
        post("/api/v1/auth/login", mapOf("email" to email, "password" to password))

    private fun post(path: String, body: Map<String, Any?>): ResponseEntity<Map<*, *>> =
        rest.postForEntity(path, body, Map::class.java)

    private fun postWithCookie(path: String, cookie: String?): ResponseEntity<Map<*, *>> =
        rest.exchange(
            path,
            HttpMethod.POST,
            HttpEntity<Void>(HttpHeaders().apply { cookie?.let { add(HttpHeaders.COOKIE, it) } }),
            Map::class.java,
        )

    private fun ResponseEntity<Map<*, *>>.data(): Map<*, *> = body!!["data"] as Map<*, *>

    private fun ResponseEntity<Map<*, *>>.errorCode(): String =
        (body!!["error"] as Map<*, *>)["code"] as String

    private fun ResponseEntity<Map<*, *>>.errorDetails(): Map<*, *> =
        (body!!["error"] as Map<*, *>)["details"] as Map<*, *>

    private fun ResponseEntity<*>.refreshCookie(): String =
        headers[HttpHeaders.SET_COOKIE]!!
            .first { it.startsWith(COOKIE_NAME) }
            .substringBefore(";")

    companion object {
        private const val DEFAULT_PASSWORD = "password1234"
        private const val COOKIE_NAME = "refresh_token="
        private const val ARGON2ID_PREFIX = "{argon2}\$argon2id\$v=19\$m=19456,t=2,p=1\$"

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) = TestContainers.register(registry)
    }
}
