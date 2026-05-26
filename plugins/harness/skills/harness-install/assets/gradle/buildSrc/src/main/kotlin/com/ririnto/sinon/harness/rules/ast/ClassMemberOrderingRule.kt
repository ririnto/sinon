package com.ririnto.sinon.harness.rules.ast

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.BodyDeclaration
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.ConstructorDeclaration
import com.github.javaparser.ast.body.EnumDeclaration
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.InitializerDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.ririnto.sinon.harness.ast.AstFinding
import com.ririnto.sinon.harness.ast.AstFindingRenderer
import com.ririnto.sinon.harness.ast.AstSupport
import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.core.HarnessCheck
import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.rules.HarnessAstRule
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
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
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierType
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import org.jetbrains.kotlin.com.intellij.psi.PsiElement as KtPsiElement

/**
 * Rule that delegates class-member ordering findings to source AST/PSI analysis.
 */
object ClassMemberOrderingRule : HarnessAstRule() {
    /**
     * Category key.
     */
    override val category: String = "classMemberOrdering"

    override fun validate(ctx: RuleContext): Collection<Finding> =
        findJavaClassMemberOrderEntries(ctx.root, ctx.manifest.raw)

    override fun renderAstFindings(
        ctx: RuleContext,
        findings: Collection<AstFinding>,
    ): Collection<Finding> {
        val parameters =
            ctx.manifest
                .categoryObject(category)
                ?.get("parameters")
                ?.jsonObject
        val kindOrder =
            (
                parameters?.get("kindOrder")?.jsonArray?.mapNotNull { entry -> entry.jsonPrimitive.contentOrNull }
                    ?: emptyList()
            ).ifEmpty {
                listOf(
                    "companionObject",
                    "constProperty",
                    "fieldOrProperty",
                    "initializer",
                    "constructor",
                    "function",
                    "interface",
                    "class",
                    "enum",
                )
            }
        val visibilityOrder =
            (
                parameters?.get("visibilityOrder")?.jsonArray?.mapNotNull { entry -> entry.jsonPrimitive.contentOrNull }
                    ?: emptyList()
            ).ifEmpty {
                listOf("public", "protected", "internal", "package", "private")
            }
        val overrideOrder =
            (
                parameters?.get("overrideOrder")?.jsonArray?.mapNotNull { entry -> entry.jsonPrimitive.contentOrNull }
                    ?: emptyList()
            ).ifEmpty {
                listOf("override", "nonOverride")
            }
        val rank =
            buildMap {
                var idx = 0
                for (kind in kindOrder) {
                    for (visibility in visibilityOrder) {
                        for (overrideState in overrideOrder) {
                            put("$overrideState:$visibility:$kind", idx++)
                        }
                    }
                }
            }
        return findings
            .groupBy { finding -> finding.file to finding.detail("ownerId") }
            .values
            .flatMap { entries ->
                entries
                    .sortedBy { entry -> entry.intDetail("position") }
                    .fold(MemberOrderAccumulator(-1, emptyList())) { accumulator, entry ->
                        val memberKind = entry.detail("memberKind")
                        val memberVisibility = entry.detail("memberVisibility")
                        val memberRank =
                            rank["${entry.detail("memberOverrideState")}:$memberVisibility:$memberKind"]
                                ?: rank["$memberVisibility:$memberKind"]
                                ?: rank[memberKind]
                                ?: rank.size
                        when {
                            memberRank < accumulator.maxRank -> {
                                accumulator.copy(
                                    findings =
                                        accumulator.findings + AstFindingRenderer.render(entry, ctx.manifest.raw),
                                )
                            }

                            else -> {
                                accumulator.copy(maxRank = memberRank)
                            }
                        }
                    }.findings
            }
    }

    override fun findAstFindings(
        file: Path,
        ctx: RuleContext,
        astFactory: KtPsiFactory?,
    ): Collection<AstFinding> =
        buildSet {
            val ktFile = AstSupport.parse(file, astFactory)
            ktFile?.accept(Visitor(::add, file, ctx, ktFile, this@ClassMemberOrderingRule))
        }

    /**
     * Get owner ID for a class or object.
     */
    internal fun kotlinOwnerId(
        ktFile: KtFile,
        declaration: KtClassOrObject,
    ): String =
        generateSequence(declaration as PsiElement?) { element -> element.parent }
            .filterIsInstance<KtClassOrObject>()
            .map { owner -> kotlinOwnerName(ktFile, owner) }
            .toList()
            .asReversed()
            .joinToString(".")

