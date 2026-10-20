package com.finngraph.composition.favorite

import com.finngraph.favorite.model.FavoriteTarget
import com.finngraph.favorite.model.FolderName
import com.finngraph.favorite.model.FolderView
import com.finngraph.favorite.port.FavoriteFolderPort
import com.finngraph.favorite.port.FavoritePort
import com.finngraph.user.port.UserPort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

sealed interface FolderWrite {
    data class Done(val folder: FolderView) : FolderWrite
    data object NotFound : FolderWrite
    data object Duplicate : FolderWrite
    data object LimitExceeded : FolderWrite
    data object FavoriteLimitExceeded : FolderWrite
}

@Component
@Transactional("appTransactionManager")
class FavoriteFolderWriter(
    private val users: UserPort,
    private val favorites: FavoritePort,
    private val folders: FavoriteFolderPort,
) {

    fun create(userId: Long, name: FolderName): FolderWrite? {
        if (!users.lockForUpdate(userId)) return null
        val owned = folders.findByUser(userId)
        if (owned.size >= FavoriteFolderPort.LIMIT) return FolderWrite.LimitExceeded
        if (owned.any { it.name == name.value }) return FolderWrite.Duplicate
        val position = (owned.maxOfOrNull { it.position } ?: -1) + 1
        return FolderWrite.Done(folders.insert(userId, name, position))
    }

    fun rename(userId: Long, folderId: Long, name: FolderName): FolderWrite? {
        if (!users.lockForUpdate(userId)) return null
        val owned = folders.findByUser(userId)
        val folder = owned.firstOrNull { it.id == folderId } ?: return FolderWrite.NotFound
        if (owned.any { it.id != folderId && it.name == name.value }) return FolderWrite.Duplicate
        if (folder.name != name.value) folders.rename(userId, folderId, name)
        return FolderWrite.Done(folder.copy(name = name.value))
    }

    fun delete(userId: Long, folderId: Long): Boolean = folders.delete(userId, folderId)

    fun reorder(userId: Long, folderIds: List<Long>): Boolean? {
        if (!users.lockForUpdate(userId)) return null
        val owned = folders.findByUser(userId).map { it.id }
        if (folderIds.size != owned.size || folderIds.toSet() != owned.toSet()) return false
        folders.reorder(userId, folderIds)
        return true
    }

    fun addItem(userId: Long, folderId: Long, target: FavoriteTarget): FolderWrite? {
        if (!users.lockForUpdate(userId)) return null
        val folder = folders.find(userId, folderId) ?: return FolderWrite.NotFound
        if (favorites.find(userId, target) == null) {
            if (favorites.countByUser(userId) >= FavoritePort.LIMIT) return FolderWrite.FavoriteLimitExceeded
            favorites.insert(userId, target)
        }
        folders.link(userId, folderId, target)
        return FolderWrite.Done(folder)
    }

    fun removeItem(userId: Long, folderId: Long, target: FavoriteTarget): FolderWrite {
        val folder = folders.find(userId, folderId) ?: return FolderWrite.NotFound
        folders.unlink(userId, folderId, target)
        return FolderWrite.Done(folder)
    }
}
