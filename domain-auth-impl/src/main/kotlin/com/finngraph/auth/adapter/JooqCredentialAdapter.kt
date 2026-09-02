package com.finngraph.auth.adapter

import com.finngraph.auth.DuplicateCredentialException
import com.finngraph.auth.adapter.jooq.tables.references.AUTH_CREDENTIALS
import com.finngraph.auth.model.AuthProvider
import com.finngraph.auth.model.CredentialView
import com.finngraph.auth.model.Email
import com.finngraph.auth.model.EmailCredentialView
import com.finngraph.auth.port.CredentialPort
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Component

@Component
class JooqCredentialAdapter(
    @Qualifier("appDslContext") private val dsl: DSLContext,
) : CredentialPort {

    override fun findUserIdByKakao(kakaoUserId: String): Long? =
        dsl.select(AUTH_CREDENTIALS.USER_ID)
            .from(AUTH_CREDENTIALS)
            .where(
                AUTH_CREDENTIALS.PROVIDER.eq(AuthProvider.KAKAO.name)
                    .and(AUTH_CREDENTIALS.PROVIDER_USER_ID.eq(kakaoUserId)),
            )
            .fetchOne(AUTH_CREDENTIALS.USER_ID)

    override fun linkKakao(userId: Long, kakaoUserId: String) {
        try {
            dsl.insertInto(AUTH_CREDENTIALS)
                .set(AUTH_CREDENTIALS.USER_ID, userId)
                .set(AUTH_CREDENTIALS.PROVIDER, AuthProvider.KAKAO.name)
                .set(AUTH_CREDENTIALS.PROVIDER_USER_ID, kakaoUserId)
                .execute()
        } catch (e: DuplicateKeyException) {
            throw DuplicateCredentialException(AuthProvider.KAKAO)
        }
    }

    override fun findKakaoIdByUserId(userId: Long): String? =
        dsl.select(AUTH_CREDENTIALS.PROVIDER_USER_ID)
            .from(AUTH_CREDENTIALS)
            .where(
                AUTH_CREDENTIALS.PROVIDER.eq(AuthProvider.KAKAO.name)
                    .and(AUTH_CREDENTIALS.USER_ID.eq(userId)),
            )
            .fetchOne(AUTH_CREDENTIALS.PROVIDER_USER_ID)

    override fun registerEmail(userId: Long, email: Email, passwordHash: String) {
        try {
            dsl.insertInto(AUTH_CREDENTIALS)
                .set(AUTH_CREDENTIALS.USER_ID, userId)
                .set(AUTH_CREDENTIALS.PROVIDER, AuthProvider.EMAIL.name)
                .set(AUTH_CREDENTIALS.EMAIL, email.value)
                .set(AUTH_CREDENTIALS.PASSWORD_HASH, passwordHash)
                .execute()
        } catch (e: DuplicateKeyException) {
            throw DuplicateCredentialException(AuthProvider.EMAIL)
        }
    }

    override fun findByEmail(email: Email): EmailCredentialView? =
        dsl.select(AUTH_CREDENTIALS.USER_ID, AUTH_CREDENTIALS.PASSWORD_HASH)
            .from(AUTH_CREDENTIALS)
            .where(
                AUTH_CREDENTIALS.PROVIDER.eq(AuthProvider.EMAIL.name)
                    .and(AUTH_CREDENTIALS.EMAIL.eq(email.value)),
            )
            .fetchOne {
                EmailCredentialView(it[AUTH_CREDENTIALS.USER_ID]!!, it[AUTH_CREDENTIALS.PASSWORD_HASH]!!)
            }

    override fun findByUserId(userId: Long): CredentialView? =
        dsl.select(AUTH_CREDENTIALS.PROVIDER, AUTH_CREDENTIALS.EMAIL)
            .from(AUTH_CREDENTIALS)
            .where(AUTH_CREDENTIALS.USER_ID.eq(userId))
            .orderBy(AUTH_CREDENTIALS.ID)
            .limit(1)
            .fetchOne {
                CredentialView(
                    AuthProvider.valueOf(it[AUTH_CREDENTIALS.PROVIDER]!!),
                    it[AUTH_CREDENTIALS.EMAIL],
                )
            }

    override fun deleteAllByUserId(userId: Long): Int =
        dsl.deleteFrom(AUTH_CREDENTIALS)
            .where(AUTH_CREDENTIALS.USER_ID.eq(userId))
            .execute()
}
