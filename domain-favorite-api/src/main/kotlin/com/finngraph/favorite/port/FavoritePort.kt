package com.finngraph.favorite.port

import com.finngraph.favorite.model.FavoriteTarget
import com.finngraph.favorite.model.FavoriteView

interface FavoritePort {

    fun findByUser(userId: Long): List<FavoriteView>
    fun findTickersByUser(userId: Long): List<String>
    fun countByUser(userId: Long): Int
    fun find(userId: Long, target: FavoriteTarget): FavoriteView?
    fun insert(userId: Long, target: FavoriteTarget): FavoriteView?
    fun delete(userId: Long, target: FavoriteTarget): Boolean

    companion object {
        const val LIMIT: Int = 50
    }
}
