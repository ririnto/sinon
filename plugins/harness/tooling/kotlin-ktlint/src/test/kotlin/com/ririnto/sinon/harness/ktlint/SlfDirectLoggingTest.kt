@file:Suppress("ktlint:harness:explicit-property-type")

package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import com.pinterest.ktlint.test.LintViolation
import org.junit.jupiter.api.Test

class SlfDirectLoggingTest {
    private val assertThat = assertThatRule { SlfDirectLogging() }

    @Test
    fun eachDirectLogLevelIsFlagged() {
        val source =
            """
            import org.slf4j.LoggerFactory

            val logger = LoggerFactory.getLogger("sample")
            fun log() {
                logger.trace("trace")
                logger.debug("debug")
                logger.info("info")
                logger.warn("warn")
                logger.error("error")
            }
            """.trimIndent() + "\n"
        assertThat(source).hasLintViolationsWithoutAutoCorrect(
            LintViolation(5, 12, "direct SLF4J logging `trace`; use `logger.atTrace()` fluent logging"),
            LintViolation(6, 12, "direct SLF4J logging `debug`; use `logger.atDebug()` fluent logging"),
            LintViolation(7, 12, "direct SLF4J logging `info`; use `logger.atInfo()` fluent logging"),
            LintViolation(8, 12, "direct SLF4J logging `warn`; use `logger.atWarn()` fluent logging"),
            LintViolation(9, 12, "direct SLF4J logging `error`; use `logger.atError()` fluent logging")
        )
    }

    @Test
    fun fullyQualifiedLoggerFactoryCallIsFlagged() {
        assertThat("fun log() = org.slf4j.LoggerFactory.getLogger(\"sample\").info(\"message\")\n")
            .hasLintViolationWithoutAutoCorrect(
                1,
                57,
                "direct SLF4J logging `info`; use `org.slf4j.LoggerFactory.getLogger(\"sample\").atInfo()` fluent logging"
            )
    }

    @Test
    fun typedLoggerPropertyIsFlagged() {
        val source =
            """
            import org.slf4j.Logger

            val logger: Logger = TODO()
            fun log() {
                logger.info("message")
            }
            """.trimIndent() + "\n"
        assertThat(source)
            .hasLintViolationWithoutAutoCorrect(5, 12, "direct SLF4J logging `info`; use `logger.atInfo()` fluent logging")
    }

    @Test
    fun typedFunctionParameterIsFlagged() {
        val source =
            """
            import org.slf4j.Logger

            fun log(logger: Logger) {
                logger.info("message")
            }
            """.trimIndent() + "\n"
        assertThat(source)
            .hasLintViolationWithoutAutoCorrect(4, 12, "direct SLF4J logging `info`; use `logger.atInfo()` fluent logging")
    }

    @Test
    fun fluentLoggingIsSafe() {
        val source =
            """
            import org.slf4j.LoggerFactory

            val logger = LoggerFactory.getLogger("sample")
            fun log() {
                logger.atInfo().log("message")
            }
            """.trimIndent() + "\n"
        assertThat(source).hasNoLintViolations()
    }

    @Test
    fun unrelatedReceiverIsSafe() {
        assertThat("fun log() = other.info(\"message\")\n").hasNoLintViolations()
    }

    @Test
    fun nullableSafeCallLoggerParameterIsFlagged() {
        val source =
            """
            import org.slf4j.Logger

            fun log(logger: Logger?) {
                logger?.info("message")
            }
            """.trimIndent() + "\n"
        assertThat(source)
            .hasLintViolationWithoutAutoCorrect(4, 13, "direct SLF4J logging `info`; use `logger.atInfo()` fluent logging")
    }

    @Test
    fun nullableFullyQualifiedLoggerParameterIsFlagged() {
        val source =
            """
            fun log(logger: org.slf4j.Logger?) {
                logger?.info("message")
            }
            """.trimIndent() + "\n"
        assertThat(source)
            .hasLintViolationWithoutAutoCorrect(2, 13, "direct SLF4J logging `info`; use `logger.atInfo()` fluent logging")
    }

    @Test
    fun unrelatedNullableSafeCallIsSafe() {
        val source =
            """
            class Service {
                fun info(message: String) {}
            }

            fun log(service: Service?) {
                service?.info("message")
            }
            """.trimIndent() + "\n"
        assertThat(source).hasNoLintViolations()
    }
}
