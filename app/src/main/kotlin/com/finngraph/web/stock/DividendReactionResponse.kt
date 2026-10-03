package com.finngraph.web.stock

import com.finngraph.stock.model.DividendReaction
import java.math.BigDecimal
import java.time.LocalDate

data class DividendReactionResponse(
    val recordDate: LocalDate,
    val kind: String,
    val dps: BigDecimal,
    val exDate: LocalDate,
    val prevClose: BigDecimal,
    val exOpen: BigDecimal,
    val theoreticalDrop: BigDecimal,
    val openGap: BigDecimal,
    val recoveryDays: Int?,
    val pending: Boolean,
) {
    companion object {
        fun from(reaction: DividendReaction) = DividendReactionResponse(
            recordDate = reaction.recordDate,
            kind = reaction.kind,
            dps = reaction.dps,
            exDate = reaction.exDate,
            prevClose = reaction.prevClose,
            exOpen = reaction.exOpen,
            theoreticalDrop = reaction.theoreticalDrop,
            openGap = reaction.openGap,
            recoveryDays = reaction.recoveryDays,
            pending = reaction.pending,
        )
    }
}
