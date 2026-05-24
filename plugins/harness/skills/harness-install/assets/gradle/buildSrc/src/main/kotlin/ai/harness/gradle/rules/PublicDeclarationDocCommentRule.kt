package ai.harness.gradle.rules

import ai.harness.gradle.HarnessPsiResults.Finding
import ai.harness.gradle.PsiFinding
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifier
import java.nio.file.Path

/**
 * Rule that delegates public documentation findings to source AST/PSI analysis.
 */
object PublicDeclarationDocCommentRule : HarnessCheckRule() {
    override val category: String = "publicDeclarationDocComment"

    override fun applies(manifest: JsonObject): Boolean {
        return manifest[category]?.jsonObject?.get("enabled")?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
    }

    override fun validate(
        manifest: JsonObject,
        root: Path,
    ): Collection<Finding> = emptyList()

    override fun renderPsiFindings(
        findings: List<PsiFinding>,
        manifest: JsonObject,
    ): Collection<Finding> = PsiFindingRenderer.renderEach(findings, manifest)

    override fun findPsiFindings(
        file: Path,
        root: Path,
        psiFactory: KtPsiFactory?,
    ): List<PsiFinding> = buildList {
        val ktFile = PsiRuleSupport.parse(file, psiFactory)
        if (ktFile != null) {
            ktFile.accept(
                object : KtTreeVisitorVoid() {
                    override fun visitClass(klass: KtClass) {
                        super.visitClass(klass)
                        if (isExternallyVisible(klass) && klass.docComment == null) {
                            add(
                                PsiFinding(
                                    rule = category,
                                    file = PsiRuleSupport.relativeFilePath(file, root),
                                    line = PsiRuleSupport.lineOf(ktFile, klass.node?.startOffset),
                                    details = mapOf("name" to (klass.name ?: "unknown")),
                                ),
                            )
                        }
                    }

                    override fun visitNamedFunction(function: KtNamedFunction) {
                        super.visitNamedFunction(function)
                        if (isExternallyVisible(function) &&
                            !function.hasModifier(KtTokens.OVERRIDE_KEYWORD) &&
                            function.docComment == null
                        ) {
                            add(
                                PsiFinding(
                                    rule = category,
                                    file = PsiRuleSupport.relativeFilePath(file, root),
                                    line = PsiRuleSupport.lineOf(ktFile, function.node?.startOffset),
                                    details = mapOf("name" to (function.name ?: "unknown")),
                                ),
                            )
                        }
                    }

                    override fun visitProperty(property: KtProperty) {
                        super.visitProperty(property)
                        if (!property.isLocal && isExternallyVisible(property) && property.docComment == null) {
                            add(
                                PsiFinding(
                                    rule = category,
                                    file = PsiRuleSupport.relativeFilePath(file, root),
                                    line = PsiRuleSupport.lineOf(ktFile, property.node?.startOffset),
                                    details = mapOf("name" to (property.name ?: "unknown")),
                                ),
                            )
                        }
                    }
                },
            )
        }
    }

    private fun isExternallyVisible(element: KtModifierListOwner): Boolean {
        val visibility = element.visibilityModifier()?.text
        if (visibility == "private" || visibility == "internal") {
            return false
        }
        if (element is KtProperty && element.isLocal) {
            return false
        }
        val parent = element.parent
        if (element is KtNamedFunction && parent is KtBlockExpression) {
            return false
        }
        return true
    }
}
