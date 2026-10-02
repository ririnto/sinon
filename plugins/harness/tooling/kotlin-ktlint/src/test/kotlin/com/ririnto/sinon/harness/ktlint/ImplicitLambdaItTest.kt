package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class ImplicitLambdaItTest :
    FunSpec({
        test("names implicit parameter and preserves escapes") {
            val source =
                """
                fun render(items: List<String>) = items.joinToString { "\\n${'$'}it" }
                """.trimIndent() + "\n"
            val expected =
                """
                fun render(items: List<String>) = items.joinToString { value -> "\\n${'$'}value" }
                """.trimIndent() + "\n"
            val itOffset = source.indexOf("it", source.indexOf('{'))
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        itOffset + 1,
                        "use an explicit name for the implicit `it` lambda parameter",
                        canBeAutoCorrected = true
                    )
                )
            lintResult1.formattedCode shouldBe expected
        }

        test("preserves outer implicit capture inside nested explicit lambda") {
            val source =
                """
                fun nested(values: List<String>) = values.map { it.let { item -> it + item } }
                """.trimIndent() + "\n"
            val expected =
                """
                fun nested(values: List<String>) = values.map { value -> value.let { item -> value + item } }
                """.trimIndent() + "\n"
            val itOffset = source.indexOf("it", source.indexOf('{'))
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        itOffset + 1,
                        "use an explicit name for the implicit `it` lambda parameter",
                        canBeAutoCorrected = true
                    )
                )
            lintResult1.formattedCode shouldBe expected
        }

        test("reports only the lambda with an implicit parameter") {
            val source =
                """
                fun nested(values: List<List<Int>>) = values.map {
                    it.flatMap { inner -> inner + it.size }
                }
                """.trimIndent() + "\n"
            val itOffset = source.indexOf("it", source.indexOf('{'))
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    source
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        2,
                        itOffset - source.indexOf('\n'),
                        "use an explicit name for the implicit `it` lambda parameter",
                        canBeAutoCorrected = true
                    )
                )
        }

        test("preserves nested implicit shadowing") {
            val source =
                """
                fun nested(values: List<List<Int>>) = values.map { it.map { it + 1 } }
                """.trimIndent() + "\n"
            val expected =
                """
                fun nested(values: List<List<Int>>) = values.map { valueValue -> valueValue.map { value -> value + 1 } }
                """.trimIndent() + "\n"
            val itOffset = source.indexOf("it", source.indexOf('{'))
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(1, itOffset + 1, "use an explicit name for the implicit `it` lambda parameter", true),
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        source.lastIndexOf("it") + 1,
                        "use an explicit name for the implicit `it` lambda parameter",
                        true
                    )
                )
            lintResult1.formattedCode shouldBe expected
        }

        test("preserves multiline escaped string template references") {
            val source =
                """
                fun render(items: List<String>) = items.joinToString {
                    "\\n${'$'}it"
                }
                """.trimIndent() + "\n"
            val expected =
                """
                fun render(items: List<String>) = items.joinToString {
                    value ->
                    "\\n${'$'}value"
                }
                """.trimIndent() + "\n"
            val itOffset = source.indexOf("it", source.indexOf('{'))
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        2,
                        itOffset - source.indexOf('\n'),
                        "use an explicit name for the implicit `it` lambda parameter",
                        canBeAutoCorrected = true
                    )
                )
            lintResult1.formattedCode shouldBe expected
        }

        test("preserves raw string template references") {
            val rawQuote = "\"\"\""
            val source =
                """
                fun render(items: List<String>) = items.joinToString { $rawQuote${'$'}it$rawQuote }
                """.trimIndent() + "\n"
            val expected =
                """
                fun render(items: List<String>) = items.joinToString { value -> $rawQuote${'$'}value$rawQuote }
                """.trimIndent() + "\n"
            val itOffset = source.indexOf("it", source.indexOf('{'))
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        itOffset + 1,
                        "use an explicit name for the implicit `it` lambda parameter",
                        canBeAutoCorrected = true
                    )
                )
            lintResult1.formattedCode shouldBe expected
        }

        test("does not autocorrect when parameter name would capture an existing reference") {
            val source =
                """
                fun render(items: List<String>, value: String) = items.joinToString { value + it }
                """.trimIndent() + "\n"
            val itOffset = source.lastIndexOf("it")
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    source
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        itOffset + 1,
                        "use an explicit name for the implicit `it` lambda parameter",
                        canBeAutoCorrected = true
                    )
                )
        }

        test("preserves member names while renaming the implicit receiver") {
            val source = "fun render(items: List<Item>) = items.map { it.it + it?.it + holder.it }\n"
            val lintResult = KtLintRuleTestEngine.execute(ruleProvider, source)
            lintResult.diagnostics.size shouldBe 1
            lintResult.formattedCode shouldBe
                "fun render(items: List<Item>) = items.map { value -> value.it + value?.it + holder.it }\n"
        }

        test("does not introduce lambda parameters for qualified members") {
            val source = "fun render() = run { holder.it + holder?.it + holder.it() + holder::it }\n"
            val lintResult = KtLintRuleTestEngine.execute(ruleProvider, source)
            lintResult.diagnostics shouldContainExactlyInAnyOrder emptyList()
            lintResult.formattedCode shouldBe source
        }

        test("preserves local shadowing and renames the captured initializer") {
            val source = "fun render(items: List<Int>) = items.map { val it = it + 1; it * 2 }\n"
            val lintResult = KtLintRuleTestEngine.execute(ruleProvider, source)
            lintResult.diagnostics.size shouldBe 1
            lintResult.formattedCode shouldBe
                "fun render(items: List<Int>) = items.map { value -> val it = value + 1; it * 2 }\n"
        }

        test("preserves shadowed function loop and destructuring parameters") {
            val source =
                """
                fun render(items: List<Int>) = items.map {
                    fun local(it: Int): Int = it + 1
                    for (it in items) consume(it)
                    run { val (it, other) = pair; consume(it + other) }
                    consume(it)
                }
                """.trimIndent() + "\n"
            val lintResult = KtLintRuleTestEngine.execute(ruleProvider, source)
            lintResult.diagnostics.size shouldBe 1
            lintResult.formattedCode shouldBe
                """
                fun render(items: List<Int>) = items.map {
                    value ->
                    fun local(it: Int): Int = it + 1
                    for (it in items) consume(it)
                    run { val (it, other) = pair; consume(it + other) }
                    consume(value)
                }
                """.trimIndent() + "\n"
        }

        test("preserves catch parameters and when subjects while renaming their inputs") {
            val source =
                """
                fun render(items: List<Int>) = items.map {
                    try { consume(it) } catch (it: Exception) { report(it) }
                    when (val it = it + 1) { else -> consume(it) }
                }
                """.trimIndent() + "\n"
            val lintResult = KtLintRuleTestEngine.execute(ruleProvider, source)
            lintResult.diagnostics.size shouldBe 1
            lintResult.formattedCode shouldBe
                """
                fun render(items: List<Int>) = items.map {
                    value ->
                    try { consume(value) } catch (it: Exception) { report(it) }
                    when (val it = value + 1) { else -> consume(it) }
                }
                """.trimIndent() + "\n"
        }

        test("local callable and type names do not shadow the implicit value") {
            val source =
                """
                fun render(items: List<Int>) = items.map { fun it(): Int { return 1 }; consume(it); it() }
                fun renderTypes(items: List<Int>) = items.map { class it {}; consume(it); it() }
                """.trimIndent() + "\n"
            val lintResult = KtLintRuleTestEngine.execute(ruleProvider, source)
            lintResult.diagnostics.size shouldBe 2
            lintResult.formattedCode shouldBe
                """
                fun render(items: List<Int>) = items.map { value -> fun it(): Int { return 1 }; consume(value); it() }
                fun renderTypes(items: List<Int>) = items.map { value -> class it {}; consume(value); it() }
                """.trimIndent() + "\n"
        }

        test("does not introduce parameters when a local declaration supplies it") {
            val source =
                """
                fun render() = run {
                    val it = 1
                    consume(it)
                    for (it in values) consume(it)
                    fun local(it: Int): Int = it
                    class Item(val it: Int) { fun value(): Int = it }
                    val item = object { val it: Int = 2; fun value(): Int = it }
                    fun it(): Int = 3
                    consume(it())
                }
                """.trimIndent() + "\n"
            val lintResult = KtLintRuleTestEngine.execute(ruleProvider, source)
            lintResult.diagnostics shouldContainExactlyInAnyOrder emptyList()
            lintResult.formattedCode shouldBe source
        }

        test("accepts named parameters and nested triple quote limitations") {
            val source =
                """
                fun render(items: List<String>) = items.joinToString { value -> value }
                """.trimIndent() + "\n"
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder emptyList()
            lintResult1.formattedCode shouldBe source
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::ImplicitLambdaIt)
    }
}
