package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.isPartOfComment20
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiErrorElement
import org.jetbrains.kotlin.psi.KtAnonymousInitializer
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.kotlin.psi.KtUserType

/**
 * Identifies proven test and lifecycle hook registrations in imported Kotest StringSpec and FunSpec scopes.
 */
internal object KotestRegistrations {
    private const val STRING_SPEC: String = "io.kotest.core.spec.style.StringSpec"
    private const val FUN_SPEC: String = "io.kotest.core.spec.style.FunSpec"
    private val hooks: Set<String> =
        setOf("afterAny", "afterContainer", "afterEach", "afterSpec", "afterTest") +
            setOf("beforeAny", "beforeContainer", "beforeEach", "beforeSpec", "beforeTest")

    /**
     * Returns true when the block directly registers tests or lifecycle hooks of a proven Kotest spec.
     */
    fun isRegistrationScope(block: KtBlockExpression): Boolean = registrationStyle(block) !== null

    /**
     * Returns true only when a whitespace node separates adjacent registration expressions.
     */
    fun isRegistrationSeparator(node: ASTNode): Boolean =
        (node.psi.parent as? KtBlockExpression)?.let { block ->
            val previous = node.treePrev?.psi as? KtExpression
            val next = node.treeNext?.psi as? KtExpression
            previous !== null &&
                next !== null &&
                node.treePrev?.isPartOfComment20 != true &&
                node.treeNext?.isPartOfComment20 != true &&
                areAdjacentStatements(block, previous, next) &&
                !hasParserError(previous) &&
                !hasParserError(next) &&
                registrationStyle(block)?.let { style ->
                    isRegistration(block, previous, style) && isRegistration(block, next, style)
                } == true
        } == true

    private enum class SpecStyle { STRING, FUN }

    private fun areAdjacentStatements(
        block: KtBlockExpression,
        previous: KtExpression,
        next: KtExpression
    ): Boolean =
        block.statements.indexOf(previous).let { previousIndex ->
            0 <= previousIndex && block.statements.getOrNull(previousIndex + 1) === next
        }

    private fun registrationStyle(block: KtBlockExpression): SpecStyle? {
        val lambda = lambdaFor(block)
        return constructorLambdaStyle(lambda) ?: initBlockStyle(block) ?: nestedContextStyle(lambda)
    }

    private fun nestedContextStyle(lambda: KtLambdaExpression?): SpecStyle? =
        lambda
            ?.let(::enclosingContextCall)
            ?.let { contextCall ->
                (contextCall.parent as? KtBlockExpression)?.let { parentBlock ->
                    registrationStyle(parentBlock)?.takeIf { style ->
                        style == SpecStyle.FUN && isFunRegistration(parentBlock, contextCall)
                    }
                }
            }

    private fun lambdaFor(block: KtBlockExpression): KtLambdaExpression? =
        (block.parent as? KtFunctionLiteral)?.let { literal ->
            (literal.parent as? KtLambdaExpression)
                ?.takeIf { lambda -> lambda.functionLiteral === literal && literal.bodyExpression === block }
        }

    private fun constructorLambdaStyle(lambda: KtLambdaExpression?): SpecStyle? =
        lambda
            ?.let { constructorLambda ->
                generateSequence(constructorLambda.parent, PsiElement::getParent)
                    .firstOrNull { parent -> parent is KtSuperTypeCallEntry || parent is KtLambdaExpression }
                    ?.let { parent -> parent as? KtSuperTypeCallEntry }
                    ?.takeIf { entry ->
                        entry.valueArgumentList?.arguments?.any { argument -> argument.getArgumentExpression() === constructorLambda } ==
                            true
                    }
            }?.let { entry ->
                generateSequence(entry.parent, PsiElement::getParent)
                    .filterIsInstance<KtClass>()
                    .firstOrNull()
                    ?.takeIf { spec -> spec.superTypeListEntries.any { superType -> superType === entry } }
                    ?.let { spec -> importedSpecStyle(entry.typeReference?.typeElement as? KtUserType, spec) }
            }

    private fun initBlockStyle(block: KtBlockExpression): SpecStyle? =
        (block.parent as? KtAnonymousInitializer)
            ?.let { initializer -> initializer.parent as? KtClassBody }
            ?.let { classBody -> classBody.parent as? KtClass }
            ?.let { spec ->
                spec.superTypeListEntries
                    .filterIsInstance<KtSuperTypeCallEntry>()
                    .singleOrNull()
                    ?.let { entry -> importedSpecStyle(entry.typeReference?.typeElement as? KtUserType, spec) }
            }

