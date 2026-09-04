package com.finngraph.web.user

import com.finngraph.support.TestContainers
import com.finngraph.web.common.ErrorCode
import com.finngraph.composition.port.KakaoOAuthPort
import com.finngraph.composition.port.KakaoUser
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class UserApiTest {

    @Autowired
    lateinit var rest: TestRestTemplate

    @MockitoBean
    lateinit var kakaoClient: KakaoOAuthPort

    @Test
    fun `내 프로필은 가입 정보를 그대로 반환`() {
        val token = signup("u12@finngraph.test", nickname = "열둘")

        val response = get("/api/v1/me", token)

        assertEquals(HttpStatus.OK, response.statusCode)
        val me = response.data()
        assertEquals("열둘", me["nickname"])
        assertEquals("u12@finngraph.test", me["email"])
        assertEquals("EMAIL", me["provider"])
        assertNotNull(me["joinedAt"])
    }

    @Test
    fun `토큰이 없거나 깨졌으면 401`() {
        assertEquals(HttpStatus.UNAUTHORIZED, get("/api/v1/me", null).statusCode)

        val broken = get("/api/v1/me", "not-a-real-jwt")
        assertEquals(HttpStatus.UNAUTHORIZED, broken.statusCode)
        assertEquals(ErrorCode.UNAUTHORIZED, broken.errorCode())
    }

    @Test
    fun `닉네임 수정은 갱신된 프로필을 반환하고 조회에도 반영`() {
        val token = signup("u14@finngraph.test", nickname = "수정전")

        val patched = patchNickname(token, "수정후")
        assertEquals(HttpStatus.OK, patched.statusCode)
        assertEquals("수정후", patched.data()["nickname"])

        assertEquals("수정후", get("/api/v1/me", token).data()["nickname"])
    }

    @Test
    fun `닉네임 검증 실패는 400 fieldErrors`() {
        val token = signup("u15@finngraph.test")

        val tooShort = patchNickname(token, " ")
        assertEquals(HttpStatus.BAD_REQUEST, tooShort.statusCode)
        assertEquals(ErrorCode.INVALID_PARAMETER, tooShort.errorCode())
        assertEquals(setOf("nickname"), (tooShort.errorDetails()["fieldErrors"] as Map<*, *>).keys)

        assertEquals(HttpStatus.BAD_REQUEST, patchNickname(token, "가".repeat(21)).statusCode)
    }

    @Test
    fun `탈퇴는 204이고 이후 같은 토큰은 401`() {
        val token = signup("u16@finngraph.test")

        assertEquals(HttpStatus.NO_CONTENT, delete("/api/v1/me", token).statusCode)
        assertEquals(HttpStatus.UNAUTHORIZED, get("/api/v1/me", token).statusCode)
    }

    @Test
    fun `탈퇴하면 refresh 토큰도 폐기`() {
        val signup = signupResponse("u17@finngraph.test")
        val token = signup.accessToken()
        val cookie = signup.refreshCookie()

        delete("/api/v1/me", token)

        val refreshed = rest.exchange(
            "/api/v1/auth/refresh",
            HttpMethod.POST,
            HttpEntity<Void>(HttpHeaders().apply { add(HttpHeaders.COOKIE, cookie) }),
            Map::class.java,
        )
        assertEquals(HttpStatus.UNAUTHORIZED, refreshed.statusCode)
    }

    @Test
    fun `탈퇴하면 자격증명이 함께 사라져 같은 이메일로 재가입`() {
        val token = signup("u18@finngraph.test")
        delete("/api/v1/me", token)

        val rejoined = rest.postForEntity(
            "/api/v1/auth/signup",
            mapOf("email" to "u18@finngraph.test", "password" to PASSWORD, "nickname" to "재가입"),
            Map::class.java,
        )
        assertEquals(HttpStatus.CREATED, rejoined.statusCode)
    }

    @Test
    fun `카카오 계정 탈퇴는 unlink를 호출하고 이메일 계정은 호출하지 않는다`() {
        given(kakaoClient.exchange(anyString())).willReturn(KakaoUser("kakao-u18b", "카카오탈퇴"))
        val kakaoToken = rest.postForEntity(
            "/api/v1/auth/kakao",
            mapOf("code" to "authorization-code"),
            Map::class.java,
        ).accessToken()

        delete("/api/v1/me", kakaoToken)
        verify(kakaoClient).unlink("kakao-u18b")

        val emailToken = signup("u18b@finngraph.test")
        delete("/api/v1/me", emailToken)
        verify(kakaoClient, never()).unlink("u18b@finngraph.test")
    }

    @Test
    fun `카카오 unlink가 실패해도 탈퇴는 성공한다`() {
        given(kakaoClient.exchange(anyString())).willReturn(KakaoUser("kakao-u18c", "실패케이스"))
        doThrow(IllegalStateException("kakao down")).`when`(kakaoClient).unlink(anyString())

        val token = rest.postForEntity(
            "/api/v1/auth/kakao",
            mapOf("code" to "authorization-code"),
            Map::class.java,
        ).accessToken()

        assertEquals(HttpStatus.NO_CONTENT, delete("/api/v1/me", token).statusCode)
        verify(kakaoClient).unlink("kakao-u18c")
        assertEquals(HttpStatus.UNAUTHORIZED, get("/api/v1/me", token).statusCode)
    }

    private fun signup(email: String, nickname: String = "테스터") =
        signupResponse(email, nickname).accessToken()

    private fun signupResponse(email: String, nickname: String = "테스터"): ResponseEntity<Map<*, *>> =
        rest.postForEntity(
            "/api/v1/auth/signup",
            mapOf("email" to email, "password" to PASSWORD, "nickname" to nickname),
            Map::class.java,
        )

    private fun get(path: String, token: String?) =
        rest.exchange(path, HttpMethod.GET, HttpEntity<Void>(bearer(token)), Map::class.java)

    private fun delete(path: String, token: String?) =
        rest.exchange(path, HttpMethod.DELETE, HttpEntity<Void>(bearer(token)), Map::class.java)

    private fun patchNickname(token: String, nickname: String) =
        rest.exchange(
            "/api/v1/me/nickname",
            HttpMethod.PATCH,
            HttpEntity(mapOf("nickname" to nickname), bearer(token)),
            Map::class.java,
        )

    private fun bearer(token: String?) = HttpHeaders().apply {
        token?.let { add(HttpHeaders.AUTHORIZATION, "Bearer $it") }
    }

    private fun ResponseEntity<Map<*, *>>.data(): Map<*, *> = body!!["data"] as Map<*, *>

    private fun ResponseEntity<Map<*, *>>.accessToken(): String = data()["accessToken"] as String

    private fun ResponseEntity<Map<*, *>>.errorCode(): String =
        (body!!["error"] as Map<*, *>)["code"] as String

    private fun ResponseEntity<Map<*, *>>.errorDetails(): Map<*, *> =
        (body!!["error"] as Map<*, *>)["details"] as Map<*, *>

    private fun ResponseEntity<*>.refreshCookie(): String =
        headers[HttpHeaders.SET_COOKIE]!!
            .first { it.startsWith("refresh_token=") }
            .substringBefore(";")

    companion object {
        private const val PASSWORD = "password1234"

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) = TestContainers.register(registry)
    }
}
