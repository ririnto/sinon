package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPostfixExpression
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSafeQualifiedExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Prefers Kotlin path extensions when they provide a direct equivalent for Java file helpers.
 *
 * Java `Path` remains the path type, and Java NIO operations without a Kotlin path equivalent remain valid.
 */
class NoJavaPathApi :
    Rule(
        ruleId = RuleId("harness:no-java-path-api"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    private companion object {
        val FILE_PATH_HELPERS: Set<String> = setOf("exists", "isDirectory", "listFiles", "mkdirs")
        val FILES_HELPERS: Set<String> =
            setOf(
                "createDirectories",
                "deleteIfExists",
                "exists",
                "isDirectory",
                "isHidden",
                "isRegularFile",
                "isSymbolicLink",
                "readAllBytes",
                "readString",
                "writeString"
            )
        val PATH_METHODS_WITHOUT_ARGUMENTS: Set<String> =
            setOf(
                "getFileName",
                "getParent",
                "getRoot",
                "normalize",
                "toAbsolutePath"
            )
        val PATH_METHODS_ACCEPTING_SEGMENTS: Set<String> = setOf("resolve")
    }

    private var currentFile: KtFile? = null

    private var pathTypeNames: Set<String> = emptySet()

    private var uriTypeNames: Set<String> = emptySet()

    private var linkOptionTypeNames: Set<String> = emptySet()

    private var pathClassNames: Set<String> = emptySet()

    private var pathsClassNames: Set<String> = emptySet()

    private var kotlinPathFactoryNames: Set<String> = emptySet()

    private var javaPathStaticFactoryNames: Map<String, String> = emptyMap()

    private var javaPathsStaticFactoryNames: Map<String, String> = emptyMap()

    private var javaPathStaticWildcardImported: Boolean = false

    private var javaPathsStaticWildcardImported: Boolean = false

    private var kotlinPathWildcardImported: Boolean = false

    private var filesClassNames: Set<String> = emptySet()

    private var filesStaticNames: Map<String, String> = emptyMap()

    private var filesWildcardImported: Boolean = false

    private var nameBindings: List<NameBinding> = emptyList()

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        (node.psi.containingFile as? KtFile)?.let { file ->
            if (file !== currentFile) {
                currentFile = file
                prepareFile(file)
            }
            when (val psi = node.psi) {
                is KtDotQualifiedExpression -> {
                    if (psi.isJavaFileHelper()) {
                        emit(psi.textOffset, "Use kotlin.io.path APIs instead of File path helpers", false)
                    }
                }

                is KtCallExpression -> {
                    if (psi.isJavaPathResolve()) {
                        emit(psi.textOffset, "Use the kotlin.io.path division operator for Path child paths", false)
                    } else if (psi.isJavaFilesHelper(file)) {
                        val offset =
                            (psi.parent as? KtQualifiedExpression)
                                ?.takeIf { expression -> expression.selectorExpression === psi }
                                ?.textOffset ?: psi.textOffset
                        emit(offset, "Use kotlin.io.path APIs for this java.nio.file.Files helper", false)
                    }
                }
            }
        }
    }

    private fun prepareFile(file: KtFile) {
        val imports =
            file.importDirectives.mapNotNull { directive ->
                directive.importPath?.pathStr?.let { path -> directive to path }
            }
        pathTypeNames =
            imports
                .filter { (_, path) -> path == "java.nio.file.Path" || path == "java.nio.file.*" }
                .map { (directive, path) ->
                    when {
                        path.endsWith(".*") -> "Path"
                        else -> directive.aliasName ?: "Path"
                    }
                }.toSet()
        pathClassNames = pathTypeNames
        uriTypeNames =
            imports
                .filter { (_, path) -> path == "java.net.URI" || path == "java.net.*" }
                .map { (directive, path) ->
                    when {
                        path.endsWith(".*") -> "URI"
                        else -> directive.aliasName ?: "URI"
                    }
                }.toSet()
        linkOptionTypeNames =
            imports
                .filter { (_, path) -> path == "java.nio.file.LinkOption" || path == "java.nio.file.*" }
                .map { (directive, path) ->
                    when {
                        path.endsWith(".*") -> "LinkOption"
                        else -> directive.aliasName ?: "LinkOption"
                    }
                }.toSet()
        pathsClassNames =
            imports
                .filter { (_, path) -> path == "java.nio.file.Paths" || path == "java.nio.file.*" }
                .map { (directive, path) ->
                    when {
                        path.endsWith(".*") -> "Paths"
                        else -> directive.aliasName ?: "Paths"
                    }
                }.toSet()
        kotlinPathFactoryNames =
            imports
                .filter { (_, path) -> path == "kotlin.io.path.Path" || path == "kotlin.io.path.*" }
                .map { (directive, path) ->
                    when {
                        path.endsWith(".*") -> "Path"
                        else -> directive.aliasName ?: "Path"
                    }
                }.toSet()
        javaPathStaticFactoryNames =
            imports
                .filter { (_, path) -> path.startsWith("java.nio.file.Path.") }
                .filter { (_, path) -> path != "java.nio.file.Path.*" }
                .mapNotNull { (directive, path) ->
                    "of".takeIf { path.endsWith(".of") }?.let { name -> (directive.aliasName ?: name) to name }
                }.toMap()
        javaPathsStaticFactoryNames =
            imports
                .filter { (_, path) -> path.startsWith("java.nio.file.Paths.") }
                .filter { (_, path) -> path != "java.nio.file.Paths.*" }
                .mapNotNull { (directive, path) ->
                    "get".takeIf { path.endsWith(".get") }?.let { name -> (directive.aliasName ?: name) to name }
                }.toMap()
        javaPathStaticWildcardImported = imports.any { (_, path) -> path == "java.nio.file.Path.*" }
        javaPathsStaticWildcardImported = imports.any { (_, path) -> path == "java.nio.file.Paths.*" }
        kotlinPathWildcardImported = imports.any { (_, path) -> path == "kotlin.io.path.*" }
        filesClassNames =
            imports
                .filter { (_, path) -> path == "java.nio.file.Files" || path == "java.nio.file.*" }
                .map { (directive, _) -> directive.aliasName ?: "Files" }
                .toSet()
        filesStaticNames =
            imports
                .filter { (_, path) -> path.startsWith("java.nio.file.Files.") }
                .filter { (_, path) -> path != "java.nio.file.Files.*" }
                .mapNotNull { (directive, path) ->
                    val methodName = path.substringAfterLast('.')
                    methodName
                        .takeIf { name -> name in FILES_HELPERS }
                        ?.let { name -> (directive.aliasName ?: name) to name }
                }.toMap()
        filesWildcardImported = imports.any { (_, path) -> path == "java.nio.file.Files.*" }
        val declarations = file.collectDescendantsOfType<KtNamedDeclaration>()
        nameBindings = declarations.mapNotNull { declaration -> declaration.nameBindingWithInference() }
    }

    private fun KtNamedDeclaration.nameBinding(): NameBinding? =
        name?.let { bindingName ->
            bindingScope()?.let { scope ->
                val knownType =
                    when (this) {
                        is KtParameter -> typeReference.knownType()
                        is KtProperty -> typeReference.knownType()
                        else -> null
                    }
                val isHoisted = this is KtNamedFunction || this is KtClassOrObject || this is KtTypeAlias
                NameBinding(bindingName, scope, textOffset, knownType, isHoisted)
            }
        }

    private fun KtNamedDeclaration.nameBindingWithInference(): NameBinding? =
        nameBinding()?.let { binding ->
            when (this) {
                is KtProperty -> {
                    binding.copy(
                        knownType =
                            when {
                                typeReference !== null -> typeReference.knownType()
                                else -> initializer?.knownType()
                            }
                    )
                }

                else -> {
                    binding
                }
            }
        }

    private fun KtNamedDeclaration.bindingScope(): PsiElement? =
        when (this) {
            is KtParameter -> {
                when (val parameterOwner = parent) {
                    is KtForExpression -> {
                        parameterOwner.body
                    }

                    else -> {
                        parameterOwner?.parent?.let { owner ->
                            when (owner) {
                                is KtPrimaryConstructor -> owner.parent ?: owner
                                is KtForExpression -> owner.body
                                else -> owner
                            }
                        }
                    }
                }
            }

            is KtDestructuringDeclarationEntry -> {
                generateSequence(parent, PsiElement::getParent)
                    .firstOrNull { element ->
                        element is KtBlockExpression ||
                            element is KtForExpression ||
                            element is KtLambdaExpression ||
                            element is KtClassBody ||
                            element is KtFile
                    }.let { scope ->
                        when (val bindingScope = scope) {
                            is KtForExpression -> bindingScope.body
                            else -> bindingScope
                        }
                    }
            }

            else -> {
                parent
            }
        }

    private fun KtTypeReference?.knownType(): KnownType? =
        this?.typeElement?.let { type ->
            when (type) {
                is KtNullableType -> type.innerType?.text?.let { typeName -> typeName.toKnownType(true) }
                else -> type.text.toKnownType(false)
            }
        }

    private fun String.toKnownType(nullable: Boolean): KnownType? =
        when (this) {
            in pathTypeNames, "java.nio.file.Path" -> KnownType.path(nullable)
            "String", "kotlin.String" -> KnownType.string(nullable)
            "Int", "kotlin.Int" -> KnownType.int(nullable)
            in uriTypeNames, "java.net.URI" -> KnownType.uri(nullable)
            in linkOptionTypeNames, "java.nio.file.LinkOption" -> KnownType.linkOption(nullable)
            else -> null
        }

    private fun KtDotQualifiedExpression.isJavaFileHelper(): Boolean =
        (selectorExpression as? KtCallExpression)?.let { helperCall ->
            when (helperCall.calleeExpression?.callableName()) {
                in FILE_PATH_HELPERS -> {
                    (receiverExpression as? KtDotQualifiedExpression)?.let { toFileCall ->
                        (toFileCall.selectorExpression as? KtCallExpression)?.let { conversionCall ->
                            conversionCall.hasNoArguments() &&
                                conversionCall.calleeExpression?.callableName() == "toFile" &&
                                toFileCall.receiverExpression.isJavaPathExpression()
                        } == true
                    } == true && helperCall.hasNoArguments()
                }

                else -> {
                    false
                }
            }
        } ?: false

    private fun KtCallExpression.hasNoArguments(): Boolean = valueArgumentList?.arguments.orEmpty().isEmpty() && lambdaArguments.isEmpty()

    private fun KtCallExpression.isJavaPathResolve(): Boolean =
        calleeExpression?.callableName() == "resolve" &&
            pathMethodReturnType() !== null

    private fun KtCallExpression.hasJavaPathReceiver(): Boolean =
        (parent as? KtQualifiedExpression)?.let { qualified ->
            qualified.selectorExpression === this &&
                when (qualified.receiverExpression.knownType()) {
                    KnownType.PATH -> true
                    KnownType.NULLABLE_PATH -> qualified is KtSafeQualifiedExpression
                    else -> false
                }
        } == true

    private fun KtCallExpression.pathMethodReturnType(): KnownType? =
        when (hasJavaPathReceiver() && hasJavaSignatureArguments()) {
            false -> {
                null
            }

            true -> {
                when (val methodName = calleeExpression?.callableName()) {
                    in PATH_METHODS_WITHOUT_ARGUMENTS -> {
                        KnownType.PATH.takeIf { arguments().isEmpty() }
                    }

                    "getName" -> {
                        KnownType.PATH.takeIf { arguments().singleOrNull()?.isIntegerArgument() == true }
                    }

                    "subpath" -> {
                        KnownType.PATH
                            .takeIf { arguments().size == 2 }
                            ?.takeIf { arguments().all { argument -> argument.isIntegerArgument() } }
                    }

                    "relativize" -> {
                        KnownType.PATH.takeIf { arguments().singleOrNull()?.isPathArgument() == true }
                    }

                    "toRealPath" -> {
                        KnownType.PATH.takeIf { arguments().all { argument -> argument.isLinkOptionArgument() } }
                    }

                    "resolveSibling" -> {
                        KnownType.PATH.takeIf { arguments().singleOrNull()?.isPathOrStringArgument() == true }
                    }

                    in PATH_METHODS_ACCEPTING_SEGMENTS -> {
                        KnownType.PATH.takeIf { arguments().isPathArgumentList() }
                    }

                    else -> {
                        null
                    }
                }
            }
        }

    private fun KtCallExpression.arguments(): List<KtExpression> =
        valueArgumentList?.arguments.orEmpty().mapNotNull(KtValueArgument::getArgumentExpression)

    private fun KtCallExpression.hasJavaSignatureArguments(): Boolean =
        lambdaArguments.isEmpty() &&
            valueArgumentList?.arguments.orEmpty().all { argument -> argument.getArgumentName() === null }

    private fun List<KtExpression>.isPathArgumentList(): Boolean =
        isNotEmpty() &&
            (all { argument -> argument.isPathArgument() } || all { argument -> argument.isStringArgument() })

    private fun KtExpression.isPathArgument(): Boolean = knownType() == KnownType.PATH

    private fun KtExpression.isStringArgument(): Boolean = knownType() == KnownType.STRING

    private fun KtExpression.isPathOrStringArgument(): Boolean = isPathArgument() || isStringArgument()

    private fun KtExpression.isIntegerArgument(): Boolean = knownType() == KnownType.INT

    private fun KtExpression.isLinkOptionArgument(): Boolean = knownType() == KnownType.LINK_OPTION

    private fun KtExpression.knownType(): KnownType? =
        when (this) {
            is KtStringTemplateExpression -> {
                KnownType.STRING
            }

            is KtConstantExpression -> {
                KnownType.INT.takeIf { text.toIntOrNull() !== null }
            }

            is KtNameReferenceExpression -> {
                knownBindingType(getReferencedName(), this)
            }

            is KtQualifiedExpression -> {
                when (val selector = selectorExpression) {
                    is KtNameReferenceExpression -> {
                        when {
                            receiverExpression is KtThisExpression -> knownBindingType(selector.getReferencedName(), this)
                            isLinkOptionConstant() -> KnownType.LINK_OPTION
                            else -> null
                        }
                    }

                    is KtCallExpression -> {
                        when {
                            selector.calleeExpression?.callableName() == "toUri" &&
                                selector.hasJavaPathReceiver() &&
                                selector.hasJavaSignatureArguments() &&
                                selector.arguments().isEmpty() -> {
                                KnownType.URI
                            }

                            else -> {
                                selector.pathMethodReturnType() ?: selector.pathFactoryType(receiverExpression)
                            }
                        }
                    }

                    else -> {
                        null
                    }
                }?.let { resultType ->
                    KnownType.nullableIf(this is KtSafeQualifiedExpression, resultType)
                }
            }

            is KtCallExpression -> {
                pathFactoryType(null)
            }

            is KtParenthesizedExpression -> {
                expression?.knownType()
            }

            is KtPostfixExpression -> {
                baseExpression?.knownType()?.let { resultType ->
                    when {
                        operationToken == KtTokens.EXCLEXCL -> KnownType.nonNullable(resultType)
                        else -> resultType
                    }
                }
            }

            else -> {
                null
            }
        }

    private fun KtCallExpression.pathFactoryType(qualifier: KtExpression?): KnownType? =
        currentFile?.let { file ->
            val arguments = arguments()
            when {
                !hasJavaSignatureArguments() -> {
                    null
                }

                else -> {
                    when (pathFactoryName(qualifier, file)) {
                        "of" -> {
                            KnownType.PATH
                                .takeIf { arguments.isNotEmpty() }
                                ?.takeIf {
                                    arguments.all { expression -> expression.isStringArgument() } ||
                                        arguments.singleOrNull()?.knownType() == KnownType.URI
                                }
                        }

                        "get" -> {
                            KnownType.PATH
                                .takeIf { arguments.isNotEmpty() }
                                ?.takeIf {
                                    arguments.all { expression -> expression.isStringArgument() } ||
                                        arguments.singleOrNull()?.knownType() == KnownType.URI
                                }
                        }

                        "Path" -> {
                            KnownType.PATH
                                .takeIf { arguments.isNotEmpty() }
                                ?.takeIf { arguments.all { expression -> expression.isStringArgument() } }
                        }

                        else -> {
                            null
                        }
                    }
                }
            }
        }

    private fun KtCallExpression.pathFactoryName(
        qualifier: KtExpression?,
        file: KtFile
    ): String? =
        when {
            calleeExpression?.callableName() == "of" &&
                qualifier.isUnshadowedQualifier(pathClassNames, "java.nio.file.Path", "java", this) -> "of"

            calleeExpression?.callableName() == "get" &&
                qualifier.isUnshadowedQualifier(pathsClassNames, "java.nio.file.Paths", "java", this) -> "get"

            calleeExpression?.callableName() == "Path" &&
                qualifier?.qualifiedName() == "kotlin.io.path" &&
                !isNameShadowed("kotlin", this) -> "Path"

            qualifier === null &&
                calleeExpression?.qualifiedName() == "kotlin.io.path.Path" &&
                !isNameShadowed("kotlin", this) -> "Path"

            qualifier === null -> importedPathFactoryKind(calleeExpression?.callableName(), this, file)

            else -> null
        }

    private fun KtExpression?.isUnshadowedQualifier(
        importedNames: Set<String>,
        fullyQualifiedName: String,
        rootPackageName: String,
        usage: PsiElement
    ): Boolean =
        this?.qualifiedName()?.let { name ->
            when {
                name == fullyQualifiedName -> !isNameShadowed(rootPackageName, usage)
                name in importedNames -> !isNameShadowed(name, usage)
                else -> false
            }
        } == true

    private fun KtQualifiedExpression.isLinkOptionConstant(): Boolean =
        selectorExpression.asNameReference()?.getReferencedName() == "NOFOLLOW_LINKS" &&
            receiverExpression.qualifiedName()?.let { qualifier ->
                (qualifier in linkOptionTypeNames && !isNameShadowed(qualifier, this)) ||
                    (qualifier == "java.nio.file.LinkOption" && !isNameShadowed("java", this))
            } == true

    private fun importedPathFactoryKind(
        methodName: String?,
        usage: PsiElement,
        file: KtFile
    ): String? =
        methodName?.let { name ->
            when {
                usage.containingFile !== file -> null
                javaPathStaticFactoryNames[name] == "of" && !isNameShadowed(name, usage) -> "of"
                javaPathsStaticFactoryNames[name] == "get" && !isNameShadowed(name, usage) -> "get"
                javaPathStaticWildcardImported && name == "of" && !isNameShadowed(name, usage) -> "of"
                javaPathsStaticWildcardImported && name == "get" && !isNameShadowed(name, usage) -> "get"
                name in kotlinPathFactoryNames && !isNameShadowed(name, usage) -> "Path"
                kotlinPathWildcardImported && name == "Path" && !isNameShadowed(name, usage) -> "Path"
                else -> null
            }
        }

    private fun KtExpression.isJavaPathExpression(): Boolean = knownType() == KnownType.PATH

    private fun knownBindingType(
        name: String,
        usage: PsiElement
    ): KnownType? =
        nameBindings
            .asSequence()
            .filter { binding -> binding.name == name }
            .filter { binding -> binding.isVisibleAt(usage) }
            .maxByOrNull { binding -> binding.scope.depth() }
            ?.knownType

    private fun isNameShadowed(
        name: String,
        usage: PsiElement
    ): Boolean = nameBindings.any { binding -> binding.name == name && binding.isVisibleAt(usage) }

    private fun NameBinding.isVisibleAt(usage: PsiElement): Boolean =
        PsiTreeUtil.isAncestor(scope, usage, false) &&
            (scope !is KtBlockExpression || isHoisted || declarationOffset < usage.textOffset)

    private fun KtCallExpression.isJavaFilesHelper(file: KtFile): Boolean =
        calleeExpression?.let { callee ->
            callee.callableName()?.let { helperName ->
                val importedMethodName = filesStaticNames[helperName]
                val qualifiedCallee = parent as? KtQualifiedExpression
                when {
                    helperName !in FILES_HELPERS && importedMethodName !in FILES_HELPERS -> {
                        false
                    }

                    qualifiedCallee === null -> {
                        (importedMethodName in FILES_HELPERS || (filesWildcardImported && helperName in FILES_HELPERS)) &&
                            !isNameShadowed(helperName, this)
                    }

                    qualifiedCallee.selectorExpression !== this -> {
                        false
                    }

                    else -> {
                        qualifiedCallee.receiverExpression.qualifiedName()?.let { qualifier ->
                            (qualifier == "java.nio.file.Files" && !isNameShadowed("java", this)) ||
                                (qualifier in filesClassNames && !isNameShadowed(qualifier, this)) ||
                                file.importDirectives.any { directive ->
                                    directive.importPath?.pathStr?.let { path ->
                                        path == "java.nio.file" &&
                                            directive.aliasName?.let { alias ->
                                                "$alias.Files" == qualifier && !isNameShadowed(alias, this)
                                            } == true
                                    } ?: false
                                }
                        } ?: false
                    }
                }
            } ?: false
        } ?: false

    private fun KtExpression?.callableName(): String? =
        when (this) {
            is KtCallExpression -> {
                calleeExpression?.asNameReference()?.let { callee -> callee.getReferencedName() }
            }

            is KtNameReferenceExpression -> {
                getReferencedName()
            }

            is KtQualifiedExpression -> {
                selectorExpression.callableName()
            }

            else -> {
                null
            }
        }

    private fun KtExpression?.asNameReference(): KtNameReferenceExpression? =
        when (this) {
            is KtNameReferenceExpression -> {
                this
            }

            is KtQualifiedExpression -> {
                selectorExpression.asNameReference()
            }

            else -> {
                null
            }
        }

    private fun KtExpression.qualifiedName(): String? =
        when (this) {
            is KtNameReferenceExpression -> {
                getReferencedName()
            }

            is KtQualifiedExpression -> {
                receiverExpression.qualifiedName()?.let { receiverName ->
                    selectorExpression.asNameReference()?.let { selector ->
                        "$receiverName.${selector.getReferencedName()}"
                    }
                }
            }

            else -> {
                null
            }
        }

    private fun PsiElement.depth(): Int = generateSequence(this as PsiElement?, PsiElement::getParent).count()

    private enum class KnownType(
        val isPath: Boolean
    ) {
        PATH(true),
        NULLABLE_PATH(true),
        STRING(false),
        NULLABLE_STRING(false),
        INT(false),
        NULLABLE_INT(false),
        URI(false),
        NULLABLE_URI(false),
        LINK_OPTION(false),
        NULLABLE_LINK_OPTION(false);

        companion object {
            fun path(nullable: Boolean): KnownType =
                if (nullable) {
                    NULLABLE_PATH
                } else {
                    PATH
                }

            fun string(nullable: Boolean): KnownType =
                if (nullable) {
                    NULLABLE_STRING
                } else {
                    STRING
                }

            fun int(nullable: Boolean): KnownType =
                if (nullable) {
                    NULLABLE_INT
                } else {
                    INT
                }

            fun uri(nullable: Boolean): KnownType =
                if (nullable) {
                    NULLABLE_URI
                } else {
                    URI
                }

            fun linkOption(nullable: Boolean): KnownType =
                if (nullable) {
                    NULLABLE_LINK_OPTION
                } else {
                    LINK_OPTION
                }

            fun nullableIf(
                nullable: Boolean,
                type: KnownType
            ): KnownType =
                when {
                    !nullable -> type
                    type == PATH -> NULLABLE_PATH
                    type == STRING -> NULLABLE_STRING
                    type == INT -> NULLABLE_INT
                    type == URI -> NULLABLE_URI
                    type == LINK_OPTION -> NULLABLE_LINK_OPTION
                    else -> type
                }

            fun nonNullable(type: KnownType): KnownType =
                when (type) {
                    NULLABLE_PATH -> PATH
                    NULLABLE_STRING -> STRING
                    NULLABLE_INT -> INT
                    NULLABLE_URI -> URI
                    NULLABLE_LINK_OPTION -> LINK_OPTION
                    else -> type
                }
        }
    }

    private data class NameBinding(
        val name: String,
        val scope: PsiElement,
        val declarationOffset: Int,
        val knownType: KnownType?,
        val isHoisted: Boolean
    )
}
