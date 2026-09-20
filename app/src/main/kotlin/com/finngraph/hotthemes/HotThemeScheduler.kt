package com.finngraph.hotthemes

import com.finngraph.composition.HotThemeComposer
import com.finngraph.composition.port.HotThemePublisherPort
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class HotThemeScheduler(
    private val composer: HotThemeComposer,
    private val publisher: HotThemePublisherPort,
    private val taskScheduler: TaskScheduler,
    registry: MeterRegistry,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val success = counter(registry, "success")
    private val failure = counter(registry, "failure")
    private val skipped = counter(registry, "skipped")

    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        taskScheduler.schedule(this::publish, Instant.now())
    }

    @Scheduled(cron = "\${app.hot-themes.cron}", zone = "Asia/Seoul")
    fun publishDaily() = publish()

    fun publish() {
        try {
            val snapshot = composer.hotForEtl(COUNT)
            if (snapshot.themes.isEmpty()) {
                log.warn("hot-themes publish skipped: no themes selected")
                skipped.increment()
                return
            }
            publisher.publish(snapshot)
            log.info("hot-themes published: {} themes, tradeDate={}", snapshot.themes.size, snapshot.tradeDate)
            success.increment()
        } catch (e: Exception) {
            log.error("hot-themes publish failed", e)
            failure.increment()
        }
    }

    private fun counter(registry: MeterRegistry, result: String): Counter =
        Counter.builder(METRIC).tag("result", result).register(registry)

    companion object {
        const val COUNT = 30
        private const val METRIC = "hot.themes.publish"
    }
}
