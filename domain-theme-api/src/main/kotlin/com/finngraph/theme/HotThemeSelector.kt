package com.finngraph.theme

import com.finngraph.theme.model.ThemeSummary

object HotThemeSelector {

    fun select(themes: List<ThemeSummary>, count: Int): List<ThemeSummary> {
        val ups = themes
            .filter { it.change?.signum() == 1 }
            .sortedByDescending { it.change }
        val downs = themes
            .filter { it.change?.signum() == -1 }
            .sortedBy { it.change }
        return ups.take((count + 1) / 2) + downs.take(count / 2)
    }
}
