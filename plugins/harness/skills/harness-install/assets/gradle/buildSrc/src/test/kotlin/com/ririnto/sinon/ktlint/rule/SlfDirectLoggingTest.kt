package com.ririnto.sinon.ktlint.rule

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SlfDirectLoggingTest {
    private val ruleProvider: RuleProvider = RuleProvider { SlfDirectLogging() }

    @Test
    fun eachDirectLogLevelIsFlagged() {
        val errors = RuleTestSupport.lintRule(
            ruleProvider,
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
        )
        assertEquals(5, errors.size)
        assertTrue(errors.all { error -> !error.canBeAutoCorrected })
    }

    @Test
    fun fullyQualifiedLoggerFactoryCallIsFlagged() {
        val errors = RuleTestSupport.lintRule(
            ruleProvider,
            "fun log() = org.slf4j.LoggerFactory.getLogger(\"sample\").info(\"message\")\n"
        )
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
    }

    @Test
    fun typedLoggerPropertyIsFlagged() {
        assertEquals(
            1,
            RuleTestSupport.lintRule(
                ruleProvider,
                """
                import org.slf4j.Logger

                val logger: Logger = TODO()
                fun log() {
                    logger.info("message")
                }
                """.trimIndent() + "\n"
            ).size
        )
    }

    @Test
    fun typedFunctionParameterIsFlagged() {
        val errors = RuleTestSupport.lintRule(
            ruleProvider,
            """
            import org.slf4j.Logger

            fun log(logger: Logger) {
                logger.info("message")
            }
            """.trimIndent() + "\n"
        )
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
    }

    @Test
    fun fluentLoggingIsSafe() {
        assertTrue(
            RuleTestSupport.lintRule(
                ruleProvider,
                """
                import org.slf4j.LoggerFactory

                val logger = LoggerFactory.getLogger("sample")
                fun log() {
                    logger.atInfo().log("message")
                }
                """.trimIndent() + "\n"
            ).isEmpty()
        )
    }

    @Test
    fun unrelatedReceiverIsSafe() {
        assertTrue(RuleTestSupport.lintRule(ruleProvider, "fun log() = other.info(\"message\")\n").isEmpty())
    }

    @Test
    fun nullableSafeCallLoggerParameterIsFlagged() {
        val errors = RuleTestSupport.lintRule(
            ruleProvider,
            """
            import org.slf4j.Logger

            fun log(logger: Logger?) {
                logger?.info("message")
            }
            """.trimIndent() + "\n"
        )
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
    }

    @Test
    fun nonNullAssertedLoggerParameterIsFlagged() {
        val errors = RuleTestSupport.lintRule(
            ruleProvider,
            """
            import org.slf4j.Logger

            fun log(logger: Logger?) {
                logger!!.info("message")
            }
            """.trimIndent() + "\n"
        )
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
    }

    @Test
    fun nullableFullyQualifiedLoggerParameterIsFlagged() {
        val errors = RuleTestSupport.lintRule(
            ruleProvider,
            """
            fun log(logger: org.slf4j.Logger?) {
                logger?.info("message")
            }
            """.trimIndent() + "\n"
        )
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
    }

    @Test
    fun spacedFullyQualifiedNullableLoggerParameterIsFlagged() {
        val errors = RuleTestSupport.lintRule(
            ruleProvider,
            """
            fun log(logger: org . slf4j . Logger?) {
                logger?.info("message")
            }
            """.trimIndent() + "\n"
        )
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
    }

    @Test
    fun deeperQualifiedNullableLoggerParameterIsSafe() {
        val errors = RuleTestSupport.lintRule(
            ruleProvider,
            """
            fun log(logger: other.org.slf4j.Logger?) {
                logger?.info("message")
            }
            """.trimIndent() + "\n"
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun unrelatedNullableSafeCallIsSafe() {
        assertTrue(
            RuleTestSupport.lintRule(
                ruleProvider,
                """
                class Service {
                    fun info(message: String) {}
                }

                fun log(service: Service?) {
                    service?.info("message")
                }
                """.trimIndent() + "\n"
            ).isEmpty()
        )
    }

    @Test
    fun unrelatedNonNullAssertedReceiverIsSafe() {
        assertTrue(
            RuleTestSupport.lintRule(
                ruleProvider,
                """
                class Service {
                    fun info(message: String) {}
                }

                fun log(service: Service?) {
                    service!!.info("message")
                }
                """.trimIndent() + "\n"
            ).isEmpty()
        )
    }
}
