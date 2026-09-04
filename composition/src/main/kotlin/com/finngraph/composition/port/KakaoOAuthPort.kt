package com.finngraph.composition.port

data class KakaoUser(val id: String, val nickname: String?)

class KakaoAuthFailedException : RuntimeException("카카오 인증에 실패했습니다.")

class KakaoUnavailableException(cause: Throwable) :
    RuntimeException("카카오 서비스를 이용할 수 없습니다.", cause)

interface KakaoOAuthPort {
    fun exchange(code: String): KakaoUser
    fun unlink(kakaoUserId: String)
}
