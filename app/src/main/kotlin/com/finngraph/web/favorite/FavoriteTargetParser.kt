package com.finngraph.web.favorite

import com.finngraph.favorite.model.FavoriteTarget
import com.finngraph.favorite.model.FavoriteType
import com.finngraph.web.common.InvalidParameterException

internal fun parseFolderTarget(type: String, key: String): FavoriteTarget {
    if (type != FavoriteType.STOCK.name) {
        throw InvalidParameterException("나만의 테마에는 종목만 담을 수 있습니다", mapOf("type" to "must be STOCK"))
    }
    return parseFavoriteTarget(type, key)
}

internal fun parseFavoriteTarget(type: String, key: String): FavoriteTarget {
    if (FavoriteType.entries.none { it.name == type }) {
        throw InvalidParameterException(
            "type은 STOCK 또는 THEME여야 합니다",
            mapOf("type" to "must be one of STOCK, THEME"),
        )
    }
    return runCatching { FavoriteTarget.of(type, key) }.getOrElse {
        val rule =
            if (type == FavoriteType.STOCK.name) "must be 1-20 alphanumeric characters"
            else "must be a positive integer"
        throw InvalidParameterException(it.message ?: "key가 올바르지 않습니다", mapOf("key" to rule))
    }
}
