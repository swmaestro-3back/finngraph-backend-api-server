package com.finngraph.favorite.port

import com.finngraph.favorite.model.FavoriteTarget
import com.finngraph.favorite.model.FolderName
import com.finngraph.favorite.model.FolderView

interface FavoriteFolderPort {

    fun findByUser(userId: Long): List<FolderView>
    fun find(userId: Long, folderId: Long): FolderView?
    fun insert(userId: Long, name: FolderName, position: Int): FolderView
    fun rename(userId: Long, folderId: Long, name: FolderName): Boolean
    fun delete(userId: Long, folderId: Long): Boolean
    fun reorder(userId: Long, folderIds: List<Long>)
    fun link(userId: Long, folderId: Long, target: FavoriteTarget): Boolean
    fun unlink(userId: Long, folderId: Long, target: FavoriteTarget): Boolean

    companion object {
        const val LIMIT: Int = 20
    }
}
