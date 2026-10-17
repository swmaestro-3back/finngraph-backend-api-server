package com.finngraph.favorite.model

data class FavoriteTarget private constructor(
    val type: FavoriteType,
    val key: String,
) {

    companion object {
        const val STOCK_KEY_MAX_LENGTH: Int = 20

        private val STOCK_KEY = Regex("[A-Z0-9]{1,$STOCK_KEY_MAX_LENGTH}")
        private val THEME_KEY = Regex("[0-9]{1,19}")

        fun of(type: String, key: String): FavoriteTarget {
            val parsed = FavoriteType.entries.firstOrNull { it.name == type }
            requireNotNull(parsed) { "type은 STOCK 또는 THEME여야 합니다: $type" }
            return when (parsed) {
                FavoriteType.STOCK -> stock(key)
                FavoriteType.THEME -> theme(key)
            }
        }

        fun stock(ticker: String): FavoriteTarget {
            val normalized = ticker.trim().uppercase()
            require(STOCK_KEY.matches(normalized)) {
                "STOCK 키는 영숫자 1~${STOCK_KEY_MAX_LENGTH}자여야 합니다: $ticker"
            }
            return FavoriteTarget(FavoriteType.STOCK, normalized)
        }

        fun theme(id: Long): FavoriteTarget {
            require(id > 0) { "THEME 키는 양의 정수여야 합니다: $id" }
            return FavoriteTarget(FavoriteType.THEME, id.toString())
        }

        private fun theme(raw: String): FavoriteTarget {
            val trimmed = raw.trim()
            require(THEME_KEY.matches(trimmed)) { "THEME 키는 양의 정수여야 합니다: $raw" }
            return theme(trimmed.toLong())
        }
    }
}
