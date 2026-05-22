package ai.harness.gradle

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import java.nio.file.Path
import kotlin.io.path.Path as kPath
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.walk
import kotlin.io.path.writeText

/**
 * Registers the harness validation task on the root project.
 */
abstract class HarnessValidationPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        if (target != target.rootProject) {
            return
        }
        target.pluginManager.apply("base")
        val depScope = target.configurations.create("harnessKotlinCompilerDeps")
        target.dependencies.add(
            depScope.name,
            "$KOTLIN_COMPILER_EMBEDDABLE:$KOTLIN_COMPILER_VERSION",
        )
        val resolvable = target.configurations.create("harnessKotlinCompilerResolvable") {
            extendsFrom(depScope)
        }
        target.tasks.register("harnessValidate", HarnessValidationTask::class.java) {
            group = "verification"
            description = "Validate Claude repository harness assets."
            kotlinCompiler.from(resolvable)
        }
        target.tasks.named("check").configure {
            dependsOn("harnessValidate")
        }
    }

    companion object {
        /**
         * Maven coordinate prefix for the Kotlin compiler artifact.
         */
        const val KOTLIN_COMPILER_EMBEDDABLE = "org.jetbrains.kotlin:kotlin-compiler-embeddable"

        /**
         * Pinned Kotlin compiler version used by the worker classloader.
         */
        const val KOTLIN_COMPILER_VERSION = "2.3.21"
    }

    /**
     * Gradle task that validates installed Claude repository harness assets.
     */
    abstract class HarnessValidationTask : DefaultTask() {
        @get:Inject
        abstract val workerExecutor: WorkerExecutor

        /**
         * Classpath of the isolated Kotlin compiler used by the PSI worker action.
         */
        @get:Classpath
        abstract val kotlinCompiler: ConfigurableFileCollection

        @TaskAction
        fun validate() {
            val root: Path = project.rootDir.toPath()
            val (manifest, manifestFindings) = loadManifest(root)
            val psiResults = computePsiResults(root)
            val findings = buildSet {
                addAll(manifestFindings)
                if (manifest != null) {
                    val knownCategories = HarnessCheck.entries.map { it.category }.toSet()
                    val knownMetadataKeys = setOf(
                        "name", "description", "\$schema",
                        "seedFiles", "generatedArtifacts", "harnessEvolution", "teamPatterns",
                    )
                    val unknownKeys = manifest.keys - knownCategories - knownMetadataKeys
                    unknownKeys.forEach { key ->
                        project.logger.warn("unknown manifest key: $key")
                    }
                    HarnessCheck.entries.forEach { check ->
                        if (check.applies(manifest)) {
                            addAll(check.validate(manifest, root, psiResults))
                        }
                    }
                }
            }.toList()
            val sorted = findings.sortedWith(compareBy({ it.severity.ordinal }, { findings.indexOf(it) }))
            sorted.forEach { finding ->
                when (finding.severity) {
                    Severity.ERROR -> project.logger.error("[${finding.severity}] ${finding.message}")
                    Severity.WARN -> project.logger.warn("[${finding.severity}] ${finding.message}")
                    Severity.INFO -> project.logger.info("[${finding.severity}] ${finding.message}")
                }
            }
            if (sorted.any { it.severity == Severity.ERROR }) {
                throw GradleException("Harness validation failed")
            }
            project.logger.lifecycle("Harness validation passed")
        }

        private fun computePsiResults(root: Path): HarnessPsiResults {
            val srcRoots = listOf(
                root / "buildSrc" / "src" / "main" / "kotlin",
                root / "buildSrc" / "src" / "test" / "kotlin",
            ).filter { it.isDirectory() }
            val srcFiles = buildList {
                srcRoots.forEach { dir ->
                    dir.walk().filter { it.isRegularFile() && it.extension == "kt" }.forEach { add(it) }
                }
            }
            val outputFile = temporaryDir.toPath() / "psi-results.json"
            val workQueue = workerExecutor.classLoaderIsolation {
                classpath.from(kotlinCompiler)
            }
            workQueue.submit(HarnessPsiWorkAction::class.java) {
                srcFilePaths.set(srcFiles.map { it.toString() })
                this.outputFile.set(outputFile.toFile())
            }
            workQueue.await()
            return Json.decodeFromString<HarnessPsiResults>(outputFile.readText())
        }

        private fun loadManifest(root: Path): Pair<JsonObject?, List<Finding>> {
            val manifestFile = root / "docs" / "harness" / "manifest.json"
            return when {
                manifestFile.isSymbolicLink() ->
                    null to listOf(Finding(Severity.ERROR, "forbidUnsafeSymlinks",
                        "symlink file is not allowed: docs/harness/manifest.json"))
                !manifestFile.isRegularFile() ->
                    null to listOf(Finding(Severity.ERROR, "requireFilesExist",
                        "missing file: docs/harness/manifest.json"))
                else -> try {
                    Json.parseToJsonElement(manifestFile.readText()).jsonObject to emptyList()
                } catch (e: Exception) {
                    null to listOf(Finding(Severity.ERROR, "requireFilesExist",
                        "failed to parse manifest: ${e.message}"))
                }
            }
        }

        /**
         * Work parameters for PSI analysis in an isolated classloader.
         */
        interface HarnessPsiWorkParameters : WorkParameters {
            /**
             * Absolute paths of Kotlin source files to scan.
             */
            val srcFilePaths: ListProperty<String>

            /**
             * JSON output sink for serialized HarnessPsiResults.
             */
            val outputFile: RegularFileProperty
        }

        /**
         * Worker action that runs Kotlin PSI scans in an isolated classloader.
         */
        abstract class HarnessPsiWorkAction : WorkAction<HarnessPsiWorkParameters> {
            override fun execute() {
                val srcFiles: List<Path> = parameters.srcFilePaths.get().map { kPath(it) }
                val results = HarnessPsiResults(
                    greaterThanComparisons = srcFiles.flatMap { tryOrEmpty { findGreaterThanComparisons(it) } },
                    blankLinesInLeafFunctions = srcFiles.flatMap { tryOrEmpty { findBlankLinesInLeafFunctions(it) } },
                    implicitLambdaIt = srcFiles.flatMap { tryOrEmpty { findImplicitLambdaIt(it) } },
                    topLevelDeclarations = srcFiles.mapNotNull { tryOrNull { inspectTopLevelDeclarations(it) } },
                    earlyReturns = srcFiles.flatMap { tryOrEmpty { findEarlyReturns(it) } },
                    silentCatches = srcFiles.flatMap { tryOrEmpty { findSilentCatches(it) } },
                    mutableCollections = srcFiles.flatMap { tryOrEmpty { findMutableCollections(it) } },
                    unstructuredLoggings = srcFiles.flatMap { tryOrEmpty { findUnstructuredLoggings(it) } },
                    wildcardImports = srcFiles.flatMap { tryOrEmpty { findWildcardImports(it) } },
                    fqnUsages = srcFiles.flatMap { tryOrEmpty { findFqnUsages(it) } },
                    docCommentMissings = srcFiles.flatMap { tryOrEmpty { findDocCommentMissings(it) } },
                    emptyCatchBlocks = srcFiles.flatMap { tryOrEmpty { findEmptyCatchBlocks(it) } },
                    braceOnIfs = srcFiles.flatMap { tryOrEmpty { findBraceOnIfViolations(it) } },
                    companionPositions = srcFiles.flatMap { tryOrEmpty { findCompanionPositionViolations(it) } },
                )
                parameters.outputFile.get().asFile.toPath().writeText(Json.encodeToString(results))
            }

            private fun findGreaterThanComparisons(file: Path): List<GreaterThanComparisonResult> {
                val ktFile = parse(file)
                val fileName = file.name
                return buildList {
                    ktFile.accept(object : KtVisitorVoid() {
                        override fun visitBinaryExpression(expression: KtBinaryExpression) {
                            super.visitBinaryExpression(expression)
                            if (expression.operationToken == KtTokens.GT || expression.operationToken == KtTokens.GTEQ) {
                                add(GreaterThanComparisonResult(fileName, lineOf(ktFile, expression.node?.startOffset)))
                            }
                        }
                    })
                }
            }

            private fun findBlankLinesInLeafFunctions(file: Path): List<BlankLineInFunctionResult> {
                val ktFile = parse(file)
                val fileName = file.name
                return buildList {
                    ktFile.accept(object : KtVisitorVoid() {
                        override fun visitNamedFunction(function: KtNamedFunction) {
                            super.visitNamedFunction(function)
                            val hasNestedFunc = function.hasDescendantOfType<KtNamedFunction> { it !== function }
                            val hasNestedLambda = function.hasDescendantOfType<KtLambdaExpression> { true }
                            if (hasNestedFunc || hasNestedLambda) {
                                return
                            }
                            val body = function.bodyExpression ?: return
                            var child: PsiElement? = body.firstChild
                            while (child != null) {
                                if (child.text.count { c -> c == '\n' } < 2) {
                                    child = child.nextSibling
                                    continue
                                }
                                add(BlankLineInFunctionResult(fileName, function.name ?: "unknown",
                                    lineOf(ktFile, child.node?.startOffset)))
                                child = child.nextSibling
                            }
                        }
                    })
                }
            }

            private fun findImplicitLambdaIt(file: Path): List<ImplicitLambdaItResult> {
                val ktFile = parse(file)
                val fileName = file.name
                return buildList {
                    ktFile.accept(object : KtVisitorVoid() {
                        override fun visitLambdaExpression(expression: KtLambdaExpression) {
                            super.visitLambdaExpression(expression)
                            if (expression.valueParameters.isNotEmpty()) {
                                return
                            }
                            var hasIt = false
                            expression.accept(object : KtVisitorVoid() {
                                override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                                    super.visitSimpleNameExpression(expression)
                                    if (expression.text == "it") {
                                        hasIt = true
                                    }
                                }
                            })
                            if (hasIt) {
                                add(ImplicitLambdaItResult(fileName, lineOf(ktFile, expression.node?.startOffset)))
                            }
                        }
                    })
                }
            }

            private fun inspectTopLevelDeclarations(file: Path): TopLevelDeclarationResult {
                val ktFile = parse(file)
                val declarations = ktFile.declarations
                val firstKind = if (declarations.isEmpty()) {
                    "unknown"
                } else when (declarations.first()::class.simpleName) {
                    "KtClass" -> "class"
                    "KtObjectDeclaration" -> "object"
                    "KtTypeAlias" -> "typealias"
                    else -> "unknown"
                }
                return TopLevelDeclarationResult(file.name, declarations.size, firstKind)
            }

            private fun findEarlyReturns(file: Path): List<EarlyReturnResult> {
                val ktFile = parse(file)
                val fileName = file.name
                return buildList {
                    ktFile.accept(object : KtVisitorVoid() {
                        override fun visitNamedFunction(function: KtNamedFunction) {
                            super.visitNamedFunction(function)
                            val body = function.bodyBlockExpression ?: return
                            val stmts = body.statements
                            if (stmts.isEmpty()) return
                            val funcName = function.name ?: "unknown"
                            stmts.forEach { stmt ->
                                if (stmt::class.simpleName == "KtReturnExpression") {
                                    if (stmt !== stmts.last()) {
                                        add(EarlyReturnResult(fileName, funcName, lineOf(ktFile, stmt.node?.startOffset)))
                                    }
                                }
                            }
                        }
                    })
                }
            }

            private fun findSilentCatches(file: Path): List<SilentCatchResult> {
                val ktFile = parse(file)
                val fileName = file.name
                return buildList {
                    ktFile.accept(object : KtVisitorVoid() {
                        override fun visitCatchSection(catchSection: org.jetbrains.kotlin.psi.KtCatchClause) {
                            super.visitCatchSection(catchSection)
                            val paramName = catchSection.catchParameter?.name
                            val body = catchSection.catchBody
                            if (paramName != null && body != null) {
                                val bodyText = body.text
                                if (!bodyText.contains(paramName) && !bodyText.contains("throw") && !bodyText.contains("logger") && !bodyText.contains("println")) {
                                    add(SilentCatchResult(fileName, lineOf(ktFile, catchSection.node?.startOffset)))
                                }
                            }
                        }
                    })
                }
            }

            private fun findMutableCollections(file: Path): List<MutableCollectionResult> {
                val ktFile = parse(file)
                val fileName = file.name
                val mutableFactories = setOf("mutableListOf", "mutableSetOf", "mutableMapOf", "ArrayList", "HashSet", "HashMap", "LinkedHashMap", "LinkedHashSet")
                return buildList {
                    ktFile.accept(object : KtVisitorVoid() {
                        override fun visitCallExpression(expression: org.jetbrains.kotlin.psi.KtCallExpression) {
                            super.visitCallExpression(expression)
                            val calleeName = expression.calleeExpression?.text ?: ""
                            if (calleeName in mutableFactories) {
                                add(MutableCollectionResult(fileName, calleeName, lineOf(ktFile, expression.node?.startOffset)))
                            }
                        }

                        override fun visitUserType(type: org.jetbrains.kotlin.psi.KtUserType) {
                            super.visitUserType(type)
                            val typeName = type.text.substringBefore("<")
                            if (typeName in mutableFactories) {
                                add(MutableCollectionResult(fileName, typeName, lineOf(ktFile, type.node?.startOffset)))
                            }
                        }
                    })
                }
            }

            private fun findUnstructuredLoggings(file: Path): List<UnstructuredLoggingResult> {
                val ktFile = parse(file)
                val fileName = file.name
                return buildList {
                    ktFile.accept(object : KtVisitorVoid() {
                        override fun visitCallExpression(expression: org.jetbrains.kotlin.psi.KtCallExpression) {
                            super.visitCallExpression(expression)
                            val calleeText = expression.calleeExpression?.text ?: ""
                            if (calleeText == "println" || calleeText == "print") {
                                add(UnstructuredLoggingResult(fileName, lineOf(ktFile, expression.node?.startOffset)))
                            }
                            if (calleeText.contains("System.out") || calleeText.contains("System.err")) {
                                add(UnstructuredLoggingResult(fileName, lineOf(ktFile, expression.node?.startOffset)))
                            }
                        }
                    })
                }
            }

            private fun findWildcardImports(file: Path): List<WildcardImportResult> {
                val ktFile = parse(file)
                val fileName = file.name
                return buildList {
                    ktFile.accept(object : KtVisitorVoid() {
                        override fun visitImportDirective(importDirective: org.jetbrains.kotlin.psi.KtImportDirective) {
                            super.visitImportDirective(importDirective)
                            if (importDirective.isAllUnder) {
                                add(WildcardImportResult(fileName, lineOf(ktFile, importDirective.node?.startOffset),
                                    importDirective.importPath?.pathStr ?: ""))
                            }
                        }
                    })
                }
            }

            private fun findFqnUsages(file: Path): List<ImportOverFqnResult> {
                val ktFile = parse(file)
                val fileName = file.name
                val imports = ktFile.importDirectives.mapNotNull { it.importedName?.asString() }.toSet()
                return buildList {
                    ktFile.accept(object : KtVisitorVoid() {
                        override fun visitDotQualifiedExpression(expression: org.jetbrains.kotlin.psi.KtDotQualifiedExpression) {
                            super.visitDotQualifiedExpression(expression)
                            val receiverChain = expression.receiverExpression
                            var depth = 1
                            var current: org.jetbrains.kotlin.psi.KtExpression? = receiverChain
                            while (current is org.jetbrains.kotlin.psi.KtDotQualifiedExpression) {
                                depth++
                                current = current.receiverExpression
                            }
                            if (depth >= 2) {
                                val simpleName = expression.selectorExpression?.text ?: ""
                                if (simpleName.isNotEmpty() && simpleName !in imports) {
                                    add(ImportOverFqnResult(fileName, lineOf(ktFile, expression.node?.startOffset)))
                                }
                            }
                        }
                    })
                }
            }

            private fun findDocCommentMissings(file: Path): List<DocCommentMissingResult> {
                val ktFile = parse(file)
                val fileName = file.name
                return buildList {
                    ktFile.accept(object : KtVisitorVoid() {
                        override fun visitClass(klass: org.jetbrains.kotlin.psi.KtClass) {
                            super.visitClass(klass)
                            val visibility = klass.visibilityModifier()?.text
                            if (visibility != "private" && visibility != "internal" && klass.docComment == null) {
                                val name = klass.name ?: "unknown"
                                add(DocCommentMissingResult(fileName, name, lineOf(ktFile, klass.node?.startOffset)))
                            }
                        }

                        override fun visitNamedFunction(function: KtNamedFunction) {
                            super.visitNamedFunction(function)
                            val visibility = function.visibilityModifier()?.text
                            val isOverride = function.hasModifier(KtTokens.OVERRIDE_KEYWORD)
                            if (visibility != "private" && visibility != "internal" && !isOverride && function.docComment == null) {
                                val name = function.name ?: "unknown"
                                add(DocCommentMissingResult(fileName, name, lineOf(ktFile, function.node?.startOffset)))
                            }
                        }

                        override fun visitProperty(property: org.jetbrains.kotlin.psi.KtProperty) {
                            super.visitProperty(property)
                            val visibility = property.visibilityModifier()?.text
                            if (visibility != "private" && visibility != "internal" && property.docComment == null) {
                                val name = property.name ?: "unknown"
                                add(DocCommentMissingResult(fileName, name, lineOf(ktFile, property.node?.startOffset)))
                            }
                        }
                    })
                }
            }

            private fun findEmptyCatchBlocks(file: Path): List<EmptyCatchBlockResult> {
                val ktFile = parse(file)
                val fileName = file.name
                return buildList {
                    ktFile.accept(object : KtVisitorVoid() {
                        override fun visitCatchSection(catchSection: org.jetbrains.kotlin.psi.KtCatchClause) {
                            super.visitCatchSection(catchSection)
                            val body = catchSection.catchBody
                            if (body is org.jetbrains.kotlin.psi.KtBlockExpression && body.statements.isEmpty()) {
                                add(EmptyCatchBlockResult(fileName, lineOf(ktFile, catchSection.node?.startOffset)))
                            }
                        }
                    })
                }
            }

            private fun findBraceOnIfViolations(file: Path): List<BraceOnIfResult> {
                val ktFile = parse(file)
                val fileName = file.name
                return buildList {
                    ktFile.accept(object : KtVisitorVoid() {
                        override fun visitIfExpression(expression: org.jetbrains.kotlin.psi.KtIfExpression) {
                            super.visitIfExpression(expression)
                            val thenBranch = expression.then
                            if (thenBranch != null && thenBranch !is org.jetbrains.kotlin.psi.KtBlockExpression) {
                                add(BraceOnIfResult(fileName, lineOf(ktFile, expression.node?.startOffset)))
                            }
                            val elseBranch = expression.`else`
                            if (elseBranch != null && elseBranch !is org.jetbrains.kotlin.psi.KtBlockExpression && elseBranch !is org.jetbrains.kotlin.psi.KtIfExpression) {
                                add(BraceOnIfResult(fileName, lineOf(ktFile, expression.node?.startOffset)))
                            }
                        }
                    })
                }
            }

            private fun findCompanionPositionViolations(file: Path): List<CompanionPositionResult> {
                val ktFile = parse(file)
                val fileName = file.name
                return buildList {
                    ktFile.accept(object : KtVisitorVoid() {
                        override fun visitClass(klass: org.jetbrains.kotlin.psi.KtClass) {
                            super.visitClass(klass)
                            val className = klass.name ?: "unknown"
                            val body = klass.getBody() ?: return
                            val decls = body.declarations
                            decls.forEachIndexed { idx, decl ->
                                if (decl is org.jetbrains.kotlin.psi.KtObjectDeclaration && decl.isCompanion()) {
                                    if (idx != 0) {
                                        add(CompanionPositionResult(fileName, className, lineOf(ktFile, decl.node?.startOffset)))
                                    }
                                }
                            }
                        }
                    })
                }
            }

            private fun parse(file: Path) = KtPsiFactory(null, true).createFile("temp", file.readText())

            private fun lineOf(ktFile: org.jetbrains.kotlin.psi.KtFile, offset: Int?): Int =
                (ktFile.viewProvider.document?.getLineNumber(offset ?: 0) ?: 0) + 1

            private fun <T> tryOrEmpty(block: () -> List<T>): List<T> = try {
                block()
            } catch (_: Exception) {
                emptyList()
            }

            private fun <T> tryOrNull(block: () -> T): T? = try {
                block()
            } catch (_: Exception) {
                null
            }

            private inline fun <reified T : PsiElement> PsiElement.hasDescendantOfType(
                crossinline predicate: (T) -> Boolean = { true },
            ): Boolean {
                var found = false
                accept(object : PsiRecursiveElementWalkingVisitor() {
                    override fun visitElement(element: PsiElement) {
                        if (element is T && predicate(element)) {
                            found = true
                            stopWalking()
                            return
                        }
                        super.visitElement(element)
                    }
                })
                return found
            }
        }
    }
}
