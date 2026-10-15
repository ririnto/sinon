package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtProperty
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
        val KOTLIN_TEST_ASSERTIONS: Map<String, String> =
            mapOf("assertTrue" to "shouldBeTrue", "assertFalse" to "shouldBeFalse", "assertNotNull" to "shouldNotBeNull")
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
                    expression.kotlinTestAssertionName(file)?.let { matcher ->
                        when (matcher) {
                            "shouldNotBeNull" -> "use Kotest `shouldNotBeNull()` for null assertions"
                            else -> "use Kotest `$matcher()` on the asserted condition"
                        }
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
                val sourceRootIndex = segments.indexOfLast { segment -> segment.equals("src", ignoreCase = true) }
                0 <= sourceRootIndex && segments.getOrNull(sourceRootIndex + 1)?.isTestSourceSet() == true
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
        if (mayBeShadowedByInheritedMember(callName, file)) {
            return null
        }
        if (KotlinLexicalScope.isDeclared(callName, this, inaccessibleEnclosingScopes())) {
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

    private fun KtCallExpression.kotlinTestAssertionName(file: KtFile): String? {
        val reference = calleeExpression as? KtNameReferenceExpression ?: return null
        val callName = reference.getReferencedName()
        val matcher = KOTLIN_TEST_ASSERTIONS[callName]
        val qualified = parent as? KtQualifiedExpression
        if (qualified?.selectorExpression === this) {
            return matcher.takeIf { file.isKotlinTestPackageReceiver(qualified.receiverExpression) }
        }
        if (KotlinLexicalScope.isDeclared(callName, this, inaccessibleEnclosingScopes())) {
            return null
        }
        val explicitImports =
            file.importDirectives.filter { directive ->
                val path = directive.importedFqName?.asString().orEmpty()
                !directive.isAllUnder && (directive.aliasName ?: path.substringAfterLast('.')) == callName
            }
        if (explicitImports.isNotEmpty()) {
            val path = explicitImports.singleOrNull()?.importedFqName?.asString() ?: return null
            val importedName = path.substringAfterLast('.')
            val importedMatcher =
                KOTLIN_TEST_ASSERTIONS[importedName]?.takeIf { path == "kotlin.test.$importedName" }
                    ?: return null
            return importedMatcher.takeIf { !mayBeShadowedByInheritedMember(callName, file) }
        }
        val hasKotlinTestWildcard =
            file.importDirectives.any { directive ->
                directive.isAllUnder && directive.importedFqName?.asString() == "kotlin.test"
            }
        val hasOtherWildcard =
            file.importDirectives.any { directive ->
                directive.isAllUnder && directive.importedFqName?.asString() != "kotlin.test"
            }
        return matcher?.takeIf {
            hasKotlinTestWildcard && !hasOtherWildcard && !mayBeShadowedByInheritedMember(callName, file)
        }
    }

    private fun PsiElement.mayBeShadowedByInheritedMember(
        name: String,
        file: KtFile
    ): Boolean =
        visibleEnclosingClassScopes().any { enclosingClass ->
            enclosingClass.inheritedMemberStatus(name, file, mutableSetOf()) != InheritedMemberStatus.NONE
        }

    private fun PsiElement.visibleEnclosingClassScopes(): List<KtClassOrObject> {
        var enclosingScope = parent
        while (enclosingScope !== null && enclosingScope !is KtClassOrObject) {
            enclosingScope = enclosingScope.parent
        }
        var enclosingClass = enclosingScope ?: return emptyList()
        val visibleScopes = mutableListOf(enclosingClass)
        while (
            (enclosingClass as? KtClass)?.hasModifier(KtTokens.INNER_KEYWORD) == true ||
            (enclosingClass as? KtObjectDeclaration)?.isObjectLiteral() == true
        ) {
            var outerScope = enclosingClass.parent
            while (outerScope !== null && outerScope !is KtClassOrObject) {
                outerScope = outerScope.parent
            }
            enclosingClass = outerScope ?: return visibleScopes
            visibleScopes.add(enclosingClass)
        }
        return visibleScopes
    }

    private fun PsiElement.inaccessibleEnclosingScopes(): Set<PsiElement> {
        val visibleClasses = visibleEnclosingClassScopes().toSet()
        val nearestClass = visibleClasses.firstOrNull() ?: return emptySet()
        if (nearestClass.parent !is KtClassBody && (nearestClass as? KtObjectDeclaration)?.isObjectLiteral() != true) {
            return emptySet()
        }
        val inaccessibleScopes = mutableSetOf<PsiElement>()
        generateSequence(parent, { scope -> scope.parent })
            .takeWhile { scope -> scope !is KtFile }
            .filterIsInstance<KtClassOrObject>()
            .forEach { scope ->
                if (scope !in visibleClasses) {
                    inaccessibleScopes.add(scope)
                }
            }
        return inaccessibleScopes
    }

    private enum class InheritedMemberStatus {
        FOUND,
        UNKNOWN,
        NONE
    }

    private fun KtClassOrObject.inheritedMemberStatus(
        name: String,
        file: KtFile,
        visited: MutableSet<String>
    ): InheritedMemberStatus {
        var status = InheritedMemberStatus.NONE
        superTypeListEntries.forEach { entry ->
            val typeText = entry.typeReference?.text ?: return InheritedMemberStatus.UNKNOWN
            if ('.' in typeText) {
                return InheritedMemberStatus.UNKNOWN
            }
            val supertypeName =
                typeText.substringBefore('<')
            if (!visited.add(supertypeName)) {
                return@forEach
            }
            if (
                file.importDirectives.any { directive ->
                    !directive.isAllUnder &&
                        (directive.aliasName ?: directive.importedFqName?.asString()?.substringAfterLast('.')) == supertypeName
                }
            ) {
                status = InheritedMemberStatus.UNKNOWN
                return@forEach
            }
            val declarations =
                file.declarations
                    .filterIsInstance<KtClassOrObject>()
                    .filter { declaration -> declaration.name == supertypeName }
            val supertype = declarations.singleOrNull()
            if (supertype === null) {
                status = InheritedMemberStatus.UNKNOWN
                return@forEach
            }
            if (supertype.declarations.any { declaration ->
                    (declaration is KtNamedFunction || declaration is KtProperty) &&
                        declaration.name == name &&
                        !declaration.hasModifier(KtTokens.PRIVATE_KEYWORD)
                }
            ) {
                return InheritedMemberStatus.FOUND
            }
            if (
                (supertype as? KtClass)?.primaryConstructorParameters?.any { parameter ->
                    parameter.hasValOrVar() &&
                        parameter.name == name &&
                        !parameter.hasModifier(KtTokens.PRIVATE_KEYWORD)
                } == true
            ) {
                return InheritedMemberStatus.FOUND
            }
            val inheritedStatus = supertype.inheritedMemberStatus(name, file, visited)
            if (inheritedStatus == InheritedMemberStatus.FOUND) {
                return InheritedMemberStatus.FOUND
            }
            if (inheritedStatus == InheritedMemberStatus.UNKNOWN) {
                status = InheritedMemberStatus.UNKNOWN
            }
        }
        return status
    }

    private fun KtFile.isKotlinPackageReceiver(expression: KtExpression): Boolean =
        expression.text == "kotlin" &&
            !KotlinLexicalScope.isDeclared("kotlin", expression, expression.inaccessibleEnclosingScopes()) &&
            !expression.mayBeShadowedByInheritedMember("kotlin", this) &&
            importDirectives.none { directive ->
                val path = directive.importedFqName?.asString().orEmpty()
                val importedName = directive.aliasName ?: path.substringAfterLast('.')
                !directive.isAllUnder && importedName == "kotlin"
            }

    private fun KtFile.isKotlinTestPackageReceiver(expression: KtExpression): Boolean =
        expression.text == "kotlin.test" &&
            !KotlinLexicalScope.isDeclared("kotlin", expression, expression.inaccessibleEnclosingScopes()) &&
            !expression.mayBeShadowedByInheritedMember("kotlin", this) &&
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
