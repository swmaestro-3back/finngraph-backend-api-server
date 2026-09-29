package com.finngraph.dailybriefing

import com.finngraph.briefing.model.BriefingStatus
import com.finngraph.briefing.port.BriefingStorePort
import com.finngraph.composition.briefing.BriefingAssembler
import com.finngraph.composition.briefing.BriefingAssembly
import com.finngraph.composition.briefing.BriefingComposer
import com.finngraph.composition.briefing.BriefingDraftSet
import com.finngraph.composition.briefing.BriefingInput
import com.finngraph.composition.briefing.SentenceDraft
import com.finngraph.composition.port.BriefingWriterPort
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

sealed interface BriefingGenerationOutcome {
    data class Generated(
        val baseDate: LocalDate,
        val status: BriefingStatus,
        val issueCount: Int,
        val droppedSentences: Int,
        val durationMs: Long,
    ) : BriefingGenerationOutcome

    data class Skipped(val reason: String, val baseDate: LocalDate?) : BriefingGenerationOutcome
}

@Component
class BriefingGenerationService(
    private val composer: BriefingComposer,
    private val writer: BriefingWriterPort,
    private val store: BriefingStorePort,
    registry: MeterRegistry,
    private val clock: Clock = Clock.systemUTC(),
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val success = counter(registry, "success")
    private val partial = counter(registry, "partial")
    private val skipped = counter(registry, "skipped")
    private val failure = counter(registry, "failure")
    private val timer = Timer.builder("$METRIC.duration").register(registry)

    fun generate(date: LocalDate?, force: Boolean): BriefingGenerationOutcome = try {
        attempt(date, force)
    } catch (e: Exception) {
        failure.increment()
        throw e
    }

    private fun attempt(date: LocalDate?, force: Boolean): BriefingGenerationOutcome {
        if (!writer.enabled) return skip(REASON_LLM_DISABLED, date)

        val input = when (val assembly = composer.assemble(date)) {
            BriefingAssembly.NoBaseDate -> return skip(REASON_NO_BASE_DATE, null)
            is BriefingAssembly.DateMismatch -> return skip(REASON_DATE_MISMATCH, assembly.actual)
            is BriefingAssembly.LowCoverage -> return skip(REASON_LOW_COVERAGE, assembly.baseDate)
            is BriefingAssembly.Ready -> assembly.input
        }
        if (!force && store.findByDate(input.baseDate) != null) return skip(REASON_EXISTS, input.baseDate)

        val started = clock.millis()
        val drafts = draft(input)
        log.info("briefing headline draft: {}", drafts.headline)
        val durationMs = clock.millis() - started
        val built = BriefingAssembler.build(
            input = input,
            drafts = drafts,
            generatedAt = OffsetDateTime.now(clock).withOffsetSameInstant(KST),
        )
        val briefing = built.briefing
        store.upsert(briefing)
        timer.record(java.time.Duration.ofMillis(durationMs))
        if (briefing.status == BriefingStatus.READY) success.increment() else partial.increment()
        log.info(
            "briefing generated: baseDate={}, status={}, issues={}, dropped={}, calls={}, durationMs={}",
            briefing.baseDate, briefing.status, briefing.issues.size, built.droppedSentences, drafts.calls, durationMs,
        )
        return BriefingGenerationOutcome.Generated(
            baseDate = briefing.baseDate,
            status = briefing.status,
            issueCount = briefing.issues.size,
            droppedSentences = built.droppedSentences,
            durationMs = durationMs,
        )
    }

    private fun draft(input: BriefingInput): BriefingDraftSet {
        var calls = 0
        val headline = call { calls += 1; writer.headline(BriefingAssembler.headlineInput(input)) }
        val commentaries = LinkedHashMap<Long, List<SentenceDraft>?>()
        input.issues.forEach { issue ->
            commentaries[issue.cluster.id] = call { calls += 1; writer.commentary(BriefingAssembler.commentaryInput(issue)) }
        }
        val watch = if (input.watchCandidates.isEmpty()) null
        else call { calls += 1; writer.watchPoints(BriefingAssembler.watchInput(input)) }
        return BriefingDraftSet(headline, commentaries, watch, calls)
    }

    private fun <T> call(block: () -> T?): T? = try {
        block()
    } catch (e: Exception) {
        val causes = generateSequence<Throwable>(e) { it.cause?.takeIf { c -> c !== it } }.joinToString(" <- ") { it.toString() }
        log.warn("briefing writer call failed: {}", causes, e)
        null
    }

    private fun skip(reason: String, baseDate: LocalDate?): BriefingGenerationOutcome {
        skipped.increment()
        log.info("briefing skipped: reason={}, baseDate={}", reason, baseDate)
        return BriefingGenerationOutcome.Skipped(reason, baseDate)
    }

    private fun counter(registry: MeterRegistry, result: String): Counter =
        Counter.builder(METRIC).tag("result", result).register(registry)

    companion object {
        const val REASON_LLM_DISABLED = "LLM_DISABLED"
        const val REASON_NO_BASE_DATE = "NO_BASE_DATE"
        const val REASON_DATE_MISMATCH = "DATE_MISMATCH"
        const val REASON_LOW_COVERAGE = "LOW_COVERAGE"
        const val REASON_EXISTS = "EXISTS"
        private const val METRIC = "briefing.generate"
        private val KST: ZoneOffset = ZoneOffset.ofHours(9)
    }
}
