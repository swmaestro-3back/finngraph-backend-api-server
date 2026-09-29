package com.finngraph.briefing.port

import com.finngraph.briefing.model.BriefingSummary
import com.finngraph.briefing.model.DailyBriefing
import java.time.LocalDate

interface BriefingStorePort {

    fun findLatest(): DailyBriefing?
    fun findByDate(date: LocalDate): DailyBriefing?
    fun findPreviousBefore(date: LocalDate): DailyBriefing?
    fun listSummaries(limit: Int): List<BriefingSummary>
    fun upsert(briefing: DailyBriefing)
}