    /**
     * Get owner name for a class or object.
     */
    internal fun kotlinOwnerName(
        ktFile: KtFile,
        declaration: KtClassOrObject,
    ): String =
        when (declaration) {
            is KtObjectDeclaration -> {
                when {
                    declaration.isCompanion() -> "companion"
                    else -> declaration.name ?: "object@${AstSupport.lineOf(ktFile, declaration.node?.startOffset)}"
                }
            }

            else -> {
                declaration.name ?: "class@${AstSupport.lineOf(ktFile, declaration.node?.startOffset)}"
            }
        }

    /**
     * Get member visibility for a declaration.
     */
    internal fun kotlinMemberVisibility(declaration: PsiElement): String =
        when (declaration) {
            is KtModifierListOwner -> {
                when (declaration.visibilityModifierType()) {
                    KtTokens.PRIVATE_KEYWORD -> "private"
                    KtTokens.PROTECTED_KEYWORD -> "protected"
                    KtTokens.INTERNAL_KEYWORD -> "internal"
                    else -> "public"
                }
            }

            else -> {
                "public"
            }
        }

    /**
     * Get member override state for a declaration.
     */
    internal fun kotlinMemberOverrideState(declaration: PsiElement): String =
        when {
            declaration is KtModifierListOwner && declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD) -> "override"
            else -> "nonOverride"
        }

    /**
     * Get member kind for a declaration.
     */
    internal fun kotlinMemberKind(declaration: PsiElement): String? =
        when (declaration) {
            is KtObjectDeclaration -> {
                when {
                    declaration.isCompanion() -> "companionObject"
                    else -> "class"
                }
            }

            is KtProperty -> {
                when {
                    declaration.hasModifier(KtTokens.CONST_KEYWORD) -> "constProperty"
                    else -> "fieldOrProperty"
                }
            }

            is KtSecondaryConstructor -> {
                "constructor"
            }

            is KtClassInitializer -> {
                "initializer"
            }

            is KtNamedFunction -> {
                "function"
            }

            is KtClass -> {
                when {
                    declaration.isInterface() -> "interface"
                    declaration.isEnum() -> "enum"
                    else -> "class"
                }
            }

            else -> {
                null
            }
        }

    /**
     * Get member name for a declaration.
     */
    internal fun kotlinMemberName(declaration: PsiElement): String =
        when (declaration) {
            is KtObjectDeclaration -> {
                when {
                    declaration.isCompanion() -> "companion object"
                    else -> declaration.name ?: "object"
                }
            }

            is KtProperty -> {
                when {
                    declaration.hasModifier(KtTokens.CONST_KEYWORD) -> "const ${declaration.name ?: "property"}"
                    else -> declaration.name ?: "property"
                }
            }

            is KtSecondaryConstructor -> {
                "constructor"
            }

            is KtClassInitializer -> {
                "initializer"
            }

            is KtNamedFunction -> {
                declaration.name ?: "function"
            }

            is KtClass -> {
                when {
                    declaration.isInterface() -> "interface ${declaration.name ?: ""}"
                    declaration.isEnum() -> "enum ${declaration.name ?: ""}"
                    else -> declaration.name ?: "class"
                }
            }

            else -> {
                "member"
            }
        }

    private fun findJavaClassMemberOrderEntries(
        root: Path,
        manifest: JsonObject,
    ): List<Finding> =
        listOf(
            root / "buildSrc" / "src" / "main" / "java",
            root / "buildSrc" / "src" / "test" / "java",
            root / "src" / "main" / "java",
            root / "src" / "test" / "java",
        ).filter { sourceRoot -> !sourceRoot.isSymbolicLink() }
            .filter { sourceRoot -> sourceRoot.isDirectory() }
            .flatMap { sourceRoot ->
                sourceRoot
                    .walk()
                    .filter { file -> !file.isSymbolicLink() }
                    .filter { file -> file.isRegularFile() }
                    .filter { file -> file.extension == "java" }
            }.flatMap { file -> findJavaClassMemberOrderEntries(root, file, manifest) }

    private fun findJavaClassMemberOrderEntries(
        root: Path,
        file: Path,
        manifest: JsonObject,
    ): List<Finding> {
        val compilationUnit = StaticJavaParser.parse(file)
        return AstFindingRenderer.renderEach(
            compilationUnit.findAll(ClassOrInterfaceDeclaration::class.java).flatMap { type ->
                javaClassMemberOrderEntries(root, file, type.nameAsString, javaOwnerId(type), type.members)
            } +
                compilationUnit.findAll(EnumDeclaration::class.java).flatMap { type ->
                    javaClassMemberOrderEntries(root, file, type.nameAsString, javaOwnerId(type), type.members)
                },
            manifest,
        )
    }

    private fun javaClassMemberOrderEntries(
        root: Path,
        file: Path,
        className: String,
        ownerId: String,
        members: List<BodyDeclaration<*>>,
    ): List<AstFinding> =
        buildList {
            members.forEachIndexed { position, member ->
                val kind = javaMemberKind(member)
                kind?.let { kind ->
                    add(
                        AstFinding(
                            rule = category,
                            file = file.relativeTo(root).invariantSeparatorsPathString,
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
            is FieldDeclaration -> {
                when {
                    member.isStatic && member.isFinal -> "constProperty"
                    else -> "fieldOrProperty"
                }
            }

            is InitializerDeclaration -> {
                "initializer"
            }

            is ConstructorDeclaration -> {
                "constructor"
            }

            is MethodDeclaration -> {
                "function"
            }

            is ClassOrInterfaceDeclaration -> {
                when {
                    member.isInterface -> "interface"
                    else -> "class"
                }
            }

            is EnumDeclaration -> {
                "enum"
            }

            else -> {
                null
            }
        }

    private fun javaMemberName(member: BodyDeclaration<*>): String =
        when (member) {
            is FieldDeclaration -> {
                when {
                    member.variables.isEmpty() -> "field"
                    else -> member.getVariable(0).nameAsString
                }
            }

            is ConstructorDeclaration -> {
                member.nameAsString
            }

            is MethodDeclaration -> {
                member.nameAsString
            }

            is ClassOrInterfaceDeclaration -> {
                member.nameAsString
            }

            is EnumDeclaration -> {
                member.nameAsString
            }

            else -> {
                "member"
            }
        }

    private data class MemberOrderAccumulator(
        val maxRank: Int,
        val findings: List<Finding>,
    )

    /**
     * AST visitor for class member ordering analysis.
     *
     * Analyzes class and object declarations to identify member ordering violations.
     */
    private class Visitor(
        private val record: (AstFinding) -> Unit,
        private val file: Path,
        private val ctx: RuleContext,
        private val ktFile: KtFile,
        private val rule: ClassMemberOrderingRule,
    ) : KtTreeVisitorVoid() {
        override fun visitClass(klass: KtClass) {
            super.visitClass(klass)
            addClassMemberOrderEntries(
                rule.kotlinOwnerId(ktFile, klass),
                klass.name ?: "unknown",
                klass.getBody()?.declarations ?: emptyList(),
            )
        }

        override fun visitObjectDeclaration(declaration: KtObjectDeclaration) {
            super.visitObjectDeclaration(declaration)
            addClassMemberOrderEntries(
                rule.kotlinOwnerId(ktFile, declaration),
                declaration.name ?: "object",
                declaration.getBody()?.declarations ?: emptyList(),
            )
        }

        /**
         * Add class member order entries to the findings list.
         */
        private fun addClassMemberOrderEntries(
            ownerId: String,
            className: String,
            declarations: List<KtDeclaration>,
        ) {
            declarations.forEachIndexed { position, declaration ->
                if (declaration !is KtEnumEntry) {
                    val kind = rule.kotlinMemberKind(declaration)
                    kind?.let { kind ->
                        record(
                            AstFinding(
                                rule = rule.category,
                                file = AstSupport.relativeFilePath(file, ctx.root),
                                line = AstSupport.lineOf(ktFile, declaration.node?.startOffset),
                                details =
                                    mapOf(
                                        "ownerId" to ownerId,
                                        "className" to className,
                                        "memberName" to rule.kotlinMemberName(declaration),
                                        "memberOverrideState" to rule.kotlinMemberOverrideState(declaration),
                                        "memberVisibility" to rule.kotlinMemberVisibility(declaration),
                                        "memberKind" to kind,
                                        "position" to position.toString(),
                                    ),
                            ),
                        )
                    }
                }
            }
        }
    }
}
