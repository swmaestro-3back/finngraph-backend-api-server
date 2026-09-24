package com.finngraph.favorite.model

sealed interface AddFavoriteResult {
    data class Added(val view: FavoriteView) : AddFavoriteResult
    data class AlreadyExists(val view: FavoriteView) : AddFavoriteResult
    data object LimitExceeded : AddFavoriteResult
}