    private fun importedSpecStyle(
        type: KtUserType?,
        spec: KtClass
    ): SpecStyle? {
        val file = spec.containingFile as? KtFile
        val visibleName = type?.referencedName
        val imports = file?.importDirectives.orEmpty().filter { directive -> importedName(directive) == visibleName }
        return when {
            type === null || visibleName === null || file === null || type.qualifier !== null -> {
                null
            }

            file.importDirectives.any(KtImportDirective::isAllUnder) || imports.size != 1 -> {
                null
            }

            KotlinLexicalScope.isDeclared(visibleName, type) -> {
                null
            }

            else -> {
                when (imports.single().importedFqName?.asString()) {
                    STRING_SPEC -> SpecStyle.STRING
                    FUN_SPEC -> SpecStyle.FUN
                    else -> null
                }
            }
        }
    }

    private fun importedName(directive: KtImportDirective): String? =
        directive.aliasName ?: directive.importedFqName?.shortName()?.asString()

    private fun enclosingContextCall(lambda: KtLambdaExpression): KtCallExpression? =
        generateSequence(lambda.parent, PsiElement::getParent)
            .firstOrNull { parent -> parent is KtCallExpression || parent is KtLambdaExpression }
            ?.let { parent -> parent as? KtCallExpression }
            ?.takeIf { call ->
                callName(call) == "context" &&
                    call.hasOnlyTrailingLambda() &&
                    call.lambdaArguments.single().getLambdaExpression() === lambda
            }?.takeIf { call ->
                (call.parent as? KtBlockExpression)?.statements?.any { statement -> statement === call } == true
            }

    private fun isRegistration(
        block: KtBlockExpression,
        expression: KtExpression,
        style: SpecStyle
    ): Boolean =
        when (style) {
            SpecStyle.STRING -> isStringTest(block, expression) || isHook(block, expression)
            SpecStyle.FUN -> isFunRegistration(block, expression) || isHook(block, expression)
        }

    private fun isStringTest(
        block: KtBlockExpression,
        expression: KtExpression
    ): Boolean =
        when (expression) {
            is KtCallExpression -> isStringLiteralTest(block, expression) || isStringConfig(block, expression)
            is KtDotQualifiedExpression -> isStringConfig(block, expression)
            else -> false
        }

    private fun isStringLiteralTest(
        block: KtBlockExpression,
        expression: KtCallExpression
    ): Boolean =
        expression.calleeExpression is KtStringTemplateExpression &&
            expression.valueArgumentList?.arguments.isNullOrEmpty() &&
            expression.hasOnlyTrailingLambda() &&
            !isShadowed("invoke", block)

    private fun isStringConfig(
        block: KtBlockExpression,
        expression: KtExpression
    ): Boolean {
        val qualified = expression as? KtDotQualifiedExpression
        val call = expression as? KtCallExpression
        val callee = call?.calleeExpression as? KtDotQualifiedExpression
        val receiver = (qualified?.receiverExpression ?: callee?.receiverExpression) as? KtStringTemplateExpression
        val configCall = qualified?.selectorExpression as? KtCallExpression
        val configName =
            when {
                qualified !== null -> callName(configCall)
                else -> (callee?.selectorExpression as? KtNameReferenceExpression)?.getReferencedName()
            }
        val expressionCall = configCall ?: call
        return receiver !== null &&
            configName == "config" &&
            expressionCall?.valueArgumentList !== null &&
            expressionCall.hasOnlyTrailingLambda() &&
            !isShadowed("config", block)
    }

    private fun isFunRegistration(
        block: KtBlockExpression,
        expression: KtExpression
    ): Boolean =
        (expression as? KtCallExpression)?.let { call ->
            callName(call)?.let { name ->
                name in setOf("context", "test") && call.hasOnlyTrailingLambda() && !isShadowed(name, block)
            }
        } == true

    private fun isHook(
        block: KtBlockExpression,
        expression: KtExpression
    ): Boolean =
        (expression as? KtCallExpression)?.let { call ->
            callName(call)?.let { name ->
                name in hooks && call.hasOnlyTrailingLambda() && !isShadowed(name, block)
            }
        } == true

    private fun callName(call: KtCallExpression?): String? = (call?.calleeExpression as? KtNameReferenceExpression)?.getReferencedName()

    private fun KtCallExpression.hasOnlyTrailingLambda(): Boolean =
        lambdaArguments.size == 1 &&
            valueArgumentList?.arguments?.none { argument ->
                argument.getArgumentExpression() is KtLambdaExpression
            } != false

    private fun isShadowed(
        name: String,
        block: KtBlockExpression
    ): Boolean =
        (block.containingFile as? KtFile)?.let { file ->
            file.importDirectives.any { directive -> directive.isAllUnder || importedName(directive) == name } ||
                KotlinLexicalScope.isDeclared(name, block)
        } != false

    private fun hasParserError(element: PsiElement): Boolean = element is PsiErrorElement || element.children.any(::hasParserError)
}
