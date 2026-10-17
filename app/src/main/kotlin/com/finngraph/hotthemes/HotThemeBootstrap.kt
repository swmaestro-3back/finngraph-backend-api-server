package com.finngraph.hotthemes

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import kotlin.concurrent.thread

@Component
class HotThemeBootstrap(
    private val publishService: HotThemePublishService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        thread(name = "hot-themes-bootstrap", isDaemon = true) { publishSafely() }
    }

    fun publishSafely() {
        try {
            publishService.publish()
        } catch (e: Exception) {
            log.error("hot-themes publish failed", e)
        }
    }
}
