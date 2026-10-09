package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class SlfDirectLoggingTest :
    FunSpec({
        test("each direct log level is flagged") {
            KtLintRuleTestEngine
                .execute(
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
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        5,
                        12,
                        "direct SLF4J logging `trace`; use `logger.atTrace()` fluent logging",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        6,
                        12,
                        "direct SLF4J logging `debug`; use `logger.atDebug()` fluent logging",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        7,
                        12,
                        "direct SLF4J logging `info`; use `logger.atInfo()` fluent logging",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        8,
                        12,
                        "direct SLF4J logging `warn`; use `logger.atWarn()` fluent logging",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        9,
                        12,
                        "direct SLF4J logging `error`; use `logger.atError()` fluent logging",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("fully qualified logger factory call is flagged") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    fun log() = org.slf4j.LoggerFactory.getLogger("sample").info("message")
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        57,
                        "direct SLF4J logging `info`; use `org.slf4j.LoggerFactory.getLogger(\"sample\").atInfo()` fluent logging",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("typed logger property is flagged") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    import org.slf4j.Logger

                    val logger: Logger = TODO()
                    fun log() {
                        logger.info("message")
                    }
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        5,
                        12,
                        "direct SLF4J logging `info`; use `logger.atInfo()` fluent logging",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("typed function parameter is flagged") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    import org.slf4j.Logger

                    fun log(logger: Logger) {
                        logger.info("message")
                    }
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        4,
                        12,
                        "direct SLF4J logging `info`; use `logger.atInfo()` fluent logging",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("fluent logging is safe") {
            val source =
                """
                import org.slf4j.LoggerFactory

                val logger = LoggerFactory.getLogger("sample")
                fun log() {
                    logger.atInfo().log("message")
                }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("unrelated receiver is safe") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    fun log() = other.info("message")
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe
                    """
                    fun log() = other.info("message")
                    """.trimIndent() + "\n"
            }
        }

        test("nullable safe call logger parameter is flagged") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    import org.slf4j.Logger

                    fun log(logger: Logger?) {
                        logger?.info("message")
                    }
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        4,
                        13,
                        "direct SLF4J logging `info`; use `logger.atInfo()` fluent logging",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("nullable fully qualified logger parameter is flagged") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    fun log(logger: org.slf4j.Logger?) {
                        logger?.info("message")
                    }
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        2,
                        13,
                        "direct SLF4J logging `info`; use `logger.atInfo()` fluent logging",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("unrelated nullable safe call is safe") {
            val source =
                """
                class Service {
                    fun info(message: String) {}
                }

                fun log(service: Service?) {
                    service?.info("message")
                }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::SlfDirectLogging)
    }
}
