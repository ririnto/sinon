package ai.harness.gradle.rules

import ai.harness.gradle.HarnessPsiResults.Finding
import ai.harness.gradle.PsiFinding
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtUserType
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.relativeTo

/**
 * Rule that reports inline fully qualified Kotlin names.
 */
object ImportOverFqnRule : HarnessCheckRule() {
    override val category: String = "importOverFqn"

    /**
     * Finds Kotlin PSI import-over-FQN findings for one source file.
     */
    override fun findPsiFindings(
        file: Path,
        root: Path,
        psiFactory: KtPsiFactory?,
    ): List<PsiFinding> = buildList {
        val ktFile = psiFactory?.createFile("temp", file.readText())
        if (ktFile != null) {
            val importedNames =
                ktFile.importDirectives
                    .mapNotNull { directive ->
                        directive.importedName?.asString()
                    }.toSet()
            fun addFqnFinding(name: String, element: PsiElement) {
                val simpleName = name.substringAfterLast('.')
                if (isPackageQualifiedName(name) && simpleName !in importedNames) {
                    add(
                        PsiFinding(
                            rule = category,
                            file = file.relativeTo(root).toString().replace("\\", "/"),
                            line = lineOf(ktFile.text, element.node?.startOffset),
                            details = mapOf("name" to name),
                        ),
                    )
                }
            }
            ktFile.accept(
                object : KtTreeVisitorVoid() {
                    override fun visitUserType(userType: KtUserType) {
                        super.visitUserType(userType)
                        if (generateSequence(userType as PsiElement?) { element -> element.parent }.any { element -> element is KtImportDirective }) {
                            return
                        }
                        if (userType.parent is KtUserType) {
                            return
                        }
                        val fqnParts =
                            generateSequence(userType) { parent -> parent.qualifier }
                                .mapNotNull { ut ->
                                    ut.referencedName
                                }.toList()
                                .asReversed()
                        if (2 <= fqnParts.size && fqnParts.first() !in importedNames) {
                            addFqnFinding(fqnParts.joinToString("."), userType)
                        }
                    }

                    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
                        super.visitDotQualifiedExpression(expression)
                        if (generateSequence(expression as PsiElement?) { element -> element.parent }.any { element -> element is KtImportDirective }) {
                            return
                        }
                        if (expression.parent is KtDotQualifiedExpression) {
                            return
                        }
                        addFqnFinding(expression.receiverExpression.text, expression.receiverExpression)
                    }
                },
            )
        }
    }

    override fun applies(manifest: JsonObject): Boolean {
        return manifest[category]
            ?.jsonObject
            ?.get("enabled")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.toBoolean()
            ?: true
    }

    override fun validate(
        manifest: JsonObject,
        root: Path,
    ): Collection<Finding> = emptyList()

    override fun renderPsiFindings(
        findings: List<PsiFinding>,
        manifest: JsonObject,
    ): Collection<Finding> = PsiFindingRenderer.renderEach(findings, manifest)

    private fun isPackageQualifiedName(name: String): Boolean {
        val parts = name.split('.')
        return 3 <= parts.size && parts[0].firstOrNull()?.isLowerCase() == true &&
            parts[1].firstOrNull()?.isLowerCase() == true &&
            parts.last().firstOrNull()?.isUpperCase() == true
    }

    private fun lineOf(
        text: String,
        offset: Int?,
    ): Int {
        if (offset == null || offset < 0) {
            return -1
        }
        return text.take(offset).count { ch -> ch == '\n' } + 1
    }
}
