package com.finngraph.favorite.model

data class FolderView(
    val id: Long,
    val name: String,
    val position: Int,
    val targets: List<FavoriteTarget>,
)
