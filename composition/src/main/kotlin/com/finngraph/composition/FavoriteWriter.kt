package com.finngraph.composition

import com.finngraph.favorite.model.AddFavoriteResult
import com.finngraph.favorite.model.FavoriteTarget
import com.finngraph.favorite.port.FavoritePort
import com.finngraph.user.port.UserPort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional("appTransactionManager")
class FavoriteWriter(
    private val users: UserPort,
    private val favorites: FavoritePort,
) {

    fun add(userId: Long, target: FavoriteTarget): AddFavoriteResult? {
        if (!users.lockForUpdate(userId)) return null
        favorites.find(userId, target)?.let { return AddFavoriteResult.AlreadyExists(it) }
        if (favorites.countByUser(userId) >= FavoritePort.LIMIT) return AddFavoriteResult.LimitExceeded
        val inserted = favorites.insert(userId, target)
            ?: return AddFavoriteResult.AlreadyExists(checkNotNull(favorites.find(userId, target)))
        return AddFavoriteResult.Added(inserted)
    }

    fun remove(userId: Long, target: FavoriteTarget): Boolean = favorites.delete(userId, target)
}
