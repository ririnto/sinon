package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

/**
 * Verifies the slf direct logging rule.
 */
class SlfDirectLoggingTest :
    FunSpec({
        test("plain constructor parameters shadow outer loggers only during initialization") {
            val source =
                """
                import org.slf4j.Logger

                val logger: Logger = TODO()
                class Service(logger: Sink) {
                    val value: Unit = logger.info("property")
                    init {
                        logger.info("init")
                    }
                    fun record() {
                        logger.info("member")
                    }
                }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            10,
                            16,
                            "harness:slf-direct-logging",
                            false
                        )
                    )
                formattedCode shouldBe source
            }
        }

        test("plain constructor sinks shadow outer loggers in property delegates only") {
            val source =
                """
                import org.slf4j.Logger

                class Sink {
                    fun info(message: String): Lazy<String> = lazy { message }
                }

                val logger: Logger = TODO()
                class Service(logger: Sink) {
                    val value: String by logger.info("delegate")
                    fun record() {
                        logger.info("member")
                    }
                }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            11,
                            16,
                            "harness:slf-direct-logging",
                            false
                        )
                    )
                formattedCode shouldBe source
            }
        }

        test("plain constructor loggers shadow outer sinks in property delegate lambdas only") {
            val source =
                """
                import org.slf4j.Logger

                class Sink {
                    fun info(message: String) {}
                }

                val logger: Sink = TODO()
                class Service(logger: Logger) {
                    val value: Unit by lazy { logger.info("delegate") }
                    fun record() {
                        logger.info("member")
                    }
                }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            9,
                            38,
                            "harness:slf-direct-logging",
                            false
                        )
                    )
                formattedCode shouldBe source
            }
        }

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
                        "harness:slf-direct-logging",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        6,
                        12,
                        "harness:slf-direct-logging",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        7,
                        12,
                        "harness:slf-direct-logging",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        8,
                        12,
                        "harness:slf-direct-logging",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        9,
                        12,
                        "harness:slf-direct-logging",
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
                        "harness:slf-direct-logging",
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
                        "harness:slf-direct-logging",
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
                        "harness:slf-direct-logging",
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
                        "harness:slf-direct-logging",
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
                        "harness:slf-direct-logging",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("same named non logger in another class is safe") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    import org.slf4j.Logger

                    class Audited(private val log: Logger) {
                        fun record() {
                            log.info("message")
                        }
                    }

                    class Journal(private val log: Journal.Sink) {
                        fun record() {
                            log.info("message")
                        }

                        class Sink {
                            fun info(message: String) {}
                        }
                    }
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        5,
                        13,
                        "harness:slf-direct-logging",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("shadowing parameter or local value is safe") {
            val source =
                """
                import org.slf4j.LoggerFactory

                class Sink {
                    fun info(message: String) {}
                }

                val logger = LoggerFactory.getLogger("sample")

                fun parameter(logger: Sink) {
                    logger.info("message")
                }

                fun local() {
                    val logger = Sink()
                    logger.info("message")
                }

                fun lambda() {
                    listOf(Sink()).forEach { logger ->
                        logger.info("message")
                    }
                }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("companion and member loggers are flagged inside their class") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    import org.slf4j.LoggerFactory

                    class Service {
                        private val member = LoggerFactory.getLogger("member")

                        fun record() {
                            member.info("message")
                            logger.warn("message")
                        }

                        companion object {
                            private val logger = LoggerFactory.getLogger("companion")
                        }
                    }
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        7,
                        16,
                        "harness:slf-direct-logging",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        8,
                        16,
                        "harness:slf-direct-logging",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("factory calls nested in unrelated receivers and initializers are safe") {
            val source =
                """
                import org.slf4j.LoggerFactory

                class Sink {
                    fun info(message: String) {}
                }

                fun log() {
                    val sink = wrap(LoggerFactory.getLogger("sample"))
                    sink.info("message")
                    wrap(LoggerFactory.getLogger("sample")).info("message")
                    val other = run {
                        LoggerFactory.getLogger("sample")
                        Sink()
                    }
                    other.info("message")
                }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("destructured lambda and loop parameters hide outer loggers") {
            val source =
                """
                import org.slf4j.LoggerFactory

                val logger = LoggerFactory.getLogger("sample")

                fun log(pairs: List<Pair<Sink, Sink>>) {
                    pairs.forEach { (logger, other) ->
                        logger.info("message")
                    }
                    for ((logger, other) in pairs) {
                        logger.info("message")
                    }
                }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("a value named LoggerFactory hides the imported factory") {
            val source =
                """
                import org.slf4j.LoggerFactory

                fun log(LoggerFactory: Factory) {
                    val sink = LoggerFactory.getLogger("sample")
                    sink.info("message")
                    LoggerFactory.getLogger("sample").info("message")
                }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
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
