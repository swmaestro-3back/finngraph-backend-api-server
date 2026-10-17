package com.finngraph.favorite.adapter

import com.finngraph.favorite.adapter.jooq.tables.references.FAVORITES
import com.finngraph.favorite.model.FavoriteTarget
import com.finngraph.favorite.model.FavoriteType
import com.finngraph.favorite.model.FavoriteView
import com.finngraph.favorite.port.FavoritePort
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
class JooqFavoriteAdapter(
    @Qualifier("appDslContext") private val dsl: DSLContext,
) : FavoritePort {

    override fun findByUser(userId: Long): List<FavoriteView> =
        dsl.select(FAVORITES.TARGET_TYPE, FAVORITES.TARGET_KEY, FAVORITES.CREATED_AT)
            .from(FAVORITES)
            .where(FAVORITES.USER_ID.eq(userId))
            .orderBy(FAVORITES.CREATED_AT.desc(), FAVORITES.ID.desc())
            .fetch { it.toView() }

    override fun findTickersByUser(userId: Long): List<String> =
        dsl.select(FAVORITES.TARGET_KEY)
            .from(FAVORITES)
            .where(FAVORITES.USER_ID.eq(userId).and(FAVORITES.TARGET_TYPE.eq(FavoriteType.STOCK.name)))
            .fetch { it[FAVORITES.TARGET_KEY]!! }

    override fun countByUser(userId: Long): Int =
        dsl.fetchCount(FAVORITES, FAVORITES.USER_ID.eq(userId))

    override fun find(userId: Long, target: FavoriteTarget): FavoriteView? =
        dsl.select(FAVORITES.TARGET_TYPE, FAVORITES.TARGET_KEY, FAVORITES.CREATED_AT)
            .from(FAVORITES)
            .where(matches(userId, target))
            .fetchOne { it.toView() }

    override fun insert(userId: Long, target: FavoriteTarget): FavoriteView? =
        dsl.insertInto(FAVORITES)
            .set(FAVORITES.USER_ID, userId)
            .set(FAVORITES.TARGET_TYPE, target.type.name)
            .set(FAVORITES.TARGET_KEY, target.key)
            .onConflictDoNothing()
            .returningResult(FAVORITES.TARGET_TYPE, FAVORITES.TARGET_KEY, FAVORITES.CREATED_AT)
            .fetchOne { it.toView() }

    override fun delete(userId: Long, target: FavoriteTarget): Boolean =
        dsl.deleteFrom(FAVORITES)
            .where(matches(userId, target))
            .execute() > 0

    private fun matches(userId: Long, target: FavoriteTarget) =
        FAVORITES.USER_ID.eq(userId)
            .and(FAVORITES.TARGET_TYPE.eq(target.type.name))
            .and(FAVORITES.TARGET_KEY.eq(target.key))

    private fun Record.toView(): FavoriteView {
        val type = FavoriteType.valueOf(this[FAVORITES.TARGET_TYPE]!!)
        val key = this[FAVORITES.TARGET_KEY]!!
        val target = when (type) {
            FavoriteType.STOCK -> FavoriteTarget.stock(key)
            FavoriteType.THEME -> FavoriteTarget.theme(key.toLong())
        }
        return FavoriteView(target, this[FAVORITES.CREATED_AT]!!)
    }
}
