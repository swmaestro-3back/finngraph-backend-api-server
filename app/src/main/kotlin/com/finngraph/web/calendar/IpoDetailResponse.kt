package com.finngraph.web.calendar

import com.finngraph.calendar.model.CompanyProfile
import com.finngraph.calendar.model.FundUseShare
import com.finngraph.calendar.model.IpoFiling
import com.finngraph.calendar.model.IpoSchedule
import com.finngraph.calendar.model.Putback
import com.finngraph.calendar.model.Seller
import com.finngraph.calendar.model.Underwriter
import com.finngraph.composition.calendar.AfterListing
import com.finngraph.composition.calendar.IpoDetailView
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

data class IpoDetailResponse(
    val corpCode: String?,
    val ticker: String?,
    val name: String,
    val status: String,
    val spac: Boolean,
    val schedule: IpoScheduleResponse,
    val offering: IpoOfferingResponse,
    val company: IpoCompanyResponse?,
    val afterListing: AfterListingResponse?,
    val filing: IpoFilingResponse?,
    val asOf: OffsetDateTime?,
) {
    companion object {
        fun from(view: IpoDetailView) = with(view.listing) {
            IpoDetailResponse(
                corpCode = corpCode,
                ticker = ticker,
                name = name,
                status = status.name,
                spac = spac,
                schedule = IpoScheduleResponse.from(schedule),
                offering = IpoOfferingResponse.from(view),
                company = view.company?.let(IpoCompanyResponse::from),
                afterListing = view.afterListing?.let(AfterListingResponse::from),
                filing = filing?.let(IpoFilingResponse::from),
                asOf = view.asOf,
            )
        }
    }
}

data class IpoScheduleResponse(
    val subscrStart: LocalDate?,
    val subscrEnd: LocalDate?,
    val payDate: LocalDate?,
    val refundDate: LocalDate?,
    val listingDate: LocalDate?,
) {
    companion object {
        fun from(schedule: IpoSchedule) = IpoScheduleResponse(
            subscrStart = schedule.subscrStart,
            subscrEnd = schedule.subscrEnd,
            payDate = schedule.payDate,
            refundDate = schedule.refundDate,
            listingDate = schedule.listingDate,
        )
    }
}

data class IpoOfferingResponse(
    val price: BigDecimal?,
    val priceBasis: String,
    val shares: Long?,
    val amount: BigDecimal?,
    val method: String?,
    val underwriters: List<UnderwriterResponse>?,
    val fundUses: List<FundUseResponse>?,
    val fundUsesWithheld: Boolean,
    val sellers: List<SellerResponse>?,
    val oldShareRatio: BigDecimal?,
    val putback: PutbackResponse?,
) {
    companion object {
        fun from(view: IpoDetailView): IpoOfferingResponse {
            val filing = view.listing.filing
            return IpoOfferingResponse(
                price = view.listing.price,
                priceBasis = view.listing.priceBasis.name,
                shares = filing?.offerShares,
                amount = view.offerAmount,
                method = filing?.offerMethod,
                underwriters = view.underwriters?.map(UnderwriterResponse::from),
                fundUses = view.fundUses?.map(FundUseResponse::from),
                fundUsesWithheld = view.fundUsesWithheld,
                sellers = filing?.sellers?.map(SellerResponse::from),
                oldShareRatio = view.oldShareRatio,
                putback = filing?.putback?.let(PutbackResponse::from),
            )
        }
    }
}

data class UnderwriterResponse(
    val name: String,
    val role: String?,
    val shares: Long?,
    val amount: BigDecimal?,
    val method: String?,
) {
    companion object {
        fun from(underwriter: Underwriter) = UnderwriterResponse(
            name = underwriter.name,
            role = underwriter.role,
            shares = underwriter.shares,
            amount = underwriter.amount,
            method = underwriter.method,
        )
    }
}

data class FundUseResponse(val purpose: String, val amount: BigDecimal, val share: BigDecimal?) {
    companion object {
        fun from(use: FundUseShare) = FundUseResponse(use.purpose, use.amount, use.share)
    }
}

data class SellerResponse(
    val holder: String,
    val relation: String?,
    val before: Long?,
    val sold: Long?,
    val after: Long?,
) {
    companion object {
        fun from(seller: Seller) = SellerResponse(seller.holder, seller.relation, seller.before, seller.sold, seller.after)
    }
}

data class PutbackResponse(
    val reason: String?,
    val investors: String?,
    val shares: String?,
    val period: String?,
    val price: String?,
) {
    companion object {
        fun from(putback: Putback) = PutbackResponse(
            reason = putback.reason,
            investors = putback.investors,
            shares = putback.shares,
            period = putback.period,
            price = putback.price,
        )
    }
}

data class IpoCompanyResponse(
    val ceo: String?,
    val establishedOn: LocalDate?,
    val address: String?,
    val homepage: String?,
    val description: String?,
    val descriptionSource: String?,
    val descriptionRceptNo: String?,
) {
    companion object {
        fun from(company: CompanyProfile) = IpoCompanyResponse(
            ceo = company.ceo,
            establishedOn = company.establishedOn,
            address = company.address,
            homepage = company.homepage,
            description = company.description,
            descriptionSource = company.descriptionSource,
            descriptionRceptNo = company.descriptionRceptNo,
        )
    }
}

data class AfterListingResponse(
    val listingDate: LocalDate,
    val open: BigDecimal,
    val close: BigDecimal,
    val openReturn: BigDecimal?,
    val closeReturn: BigDecimal?,
    val price: BigDecimal?,
    val currentReturn: BigDecimal?,
    val priceDate: LocalDate?,
) {
    companion object {
        fun from(after: AfterListing) = AfterListingResponse(
            listingDate = after.listingDate,
            open = after.open,
            close = after.close,
            openReturn = after.openReturn,
            closeReturn = after.closeReturn,
            price = after.price,
            currentReturn = after.currentReturn,
            priceDate = after.priceDate,
        )
    }
}

data class IpoFilingResponse(val firstRceptNo: String, val latestRceptNo: String, val latestReportName: String) {
    companion object {
        fun from(filing: IpoFiling) = IpoFilingResponse(filing.firstRceptNo, filing.latestRceptNo, filing.latestReportName)
    }
}
