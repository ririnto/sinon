package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSafeQualifiedExpression

/**
 * Requires Kotest matchers for null and Boolean assertions in Kotlin test sources.
 */
class KotestTestAssertions :
    Rule(
        ruleId = RuleId("harness:kotest-test-assertions"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    private companion object {
        val ASSERTION_FUNCTIONS: Set<String> = setOf("assert", "require", "check", "requireNotNull", "checkNotNull")
        val KOTLIN_TEST_BOOLEAN_ASSERTIONS: Map<String, String> =
            mapOf("assertTrue" to "shouldBeTrue", "assertFalse" to "shouldBeFalse")
    }

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        val expression = node.psi
        val file = expression.containingFile as? KtFile ?: return
        if (!file.isTestSource()) {
            return
        }
        when (expression) {
            is KtCallExpression -> {
                val message =
                    expression.kotlinTestBooleanAssertionName(file)?.let { matcher ->
                        "use Kotest `$matcher()` on the asserted condition"
                    } ?: expression.stdlibAssertionName(file)?.let { name ->
                        when (name) {
                            "requireNotNull", "checkNotNull" -> "use Kotest `shouldNotBeNull()` for null assertions"
                            else -> "use Kotest `shouldBeTrue()` on the asserted condition"
                        }
                    }
                message?.let { diagnosticMessage ->
                    emit(expression.textOffset, diagnosticMessage, false)
                }
            }

            is KtSafeQualifiedExpression -> {
                if (expression.isAssertedSafeCall(file)) {
                    emit(
                        expression.textOffset,
                        "use Kotest `shouldNotBeNull()` before asserting a nullable value",
                        false
                    )
                }
            }
        }
    }

    private fun KtFile.isTestSource(): Boolean =
        virtualFilePath
            .orEmpty()
            .replace('\\', '/')
            .split('/')
            .let { segments ->
                segments.indices.any { index ->
                    segments[index].equals("src", ignoreCase = true) &&
                        segments.getOrNull(index + 1)?.isTestSourceSet() == true
                }
            }

    private fun String.isTestSourceSet(): Boolean =
        equals("test", ignoreCase = true) ||
            equals("androidTest", ignoreCase = true) ||
            equals("commonTest", ignoreCase = true) ||
            startsWith("test", ignoreCase = true) ||
            endsWith("Test", ignoreCase = true) ||
            endsWith("Tests", ignoreCase = true)

    private fun KtCallExpression.stdlibAssertionName(file: KtFile): String? {
        val reference = calleeExpression as? KtNameReferenceExpression ?: return null
        val callName = reference.getReferencedName()
        val qualified = parent as? KtQualifiedExpression
        if (qualified?.selectorExpression === this) {
            return if (
                file.isKotlinPackageReceiver(qualified.receiverExpression) &&
                callName in ASSERTION_FUNCTIONS
            ) {
                callName
            } else {
                null
            }
        }
        if (KotlinLexicalScope.isDeclared(callName, this)) {
            return null
        }
        val imported =
            file.importDirectives
                .mapNotNull { directive ->
                    val path = directive.importedFqName?.asString() ?: return@mapNotNull null
                    val alias = directive.aliasName
                    val importedName = alias ?: path.substringAfterLast('.')
                    if (importedName == callName) {
                        path
                    } else {
                        null
                    }
                }.distinct()
        if (imported.isNotEmpty()) {
            return imported
                .singleOrNull()
                ?.takeIf { path -> path in ASSERTION_FUNCTIONS.map { name -> "kotlin.$name" } }
                ?.substringAfterLast('.')
        }
        return callName.takeIf { name -> name in ASSERTION_FUNCTIONS }
    }

    private fun KtCallExpression.kotlinTestBooleanAssertionName(file: KtFile): String? {
        val reference = calleeExpression as? KtNameReferenceExpression ?: return null
        val callName = reference.getReferencedName()
        if (KotlinLexicalScope.isDeclared(callName, this)) {
            return null
        }
        val matcher = KOTLIN_TEST_BOOLEAN_ASSERTIONS[callName]
        val qualified = parent as? KtQualifiedExpression
        if (qualified?.selectorExpression === this) {
            return matcher.takeIf { file.isKotlinTestPackageReceiver(qualified.receiverExpression) }
        }
        val explicitImports =
            file.importDirectives.filter { directive ->
                val path = directive.importedFqName?.asString().orEmpty()
                !directive.isAllUnder && (directive.aliasName ?: path.substringAfterLast('.')) == callName
            }
        if (explicitImports.isNotEmpty()) {
            val path = explicitImports.singleOrNull()?.importedFqName?.asString() ?: return null
            val importedName = path.substringAfterLast('.')
            return KOTLIN_TEST_BOOLEAN_ASSERTIONS[importedName].takeIf { path == "kotlin.test.$importedName" }
        }
        return matcher?.takeIf {
            file.importDirectives.any { directive ->
                directive.isAllUnder && directive.importedFqName?.asString() == "kotlin.test"
            }
        }
    }

    private fun KtFile.isKotlinPackageReceiver(expression: KtExpression): Boolean =
        expression.text == "kotlin" &&
            !KotlinLexicalScope.isDeclared("kotlin", expression) &&
            importDirectives.none { directive ->
                val path = directive.importedFqName?.asString().orEmpty()
                val importedName = directive.aliasName ?: path.substringAfterLast('.')
                !directive.isAllUnder && importedName == "kotlin"
            }

    private fun KtFile.isKotlinTestPackageReceiver(expression: KtExpression): Boolean =
        expression.text == "kotlin.test" &&
            !KotlinLexicalScope.isDeclared("kotlin", expression) &&
            importDirectives.none { directive ->
                val path = directive.importedFqName?.asString().orEmpty()
                val importedName = directive.aliasName ?: path.substringAfterLast('.')
                !directive.isAllUnder && importedName == "kotlin"
            }

    private fun KtSafeQualifiedExpression.isAssertedSafeCall(file: KtFile): Boolean {
        (selectorExpression as? KtCallExpression)?.let { call ->
            val name = call.calleeExpression?.text.orEmpty()
            if (file.importsKotestAssertion(name, call)) {
                return true
            }
        }
        var assertedExpression: KtExpression = this
        while (assertedExpression.parent is KtParenthesizedExpression) {
            assertedExpression = assertedExpression.parent as KtParenthesizedExpression
        }
        val parent = assertedExpression.parent
        if (parent is KtDotQualifiedExpression) {
            val call = parent.selectorExpression as? KtCallExpression
            if (parent.receiverExpression === assertedExpression && call !== null) {
                val name = call.calleeExpression?.text.orEmpty()
                if (file.importsKotestAssertion(name, call)) {
                    return true
                }
            }
        }
        if (parent is KtBinaryExpression) {
            val name = parent.operationReference.text
            return parent.left === assertedExpression && file.importsKotestAssertion(name, parent)
        }
        return false
    }

    private fun KtFile.importsKotestAssertion(
        name: String,
        expression: KtExpression
    ): Boolean {
        if (KotlinLexicalScope.isDeclared(name, expression)) {
            return false
        }
        val explicitImports =
            importDirectives.filter { directive ->
                val path = directive.importedFqName?.asString().orEmpty()
                !directive.isAllUnder && (directive.aliasName ?: path.substringAfterLast('.')) == name
            }
        if (explicitImports.isNotEmpty()) {
            return explicitImports.singleOrNull()?.let { directive ->
                val path = directive.importedFqName?.asString().orEmpty()
                val originalName = path.substringAfterLast('.')
                val kotestPath = path == "io.kotest.matchers" || path.startsWith("io.kotest.matchers.")
                kotestPath && originalName.startsWith("should")
            } == true
        }
        return name.startsWith("should") &&
            importDirectives.any { directive ->
                val path = directive.importedFqName?.asString().orEmpty()
                directive.isAllUnder &&
                    (path == "io.kotest.matchers" || path.startsWith("io.kotest.matchers."))
            }
    }
}
