package com.finngraph.favorite.model

import java.time.OffsetDateTime

data class FavoriteView(
    val target: FavoriteTarget,
    val createdAt: OffsetDateTime,
)
