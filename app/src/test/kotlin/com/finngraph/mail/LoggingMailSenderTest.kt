package com.finngraph.mail

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.mock.env.MockEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoggingMailSenderTest {

    private val logger = LoggerFactory.getLogger(LoggingMailSender::class.java) as Logger
    private val appender = ListAppender<ILoggingEvent>()
    private val originalLevel: Level? = logger.level

    @BeforeEach
    fun attach() {
        appender.start()
        logger.addAppender(appender)
        logger.level = Level.DEBUG
    }

    @AfterEach
    fun detach() {
        logger.detachAppender(appender)
        logger.level = originalLevel
    }

    @Test
    fun `prod 프로파일에서는 생성할 수 없다`() {
        val environment = MockEnvironment().apply { setActiveProfiles("prod") }

        assertThrows(IllegalArgumentException::class.java) { LoggingMailSender(environment) }
    }

    @Test
    fun `INFO에는 수신자·제목만 남기고 본문은 DEBUG로 내린다`() {
        LoggingMailSender(MockEnvironment()).send("u@x.com", "[finngraph] 이메일 인증 코드", "인증 코드는 123456 입니다.")

        val info = appender.list.single { it.level == Level.INFO }.formattedMessage
        assertTrue(info.contains("u@x.com"), info)
        assertTrue(info.contains("[finngraph] 이메일 인증 코드"), info)
        assertFalse(info.contains("123456"), info)

        val debug = appender.list.single { it.level == Level.DEBUG }.formattedMessage
        assertTrue(debug.contains("123456"), debug)
        assertEquals(2, appender.list.size)
    }
}
