package com.finngraph.hotthemes

import com.finngraph.composition.HotThemeComposer
import com.finngraph.composition.port.HotThemePublisherPort
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate

sealed interface HotThemePublishOutcome {
    data class Published(val themes: Int, val tradeDate: LocalDate?) : HotThemePublishOutcome
    data object Skipped : HotThemePublishOutcome
}

@Component
class HotThemePublishService(
    private val composer: HotThemeComposer,
    private val publisher: HotThemePublisherPort,
    registry: MeterRegistry,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val success = counter(registry, "success")
    private val failure = counter(registry, "failure")
    private val skipped = counter(registry, "skipped")

    fun publish(): HotThemePublishOutcome = try {
        attempt()
    } catch (e: Exception) {
        failure.increment()
        throw e
    }

    private fun attempt(): HotThemePublishOutcome {
        val snapshot = composer.hotForEtl(COUNT)
        if (snapshot.themes.isEmpty()) {
            log.warn("hot-themes publish skipped: no themes selected")
            skipped.increment()
            return HotThemePublishOutcome.Skipped
        }
        publisher.publish(snapshot)
        log.info("hot-themes published: {} themes, tradeDate={}", snapshot.themes.size, snapshot.tradeDate)
        success.increment()
        return HotThemePublishOutcome.Published(snapshot.themes.size, snapshot.tradeDate)
    }

    private fun counter(registry: MeterRegistry, result: String): Counter =
        Counter.builder(METRIC).tag("result", result).register(registry)

    companion object {
        const val COUNT = 30
        private const val METRIC = "hot.themes.publish"
    }
}
