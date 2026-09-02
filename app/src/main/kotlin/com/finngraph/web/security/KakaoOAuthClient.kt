package com.finngraph.web.security

import com.finngraph.web.common.AuthenticationFailedException
import com.finngraph.web.common.ErrorCode
import com.finngraph.web.common.UpstreamUnavailableException
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.time.Duration

data class KakaoUser(val id: String, val nickname: String?)

interface KakaoOAuthClient {
    fun exchange(code: String): KakaoUser
    fun unlink(kakaoUserId: String)
}

@Component
class RestClientKakaoOAuthClient(private val properties: KakaoProperties) : KakaoOAuthClient {

    private val client = RestClient.builder()
        .requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(CONNECT_TIMEOUT)
                setReadTimeout(READ_TIMEOUT)
            },
        )
        .build()

    override fun exchange(code: String): KakaoUser = requestUser(requestAccessToken(code))

    override fun unlink(kakaoUserId: String) {
        call {
            client.post()
                .uri(properties.unlinkUri)
                .header(HttpHeaders.AUTHORIZATION, "KakaoAK ${properties.adminKey}")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(
                    LinkedMultiValueMap<String, String>().apply {
                        add("target_id_type", "user_id")
                        add("target_id", kakaoUserId)
                    },
                )
                .retrieve()
                .toBodilessEntity()
        }
    }

    private fun requestAccessToken(code: String): String {
        val body = call {
            client.post()
                .uri(properties.tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(
                    LinkedMultiValueMap<String, String>().apply {
                        add("grant_type", "authorization_code")
                        add("client_id", properties.clientId)
                        add("redirect_uri", properties.redirectUri)
                        add("code", code)
                        if (properties.clientSecret.isNotBlank()) add("client_secret", properties.clientSecret)
                    },
                )
                .retrieve()
                .body(Map::class.java)
        }
        return body?.get("access_token")?.toString() ?: throw authFailed()
    }

    private fun requestUser(accessToken: String): KakaoUser {
        val body = call {
            client.get()
                .uri(properties.userUri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .retrieve()
                .body(Map::class.java)
        } ?: throw authFailed()

        val id = body["id"]?.toString() ?: throw authFailed()
        val nickname = ((body["kakao_account"] as? Map<*, *>)?.get("profile") as? Map<*, *>)
            ?.get("nickname")
            ?.toString()
        return KakaoUser(id, nickname)
    }

    private fun <T> call(request: () -> T): T = try {
        request()
    } catch (e: HttpClientErrorException) {
        throw authFailed()
    } catch (e: RestClientException) {
        throw UpstreamUnavailableException(
            ErrorCode.KAKAO_UNAVAILABLE,
            "카카오 서비스를 이용할 수 없습니다.",
            e,
        )
    }

    private fun authFailed() =
        AuthenticationFailedException(ErrorCode.KAKAO_AUTH_FAILED, "카카오 인증에 실패했습니다.")

    private companion object {
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(3)
        val READ_TIMEOUT: Duration = Duration.ofSeconds(5)
    }
}
