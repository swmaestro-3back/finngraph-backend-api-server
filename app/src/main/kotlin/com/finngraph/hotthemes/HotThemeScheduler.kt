package com.finngraph.hotthemes

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class HotThemeScheduler(
    private val publishService: HotThemePublishService,
    private val taskScheduler: TaskScheduler,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        taskScheduler.schedule(this::publishSafely, Instant.now())
    }

    @Scheduled(cron = "\${app.hot-themes.cron}", zone = "Asia/Seoul")
    fun publishDaily() = publishSafely()

    fun publishSafely() {
        try {
            publishService.publish()
        } catch (e: Exception) {
            log.error("hot-themes publish failed", e)
        }
    }
}
