package com.finngraph.web.user

import com.finngraph.composition.port.KakaoOAuthPort
import com.finngraph.composition.port.KakaoUser
import com.finngraph.support.AuthFixtures
import com.finngraph.support.AuthFixturesConfig
import com.finngraph.support.TestContainers
import com.finngraph.web.common.ErrorCode
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
import org.springframework.context.annotation.Import
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
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(AuthFixturesConfig::class)
class UserApiTest {

    @Autowired
    lateinit var auth: AuthFixtures

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

        assertEquals(HttpStatus.NO_CONTENT, withdraw(token).statusCode)
        assertEquals(HttpStatus.UNAUTHORIZED, get("/api/v1/me", token).statusCode)
    }

    @Test
    fun `탈퇴하면 refresh 토큰도 폐기`() {
        val signup = signupResponse("u17@finngraph.test")

        withdraw(signup.accessToken())

        assertEquals(HttpStatus.UNAUTHORIZED, refresh(signup.refreshCookie()).statusCode)
    }

    @Test
    fun `탈퇴하면 자격증명이 함께 사라져 같은 이메일로 재가입`() {
        val token = signup("u18@finngraph.test")
        withdraw(token)

        val rejoined = auth.verifiedSignup("u18@finngraph.test", nickname = "재가입")
        assertEquals(HttpStatus.CREATED, rejoined.statusCode)
    }

    @Test
    fun `카카오 계정 탈퇴는 비밀번호 없이 unlink를 호출하고 이메일 계정은 호출하지 않는다`() {
        given(kakaoClient.exchange(anyString())).willReturn(KakaoUser("kakao-u18b", "카카오탈퇴"))

        assertEquals(HttpStatus.NO_CONTENT, withdraw(kakaoLogin(), password = null).statusCode)
        verify(kakaoClient).unlink("kakao-u18b")

        withdraw(signup("u18b@finngraph.test"))
        verify(kakaoClient, never()).unlink("u18b@finngraph.test")
    }

    @Test
    fun `카카오 unlink가 실패해도 탈퇴는 성공한다`() {
        given(kakaoClient.exchange(anyString())).willReturn(KakaoUser("kakao-u18c", "실패케이스"))
        doThrow(IllegalStateException("kakao down")).`when`(kakaoClient).unlink(anyString())
        val token = kakaoLogin()

        assertEquals(HttpStatus.NO_CONTENT, withdraw(token, password = null).statusCode)
        verify(kakaoClient).unlink("kakao-u18c")
        assertEquals(HttpStatus.UNAUTHORIZED, get("/api/v1/me", token).statusCode)
    }

    @Test
    fun `카카오 계정은 본문 없이도 탈퇴된다`() {
        given(kakaoClient.exchange(anyString())).willReturn(KakaoUser("kakao-wd1", "본문없음"))
        val token = kakaoLogin()

        val response = rest.exchange(
            "/api/v1/me/withdrawal",
            HttpMethod.POST,
            HttpEntity<Void>(bearer(token)),
            Map::class.java,
        )

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
    }

    @Test
    fun `이메일 계정 탈퇴는 비밀번호가 없으면 400이고 계정은 남는다`() {
        val token = signup("wd2@finngraph.test")

        val response = withdraw(token, password = null)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.INVALID_PARAMETER, response.errorCode())
        assertEquals(setOf("password"), response.fieldErrorKeys())
        assertEquals(HttpStatus.OK, get("/api/v1/me", token).statusCode)
    }

    @Test
    fun `이메일 계정 탈퇴는 비밀번호가 틀리면 400 PASSWORD_MISMATCH이고 계정은 남는다`() {
        val token = signup("wd3@finngraph.test")

        val response = withdraw(token, WRONG_PASSWORD)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.PASSWORD_MISMATCH, response.errorCode())
        assertEquals(4, response.errorDetails()["remainingAttempts"])
        assertEquals(HttpStatus.OK, get("/api/v1/me", token).statusCode)
    }

    @Test
    fun `비밀번호 변경과 탈퇴는 시도 횟수를 공유한다`() {
        val token = signup("wd4@finngraph.test")
        repeat(5) { changePassword(token, WRONG_PASSWORD, NEW_PASSWORD) }

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, withdraw(token).statusCode)
        assertEquals(HttpStatus.OK, get("/api/v1/me", token).statusCode)
    }

    @Test
    fun `DELETE 탈퇴 경로는 제거되어 405`() {
        val token = signup("wd5@finngraph.test")

        val response = delete("/api/v1/me", token)

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.statusCode)
        assertEquals(ErrorCode.METHOD_NOT_ALLOWED, response.errorCode())
        assertEquals(HttpStatus.OK, get("/api/v1/me", token).statusCode)
    }

    @Test
    fun `비밀번호를 바꾸면 새 세션을 주고 새 비밀번호로만 로그인된다`() {
        val signup = signupResponse("pw1@finngraph.test")

        val changed = changePassword(signup.accessToken(), AuthFixtures.DEFAULT_PASSWORD, NEW_PASSWORD)

        assertEquals(HttpStatus.OK, changed.statusCode)
        assertNotNull(changed.data()["accessToken"])
        assertEquals("pw1@finngraph.test", (changed.data()["user"] as Map<*, *>)["email"])
        assertEquals(HttpStatus.UNAUTHORIZED, auth.login("pw1@finngraph.test").statusCode)
        assertEquals(HttpStatus.OK, auth.login("pw1@finngraph.test", NEW_PASSWORD).statusCode)
    }

    @Test
    fun `비밀번호를 바꾸면 기존 refresh는 모두 끊기고 응답의 refresh만 산다`() {
        val signup = signupResponse("pw2@finngraph.test")
        val otherDevice = auth.login("pw2@finngraph.test").refreshCookie()

        val changed = changePassword(signup.accessToken(), AuthFixtures.DEFAULT_PASSWORD, NEW_PASSWORD)

        assertEquals(HttpStatus.UNAUTHORIZED, refresh(signup.refreshCookie()).statusCode)
        assertEquals(HttpStatus.UNAUTHORIZED, refresh(otherDevice).statusCode)
        assertEquals(HttpStatus.OK, refresh(changed.refreshCookie()).statusCode)
    }

    @Test
    fun `현재 비밀번호가 틀리면 400 PASSWORD_MISMATCH와 남은 횟수를 준다`() {
        val token = signup("pw3@finngraph.test")

        val first = changePassword(token, WRONG_PASSWORD, NEW_PASSWORD)
        assertEquals(HttpStatus.BAD_REQUEST, first.statusCode)
        assertEquals(ErrorCode.PASSWORD_MISMATCH, first.errorCode())
        assertEquals(4, first.errorDetails()["remainingAttempts"])
        assertEquals(3, changePassword(token, WRONG_PASSWORD, NEW_PASSWORD).errorDetails()["remainingAttempts"])

        assertEquals(HttpStatus.OK, auth.login("pw3@finngraph.test").statusCode)
    }

    @Test
    fun `다섯 번 틀리면 맞는 비밀번호도 429로 막는다`() {
        val token = signup("pw4@finngraph.test")

        val fifth = (1..5).map { changePassword(token, WRONG_PASSWORD, NEW_PASSWORD) }.last()
        assertEquals(0, fifth.errorDetails()["remainingAttempts"])

        val locked = changePassword(token, AuthFixtures.DEFAULT_PASSWORD, NEW_PASSWORD)
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, locked.statusCode)
        assertEquals(ErrorCode.RATE_LIMITED, locked.errorCode())
        assertTrue((locked.errorDetails()["retryAfterSeconds"] as Number).toLong() > 0)
        assertEquals(HttpStatus.OK, auth.login("pw4@finngraph.test").statusCode)
    }

    @Test
    fun `맞히면 시도 횟수가 초기화된다`() {
        val token = signup("pw5@finngraph.test")
        repeat(3) { changePassword(token, WRONG_PASSWORD, NEW_PASSWORD) }

        val changed = changePassword(token, AuthFixtures.DEFAULT_PASSWORD, NEW_PASSWORD)
        assertEquals(HttpStatus.OK, changed.statusCode)

        val next = changePassword(changed.accessToken(), WRONG_PASSWORD, "another5678")
        assertEquals(4, next.errorDetails()["remainingAttempts"])
    }

    @Test
    fun `입력 검증 실패는 400 fieldErrors이고 시도 횟수를 쓰지 않는다`() {
        val token = signup("pw6@finngraph.test")

        assertEquals(setOf("newPassword"), changePassword(token, AuthFixtures.DEFAULT_PASSWORD, "short1").fieldErrorKeys())
        assertEquals(
            setOf("newPassword"),
            changePassword(token, AuthFixtures.DEFAULT_PASSWORD, AuthFixtures.DEFAULT_PASSWORD).fieldErrorKeys(),
        )
        assertEquals(setOf("currentPassword"), changePassword(token, " ", NEW_PASSWORD).fieldErrorKeys())

        assertEquals(4, changePassword(token, WRONG_PASSWORD, NEW_PASSWORD).errorDetails()["remainingAttempts"])
    }

    @Test
    fun `카카오 계정의 비밀번호 변경은 409 PASSWORD_NOT_SET`() {
        given(kakaoClient.exchange(anyString())).willReturn(KakaoUser("kakao-pw7", "카카오비번"))

        val response = changePassword(kakaoLogin(), "anything1234", NEW_PASSWORD)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.PASSWORD_NOT_SET, response.errorCode())
    }

    private fun signup(email: String, nickname: String = "테스터") =
        signupResponse(email, nickname).accessToken()

    private fun signupResponse(email: String, nickname: String = "테스터"): ResponseEntity<Map<*, *>> =
        auth.verifiedSignup(email, nickname = nickname)

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

    private fun changePassword(token: String, current: String, next: String) =
        rest.exchange(
            "/api/v1/me/password",
            HttpMethod.PATCH,
            HttpEntity(mapOf("currentPassword" to current, "newPassword" to next), bearer(token)),
            Map::class.java,
        )

    private fun withdraw(token: String, password: String? = AuthFixtures.DEFAULT_PASSWORD) =
        rest.exchange(
            "/api/v1/me/withdrawal",
            HttpMethod.POST,
            HttpEntity(mapOf("password" to password), bearer(token)),
            Map::class.java,
        )

    private fun refresh(cookie: String) =
        rest.exchange(
            "/api/v1/auth/refresh",
            HttpMethod.POST,
            HttpEntity<Void>(HttpHeaders().apply { add(HttpHeaders.COOKIE, cookie) }),
            Map::class.java,
        )

    private fun kakaoLogin(): String =
        rest.postForEntity("/api/v1/auth/kakao", mapOf("code" to "authorization-code"), Map::class.java)
            .accessToken()

    private fun bearer(token: String?) = HttpHeaders().apply {
        token?.let { add(HttpHeaders.AUTHORIZATION, "Bearer $it") }
    }

    private fun ResponseEntity<Map<*, *>>.data(): Map<*, *> = body!!["data"] as Map<*, *>

    private fun ResponseEntity<Map<*, *>>.accessToken(): String = data()["accessToken"] as String

    private fun ResponseEntity<Map<*, *>>.errorCode(): String =
        (body!!["error"] as Map<*, *>)["code"] as String

    private fun ResponseEntity<Map<*, *>>.errorDetails(): Map<*, *> =
        (body!!["error"] as Map<*, *>)["details"] as Map<*, *>

    private fun ResponseEntity<Map<*, *>>.fieldErrorKeys(): Set<*> =
        (errorDetails()["fieldErrors"] as Map<*, *>).keys

    private fun ResponseEntity<*>.refreshCookie(): String =
        headers[HttpHeaders.SET_COOKIE]!!
            .first { it.startsWith("refresh_token=") }
            .substringBefore(";")

    companion object {

        const val NEW_PASSWORD = "newpass5678"
        const val WRONG_PASSWORD = "wrongpass99"

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) = TestContainers.register(registry)
    }
}
