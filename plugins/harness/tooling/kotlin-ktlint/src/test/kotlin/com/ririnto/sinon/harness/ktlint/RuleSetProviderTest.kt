package com.ririnto.sinon.harness.ktlint

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RuleSetProviderTest :
    FunSpec({
        test("registers every harness rule") {
            RuleSetProvider().getRuleProviders().map { provider -> provider.ruleId.value }.toSet() shouldBe
                setOf(
                    "harness:companion-object-position",
                    "harness:comparison-direction",
                    "harness:control-flow-braces",
                    "harness:explicit-function-return-type",
                    "harness:explicit-property-type",
                    "harness:explicit-unit-branch",
                    "harness:function-body-blank-lines",
                    "harness:implicit-lambda-it",
                    "harness:import-over-fqn",
                    "harness:kotlin-top-level-declaration-count",
                    "harness:leading-underscore",
                    "harness:multiline-kdoc",
                    "harness:mid-function-exit",
                    "harness:nested-data-class-last",
                    "harness:no-import-alias",
                    "harness:no-java-path-api",
                    "harness:no-line-comment",
                    "harness:non-null-assertion",
                    "harness:null-comparison-identity",
                    "harness:nullable-elvis-return",
                    "harness:public-declaration-doc-comment",
                    "harness:no-regex-constructor",
                    "harness:slf-direct-logging",
                    "harness:terminal-branch-when",
                    "harness:unchecked-cast-suppression",
                    "harness:unstructured-logging"
                )
        }
    })
