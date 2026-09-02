package com.finngraph.auth.port

import com.finngraph.auth.model.CredentialView
import com.finngraph.auth.model.Email
import com.finngraph.auth.model.EmailCredentialView

interface CredentialPort {

    fun findUserIdByKakao(kakaoUserId: String): Long?
    fun linkKakao(userId: Long, kakaoUserId: String)
    fun findKakaoIdByUserId(userId: Long): String?
    fun registerEmail(userId: Long, email: Email, passwordHash: String)
    fun findByEmail(email: Email): EmailCredentialView?
    fun findByUserId(userId: Long): CredentialView?
    fun deleteAllByUserId(userId: Long): Int
}
