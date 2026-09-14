package com.finngraph.composition.port

data class KakaoUser(val id: String, val nickname: String?)

class KakaoAuthFailedException : RuntimeException("카카오 OAuth 또는 사용자 조회 응답이 유효하지 않음.")

class KakaoUnavailableException(cause: Throwable) :
    RuntimeException("카카오 API 호출 실패함.", cause)

interface KakaoOAuthPort {
    fun exchange(code: String): KakaoUser
    fun unlink(kakaoUserId: String)
}
