package ai.harness.gradle.rules

import ai.harness.gradle.HarnessPsiResults.Finding
import ai.harness.gradle.PsiFinding
import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.BodyDeclaration
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.ConstructorDeclaration
import com.github.javaparser.ast.body.EnumDeclaration
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.InitializerDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.api.GradleException
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassInitializer
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifier
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

/**
 * Rule that delegates class-member ordering findings to source AST/PSI analysis.
 */
object ClassMemberOrderingRule : HarnessCheckRule {
    override val category: String = "classMemberOrdering"

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
    ): Collection<Finding> = findJavaClassMemberOrderEntries(root, manifest)

    override fun renderPsiFindings(
        findings: List<PsiFinding>,
        manifest: JsonObject,
    ): Collection<Finding> {
        val parameters = manifest[category]?.jsonObject?.get("parameters")?.jsonObject
        val order =
            ai.harness.gradle.HarnessCheck.stringArrayFrom(parameters, "order").ifEmpty {
                listOf("companionObject", "fieldOrProperty", "initializer", "constructor", "function", "nestedType")
            }
        val rank = order.withIndex().associate { indexed -> indexed.value to indexed.index }
        return findings
            .groupBy { finding -> finding.file to finding.detail("ownerId") }
            .values
            .flatMap { entries ->
                buildList {
                    var maxRank = -1
                    entries.sortedBy { entry -> entry.intDetail("position") }.forEach { entry ->
                        val memberKind = entry.detail("memberKind")
                        val memberVisibility = entry.detail("memberVisibility")
                        val memberOverrideState = entry.detail("memberOverrideState")
                        val memberRank =
                            rank["$memberOverrideState:$memberVisibility:$memberKind"]
                                ?: rank["$memberVisibility:$memberKind"]
                                ?: rank[memberKind]
                                ?: rank.size
                        when {
                            memberRank < maxRank -> add(PsiFindingRenderer.render(entry, manifest))
                            else -> maxRank = memberRank
                        }
                    }
                }
            }
    }

    override fun findPsiFindings(
        file: Path,
        root: Path,
        psiFactory: KtPsiFactory?,
    ): List<PsiFinding> = buildList {
        val ktFile = PsiRuleSupport.parse(file, psiFactory)
        if (ktFile != null) {
            fun addClassMemberOrderEntries(
                ownerId: String,
                className: String,
                declarations: List<KtDeclaration>,
            ) {
                declarations.forEachIndexed { position, declaration ->
                    if (declaration !is KtEnumEntry) {
                        val kind = kotlinMemberKind(declaration)
                        if (kind != null) {
                            add(
                                PsiFinding(
                                    rule = category,
                                    file = PsiRuleSupport.relativeFilePath(file, root),
                                    line = PsiRuleSupport.lineOf(ktFile, declaration.node?.startOffset),
                                    details =
                                        mapOf(
                                            "ownerId" to ownerId,
                                            "className" to className,
                                            "memberName" to kotlinMemberName(declaration),
                                            "memberOverrideState" to kotlinMemberOverrideState(declaration),
                                            "memberVisibility" to kotlinMemberVisibility(declaration),
                                            "memberKind" to kind,
                                            "position" to position.toString(),
                                        ),
                                ),
                            )
                        }
                    }
                }
            }
            ktFile.accept(
                object : KtTreeVisitorVoid() {
                    override fun visitClass(klass: KtClass) {
                        super.visitClass(klass)
                        addClassMemberOrderEntries(
                            kotlinOwnerId(ktFile, klass),
                            klass.name ?: "unknown",
                            klass.getBody()?.declarations ?: emptyList(),
                        )
                    }

                    override fun visitObjectDeclaration(declaration: KtObjectDeclaration) {
                        super.visitObjectDeclaration(declaration)
                        addClassMemberOrderEntries(
                            kotlinOwnerId(ktFile, declaration),
                            declaration.name ?: "object",
                            declaration.getBody()?.declarations ?: emptyList(),
                        )
                    }
                },
            )
        }
    }

    private fun kotlinOwnerId(
        ktFile: KtFile,
        declaration: KtClassOrObject,
    ): String =
        generateSequence(declaration as PsiElement?) { element -> element.parent }
            .filterIsInstance<KtClassOrObject>()
            .map { owner -> kotlinOwnerName(ktFile, owner) }
            .toList()
            .asReversed()
            .joinToString(".")

    private fun kotlinOwnerName(
        ktFile: KtFile,
        declaration: KtClassOrObject,
    ): String =
        when (declaration) {
            is KtObjectDeclaration -> {
                when {
                    declaration.isCompanion() -> "companion"
                    else -> declaration.name ?: "object@${PsiRuleSupport.lineOf(ktFile, declaration.node?.startOffset)}"
                }
            }

            else -> {
                declaration.name ?: "class@${PsiRuleSupport.lineOf(ktFile, declaration.node?.startOffset)}"
            }
        }

    private fun kotlinMemberVisibility(declaration: PsiElement): String =
        when (declaration) {
            is KtModifierListOwner -> {
                when (declaration.visibilityModifier()?.text) {
                    "private" -> "private"
                    "protected" -> "protected"
                    "internal" -> "internal"
                    else -> "public"
                }
            }

            else -> "public"
        }

    private fun kotlinMemberOverrideState(declaration: PsiElement): String =
        when {
            declaration is KtModifierListOwner && declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD) -> "override"
            else -> "nonOverride"
        }

    private fun kotlinMemberKind(declaration: PsiElement): String? =
        when (declaration) {
            is KtObjectDeclaration -> {
                when {
                    declaration.isCompanion() -> "companionObject"
                    else -> "nestedType"
                }
            }

            is KtProperty -> "fieldOrProperty"
            is KtSecondaryConstructor -> "constructor"
            is KtClassInitializer -> "initializer"
            is KtNamedFunction -> "function"
            is KtClass -> "nestedType"
            else -> null
        }

    private fun kotlinMemberName(declaration: PsiElement): String =
        when (declaration) {
            is KtObjectDeclaration -> {
                when {
                    declaration.isCompanion() -> "companion object"
                    else -> declaration.name ?: "object"
                }
            }

            is KtProperty -> declaration.name ?: "property"
            is KtSecondaryConstructor -> "constructor"
            is KtClassInitializer -> "initializer"
            is KtNamedFunction -> declaration.name ?: "function"
            is KtClass -> declaration.name ?: "class"
            else -> "member"
        }

    private fun findJavaClassMemberOrderEntries(
        root: Path,
        manifest: JsonObject,
    ): List<Finding> {
        val sourceRoots =
            listOf(
                root / "buildSrc" / "src" / "main" / "java",
                root / "buildSrc" / "src" / "test" / "java",
                root / "src" / "main" / "java",
                root / "src" / "test" / "java",
            ).filter { sourceRoot -> !sourceRoot.isSymbolicLink() }
                .filter { sourceRoot -> sourceRoot.isDirectory() }
        return sourceRoots
            .flatMap { sourceRoot ->
                sourceRoot
                    .walk()
                    .filter { file -> !file.isSymbolicLink() }
                    .filter { file -> file.isRegularFile() }
                    .filter { file -> file.extension == "java" }
            }.flatMap { file -> findJavaClassMemberOrderEntries(root, file, manifest) }
    }

    private fun findJavaClassMemberOrderEntries(
        root: Path,
        file: Path,
        manifest: JsonObject,
    ): List<Finding> =
        try {
            val compilationUnit = StaticJavaParser.parse(file)
            val classEntries =
                compilationUnit.findAll(ClassOrInterfaceDeclaration::class.java).flatMap { type ->
                    javaClassMemberOrderEntries(root, file, type.nameAsString, javaOwnerId(type), type.members)
                }
            val enumEntries =
                compilationUnit.findAll(EnumDeclaration::class.java).flatMap { type ->
                    javaClassMemberOrderEntries(root, file, type.nameAsString, javaOwnerId(type), type.members)
                }
            renderPsiFindings(classEntries + enumEntries, manifest).toList()
        } catch (error: Exception) {
            throw GradleException("failed to parse Java source ${file.relativeTo(root)}", error)
        }

    private fun javaClassMemberOrderEntries(
        root: Path,
        file: Path,
        className: String,
        ownerId: String,
        members: List<BodyDeclaration<*>>,
    ): List<PsiFinding> =
        buildList {
            members.forEachIndexed { position, member ->
                val kind = javaMemberKind(member)
                if (kind != null) {
                    add(
                        PsiFinding(
                            rule = category,
                            file = file.relativeTo(root).toString().replace("\\", "/"),
                            line = member.begin.map { begin -> begin.line }.orElse(-1),
                            details =
                                mapOf(
                                    "ownerId" to ownerId,
                                    "className" to className,
                                    "memberName" to javaMemberName(member),
                                    "memberOverrideState" to javaMemberOverrideState(member),
                                    "memberVisibility" to javaMemberVisibility(member),
                                    "memberKind" to kind,
                                    "position" to position.toString(),
                                ),
                        ),
                    )
                }
            }
        }

    private fun javaOwnerId(type: Node): String =
        generateSequence(type) { node -> node.parentNode.orElse(null) }
            .filter { node -> node is ClassOrInterfaceDeclaration || node is EnumDeclaration }
            .map { node ->
                when (node) {
                    is ClassOrInterfaceDeclaration -> node.nameAsString
                    is EnumDeclaration -> node.nameAsString
                    else -> "type"
                }
            }.toList()
            .asReversed()
            .joinToString(".")

    private fun javaMemberVisibility(member: BodyDeclaration<*>): String =
        when (member) {
            is FieldDeclaration -> javaVisibility(member.isPrivate, member.isProtected, member.isPublic)
            is ConstructorDeclaration -> javaVisibility(member.isPrivate, member.isProtected, member.isPublic)
            is MethodDeclaration -> javaVisibility(member.isPrivate, member.isProtected, member.isPublic)
            is ClassOrInterfaceDeclaration -> javaVisibility(member.isPrivate, member.isProtected, member.isPublic)
            is EnumDeclaration -> javaVisibility(member.isPrivate, member.isProtected, member.isPublic)
            else -> "package"
        }

    private fun javaVisibility(
        isPrivate: Boolean,
        isProtected: Boolean,
        isPublic: Boolean,
    ): String =
        when {
            isPrivate -> "private"
            isProtected -> "protected"
            isPublic -> "public"
            else -> "package"
        }

    private fun javaMemberOverrideState(member: BodyDeclaration<*>): String =
        when {
            member is MethodDeclaration && member.getAnnotationByName("Override").isPresent -> "override"
            else -> "nonOverride"
        }

    private fun javaMemberKind(member: BodyDeclaration<*>): String? =
        when (member) {
            is FieldDeclaration -> "fieldOrProperty"
            is InitializerDeclaration -> "initializer"
            is ConstructorDeclaration -> "constructor"
            is MethodDeclaration -> "function"
            is ClassOrInterfaceDeclaration -> "nestedType"
            is EnumDeclaration -> "nestedType"
            else -> null
        }

    private fun javaMemberName(member: BodyDeclaration<*>): String =
        when (member) {
            is FieldDeclaration -> {
                when {
                    member.variables.isEmpty() -> "field"
                    else -> member.getVariable(0).nameAsString
                }
            }

            is ConstructorDeclaration -> member.nameAsString
            is MethodDeclaration -> member.nameAsString
            is ClassOrInterfaceDeclaration -> member.nameAsString
            is EnumDeclaration -> member.nameAsString
            else -> "member"
        }
}
