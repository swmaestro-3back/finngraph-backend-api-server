package com.finngraph.theme.port

import com.finngraph.theme.model.IndexPeriod
import com.finngraph.theme.model.ThemeId
import com.finngraph.theme.model.ThemeIndexCandle
import com.finngraph.theme.model.ThemeIndexSummary

interface ThemeIndexPort {

    fun findCandles(id: ThemeId, period: IndexPeriod, limit: Int): List<ThemeIndexCandle>

    fun findSummary(id: ThemeId): ThemeIndexSummary?
}
