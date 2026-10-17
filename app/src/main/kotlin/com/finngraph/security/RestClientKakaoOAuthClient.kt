package com.finngraph.security

import com.finngraph.composition.port.KakaoAuthFailedException
import com.finngraph.composition.port.KakaoOAuthPort
import com.finngraph.composition.port.KakaoUnavailableException
import com.finngraph.composition.port.KakaoUser
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.time.Duration

@Component
class RestClientKakaoOAuthClient(private val properties: KakaoProperties) : KakaoOAuthPort {

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
        return body?.get("access_token")?.toString() ?: throw KakaoAuthFailedException()
    }

    private fun requestUser(accessToken: String): KakaoUser {
        val body = call {
            client.get()
                .uri(properties.userUri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .retrieve()
                .body(Map::class.java)
        } ?: throw KakaoAuthFailedException()

        val id = body["id"]?.toString() ?: throw KakaoAuthFailedException()
        val nickname = ((body["kakao_account"] as? Map<*, *>)?.get("profile") as? Map<*, *>)
            ?.get("nickname")
            ?.toString()
        return KakaoUser(id, nickname)
    }

    private fun <T> call(request: () -> T): T = try {
        request()
    } catch (e: HttpClientErrorException) {
        throw KakaoAuthFailedException()
    } catch (e: RestClientException) {
        throw KakaoUnavailableException(e)
    }

    private companion object {
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(3)
        val READ_TIMEOUT: Duration = Duration.ofSeconds(5)
    }
}
