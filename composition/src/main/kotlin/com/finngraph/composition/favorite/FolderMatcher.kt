package com.finngraph.composition.favorite

import com.finngraph.favorite.model.FavoriteTarget
import com.finngraph.favorite.model.FolderName
import com.finngraph.favorite.model.FolderView
import com.finngraph.theme.model.PrimaryTheme
import com.finngraph.theme.model.ThemeMembership

data class FolderSuggestion(
    val folder: FolderView,
    val included: Boolean,
    val theme: PrimaryTheme?,
    val matched: List<String>,
    val recommended: Boolean,
)

object FolderMatcher {

    const val BROAD_THEME_SIZE: Int = 60
    private const val NAME_THEME_MIN_SIZE = 3
    private const val NAME_THEME_MIN_SHARED = 2
    private val TRAILING_NOTE = Regex("""\s*\([^()]*\)\s*$""")

    private data class Hit(val name: String, val shared: List<ThemeMembership>)

    private data class Scored(val suggestion: FolderSuggestion, val score: Double)

    fun specific(themes: List<ThemeMembership>): List<ThemeMembership> = themes.filter { it.size <= BROAD_THEME_SIZE }

    fun rank(
        target: FavoriteTarget,
        targetThemes: List<ThemeMembership>,
        folders: List<FolderView>,
        memberships: Map<String, List<ThemeMembership>>,
        nameOf: (FavoriteTarget) -> String,
    ): List<FolderSuggestion> {
        val wanted = targetThemes.associateBy { it.id }
        return folders.map { folder ->
            val others = folder.targets.filter { it != target }
            val hits = others.mapNotNull { member ->
                val shared = memberships[member.key].orEmpty().filter { it.id in wanted }
                if (shared.isEmpty()) null else Hit(nameOf(member), shared)
            }
            Scored(
                suggestion = FolderSuggestion(
                    folder = folder,
                    included = target in folder.targets,
                    theme = reasonOf(hits),
                    matched = hits.map { it.name },
                    recommended = hits.isNotEmpty() && hits.size * 2 > others.size,
                ),
                score = hits.sumOf { hit -> hit.shared.maxOf { 1.0 / it.size.coerceAtLeast(1) } },
            )
        }
            .sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.suggestion.folder.position })
            .map { it.suggestion }
    }

    fun nameTheme(targetThemes: List<ThemeMembership>, favoriteThemes: List<List<ThemeMembership>>): ThemeMembership? {
        val candidates = targetThemes.filter { it.size >= NAME_THEME_MIN_SIZE }.ifEmpty { targetThemes }
        val shared = favoriteThemes.flatMap { themes -> themes.map { it.id }.distinct() }
            .groupingBy { it }
            .eachCount()
            .filterValues { it >= NAME_THEME_MIN_SHARED }
        return candidates.minWithOrNull(
            compareByDescending<ThemeMembership> { shared[it.id] ?: 0 }.thenBy { it.size }.thenBy { it.name },
        )
    }

    fun folderName(raw: String): String? {
        val trimmed = raw.trim()
        if (fits(trimmed)) return trimmed
        val plain = trimmed.replace(TRAILING_NOTE, "").trim()
        return plain.takeIf { fits(it) }
    }

    private fun fits(value: String): Boolean =
        value.isNotEmpty() && value.codePointCount(0, value.length) <= FolderName.MAX_LENGTH

    private fun reasonOf(hits: List<Hit>): PrimaryTheme? {
        val counts = hits.flatMap { hit -> hit.shared.distinctBy { it.id } }.groupBy { it.id }
        return counts.values
            .minWithOrNull(
                compareByDescending<List<ThemeMembership>> { it.size }
                    .thenBy { it.first().size }
                    .thenBy { it.first().name },
            )
            ?.first()
            ?.let { PrimaryTheme(it.id, it.name) }
    }
}
