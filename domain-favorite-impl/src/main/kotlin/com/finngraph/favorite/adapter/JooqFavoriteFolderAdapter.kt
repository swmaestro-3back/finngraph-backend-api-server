package com.finngraph.favorite.adapter

import com.finngraph.favorite.adapter.jooq.tables.references.FAVORITES
import com.finngraph.favorite.adapter.jooq.tables.references.FAVORITE_FOLDERS
import com.finngraph.favorite.adapter.jooq.tables.references.FAVORITE_FOLDER_ITEMS
import com.finngraph.favorite.model.FavoriteTarget
import com.finngraph.favorite.model.FavoriteType
import com.finngraph.favorite.model.FolderName
import com.finngraph.favorite.model.FolderView
import com.finngraph.favorite.port.FavoriteFolderPort
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
class JooqFavoriteFolderAdapter(
    @Qualifier("appDslContext") private val dsl: DSLContext,
) : FavoriteFolderPort {

    override fun findByUser(userId: Long): List<FolderView> {
        val folders = dsl.select(FAVORITE_FOLDERS.ID, FAVORITE_FOLDERS.NAME, FAVORITE_FOLDERS.POSITION)
            .from(FAVORITE_FOLDERS)
            .where(FAVORITE_FOLDERS.USER_ID.eq(userId))
            .orderBy(FAVORITE_FOLDERS.POSITION, FAVORITE_FOLDERS.ID)
            .fetch()
        if (folders.isEmpty()) return emptyList()
        val members = targetsByFolder(folders.map { requireNotNull(it[FAVORITE_FOLDERS.ID]) })
        return folders.map {
            val id = requireNotNull(it[FAVORITE_FOLDERS.ID])
            FolderView(
                id = id,
                name = requireNotNull(it[FAVORITE_FOLDERS.NAME]),
                position = requireNotNull(it[FAVORITE_FOLDERS.POSITION]),
                targets = members[id].orEmpty(),
            )
        }
    }

    override fun find(userId: Long, folderId: Long): FolderView? =
        findByUser(userId).firstOrNull { it.id == folderId }

    override fun insert(userId: Long, name: FolderName, position: Int): FolderView {
        val id = dsl.insertInto(FAVORITE_FOLDERS)
            .set(FAVORITE_FOLDERS.USER_ID, userId)
            .set(FAVORITE_FOLDERS.NAME, name.value)
            .set(FAVORITE_FOLDERS.POSITION, position)
            .returningResult(FAVORITE_FOLDERS.ID)
            .fetchOne { requireNotNull(it.value1()) }
        return FolderView(requireNotNull(id), name.value, position, emptyList())
    }

    override fun rename(userId: Long, folderId: Long, name: FolderName): Boolean =
        dsl.update(FAVORITE_FOLDERS)
            .set(FAVORITE_FOLDERS.NAME, name.value)
            .set(FAVORITE_FOLDERS.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(owned(userId, folderId))
            .execute() > 0

    override fun delete(userId: Long, folderId: Long): Boolean =
        dsl.deleteFrom(FAVORITE_FOLDERS)
            .where(owned(userId, folderId))
            .execute() > 0

    override fun reorder(userId: Long, folderIds: List<Long>) {
        val updates = folderIds.mapIndexed { index, folderId ->
            dsl.update(FAVORITE_FOLDERS)
                .set(FAVORITE_FOLDERS.POSITION, index)
                .set(FAVORITE_FOLDERS.UPDATED_AT, DSL.currentOffsetDateTime())
                .where(owned(userId, folderId))
        }
        if (updates.isNotEmpty()) dsl.batch(updates).execute()
    }

    override fun link(userId: Long, folderId: Long, target: FavoriteTarget): Boolean =
        dsl.insertInto(FAVORITE_FOLDER_ITEMS, FAVORITE_FOLDER_ITEMS.FOLDER_ID, FAVORITE_FOLDER_ITEMS.FAVORITE_ID)
            .select(
                dsl.select(FAVORITE_FOLDERS.ID, FAVORITES.ID)
                    .from(FAVORITE_FOLDERS)
                    .join(FAVORITES).on(favoriteOf(userId, target))
                    .where(owned(userId, folderId)),
            )
            .onConflictDoNothing()
            .execute() > 0

    override fun unlink(userId: Long, folderId: Long, target: FavoriteTarget): Boolean =
        dsl.deleteFrom(FAVORITE_FOLDER_ITEMS)
            .where(
                FAVORITE_FOLDER_ITEMS.FOLDER_ID.`in`(
                    dsl.select(FAVORITE_FOLDERS.ID).from(FAVORITE_FOLDERS).where(owned(userId, folderId)),
                ),
            )
            .and(
                FAVORITE_FOLDER_ITEMS.FAVORITE_ID.`in`(
                    dsl.select(FAVORITES.ID).from(FAVORITES).where(favoriteOf(userId, target)),
                ),
            )
            .execute() > 0

    private fun targetsByFolder(folderIds: List<Long>): Map<Long, List<FavoriteTarget>> =
        dsl.select(FAVORITE_FOLDER_ITEMS.FOLDER_ID, FAVORITES.TARGET_TYPE, FAVORITES.TARGET_KEY)
            .from(FAVORITE_FOLDER_ITEMS)
            .join(FAVORITES).on(FAVORITES.ID.eq(FAVORITE_FOLDER_ITEMS.FAVORITE_ID))
            .where(FAVORITE_FOLDER_ITEMS.FOLDER_ID.`in`(folderIds))
            .orderBy(FAVORITE_FOLDER_ITEMS.CREATED_AT.desc(), FAVORITES.ID.desc())
            .fetch()
            .groupBy(
                { requireNotNull(it[FAVORITE_FOLDER_ITEMS.FOLDER_ID]) },
                { targetOf(requireNotNull(it[FAVORITES.TARGET_TYPE]), requireNotNull(it[FAVORITES.TARGET_KEY])) },
            )

    private fun owned(userId: Long, folderId: Long): Condition =
        FAVORITE_FOLDERS.ID.eq(folderId).and(FAVORITE_FOLDERS.USER_ID.eq(userId))

    private fun favoriteOf(userId: Long, target: FavoriteTarget): Condition =
        FAVORITES.USER_ID.eq(userId)
            .and(FAVORITES.TARGET_TYPE.eq(target.type.name))
            .and(FAVORITES.TARGET_KEY.eq(target.key))

    private fun targetOf(type: String, key: String): FavoriteTarget = when (FavoriteType.valueOf(type)) {
        FavoriteType.STOCK -> FavoriteTarget.stock(key)
        FavoriteType.THEME -> FavoriteTarget.theme(key.toLong())
    }
}
