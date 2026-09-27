package com.finngraph.web.auth

class VerificationFailedException(val code: String, val remainingAttempts: Int?) :
    RuntimeException("이메일 인증에 실패했습니다: $code")
