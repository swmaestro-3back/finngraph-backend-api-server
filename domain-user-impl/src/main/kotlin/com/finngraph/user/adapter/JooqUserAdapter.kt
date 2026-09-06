package com.finngraph.user.adapter

import com.finngraph.user.adapter.jooq.tables.references.USERS
import com.finngraph.user.model.Nickname
import com.finngraph.user.model.UserView
import com.finngraph.user.port.UserPort
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

@Component
class JooqUserAdapter(
    @Qualifier("appDslContext") private val dsl: DSLContext,
) : UserPort {

    override fun create(nickname: Nickname): Long =
        dsl.insertInto(USERS)
            .set(USERS.NICKNAME, nickname.value)
            .returningResult(USERS.ID)
            .fetchOne()!!
            .value1()!!

    override fun findById(id: Long): UserView? =
        dsl.select(USERS.ID, USERS.NICKNAME, USERS.CREATED_AT)
            .from(USERS)
            .where(USERS.ID.eq(id))
            .fetchOne { UserView(it[USERS.ID]!!, it[USERS.NICKNAME]!!, it[USERS.CREATED_AT]!!) }

    override fun updateNickname(id: Long, nickname: Nickname): UserView? =
        dsl.update(USERS)
            .set(USERS.NICKNAME, nickname.value)
            .set(USERS.UPDATED_AT, OffsetDateTime.now())
            .where(USERS.ID.eq(id))
            .returningResult(USERS.ID, USERS.NICKNAME, USERS.CREATED_AT)
            .fetchOne { UserView(it[USERS.ID]!!, it[USERS.NICKNAME]!!, it[USERS.CREATED_AT]!!) }

    override fun delete(id: Long): Boolean =
        dsl.deleteFrom(USERS)
            .where(USERS.ID.eq(id))
            .execute() > 0
}
