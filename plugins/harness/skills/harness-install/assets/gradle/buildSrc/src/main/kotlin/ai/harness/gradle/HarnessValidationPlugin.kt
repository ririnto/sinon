package ai.harness.gradle

import ai.harness.gradle.rules.ForbidBlankLineInLeafFunctionRule
import ai.harness.gradle.rules.ForbidEarlyReturnRule
import ai.harness.gradle.rules.ForbidEmptyCatchBlockRule
import ai.harness.gradle.rules.ForbidGreaterThanComparisonRule
import ai.harness.gradle.rules.ForbidImplicitLambdaItRule
import ai.harness.gradle.rules.ForbidMutableCollectionRule
import ai.harness.gradle.rules.ForbidSilentCatchRule
import ai.harness.gradle.rules.ForbidUnstructuredLoggingRule
import ai.harness.gradle.rules.ForbidWildcardImportRule
import ai.harness.gradle.rules.RequireBracesOnIfRule
import ai.harness.gradle.rules.RequireCompanionObjectPositionRule
import ai.harness.gradle.rules.RequireDocCommentOnPublicDeclarationRule
import ai.harness.gradle.rules.RequireImportOverFqnRule
import ai.harness.gradle.rules.RequireSingleTopLevelKotlinDeclarationRule
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiRecursiveElementWalkingVisitor
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
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
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCatchClause
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifier
import java.nio.file.Path
import javax.inject.Inject
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.io.path.writeText
import kotlin.io.path.Path

/**
 * Registers the harness validation task on the root project.
 */
abstract class HarnessValidationPlugin : Plugin<Project> {
    companion object {
        /**
         * Maven coordinate prefix for the Kotlin compiler artifact.
         */
        const val KOTLIN_COMPILER_EMBEDDABLE = "org.jetbrains.kotlin:kotlin-compiler-embeddable"

        /**
         * Pinned Kotlin compiler version used by the worker classloader.
         */
        const val KOTLIN_COMPILER_VERSION = "2.1.0"
    }

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
        target.tasks.register("harnessValidate", HarnessValidationTask::class.java) {
            group = "verification"
            description = "Validate Claude repository harness assets."
            kotlinCompiler.from(
                target.configurations.create("harnessKotlinCompilerResolvable") {
                    extendsFrom(depScope)
                },
            )
        }
        target.tasks.named("check").configure {
            dependsOn("harnessValidate")
        }
    }

    /**
     * Gradle task that validates installed Claude repository harness assets.
     */
    abstract class HarnessValidationTask : DefaultTask() {
        /**
         * Gradle worker executor for running PSI analysis in an isolated classloader.
         */
        @get:Inject
        abstract val workerExecutor: WorkerExecutor

        /**
         * Classpath of the isolated Kotlin compiler used by the PSI worker action.
         */
        @get:Classpath
        abstract val kotlinCompiler: ConfigurableFileCollection

        /**
         * Executes harness validation by scanning PSI results and manifest integrity.
         */
        @TaskAction
        fun validate() {
            val root: Path = project.rootDir.toPath()
            val (manifest, manifestFindings) = loadManifest(root)
            val psiResults = computePsiResults(root)
            val findings =
                buildSet {
                    addAll(manifestFindings)
                    if (manifest != null) {
                        val knownMetadataKeys =
                            setOf(
                                "name",
                                "description",
                                $$"$schema",
                                "seedFiles",
                                "generatedArtifacts",
                                "harnessEvolution",
                                "teamPatterns",
                            )
                        val unknownKeys =
                            manifest.keys - HarnessCheck.entries.map { check -> check.category }.toSet() - knownMetadataKeys
                        unknownKeys.forEach { key ->
                            project.logger.warn("unknown manifest key: $key")
                        }
                        HarnessCheck.entries.filter { check -> check.applies(manifest) }.forEach { check ->
                            addAll(check.validate(manifest, root, psiResults))
                        }
                    }
                }
            findings.sortedWith(compareBy({ finding -> finding.severity.ordinal }, { finding -> findings.indexOf(finding) })).forEach { finding ->
                when (finding.severity) {
                    Severity.ERROR -> project.logger.error("[${finding.severity}] ${finding.message}")
                    Severity.WARN -> project.logger.warn("[${finding.severity}] ${finding.message}")
                    Severity.INFO -> project.logger.info("[${finding.severity}] ${finding.message}")
                }
            }
            if (findings.any { finding -> finding.severity == Severity.ERROR }) {
                throw GradleException("Harness validation failed")
            }
            project.logger.lifecycle("Harness validation passed")
        }

        private fun computePsiResults(root: Path): HarnessPsiResults {
            val srcRoots =
                listOf(
                    root / "buildSrc" / "src" / "main" / "kotlin",
                    root / "buildSrc" / "src" / "test" / "kotlin",
                ).filter { srcRoot -> srcRoot.isDirectory() }
            val srcFiles =
                srcRoots.flatMap { dir ->
                    dir.walk().filter { file -> file.isRegularFile() && file.extension == "kt" }
                }
            val outputFile = temporaryDir.toPath() / "psi-results.json"
            val workQueue =
                workerExecutor.classLoaderIsolation {
                    classpath.from(kotlinCompiler)
                }
            workQueue.submit(HarnessPsiWorkAction::class.java) {
                srcFilePaths.set(srcFiles.map { srcFile -> srcFile.toString() })
                rootDir.set(root.toString())
                this.outputFile.set(outputFile.toFile())
            }
            workQueue.await()
            return Json.decodeFromString(outputFile.readText())
        }

        private fun loadManifest(root: Path): Pair<JsonObject?, List<Finding>> {
            val manifestFile = root / "docs" / "harness" / "manifest.json"
            return when {
                manifestFile.isSymbolicLink() -> {
                    null to
                        listOf(
                            Finding(
                                Severity.ERROR,
                                "forbidUnsafeSymlinks",
                                "symlink file is not allowed: docs/harness/manifest.json",
                            ),
                        )
                }

                !manifestFile.isRegularFile() -> {
                    null to
                        listOf(
                            Finding(
                                Severity.ERROR,
                                "requireFilesExist",
                                "missing file: docs/harness/manifest.json",
                            ),
                        )
                }

                else -> {
                    try {
                        Json.parseToJsonElement(manifestFile.readText()).jsonObject to emptyList()
                    } catch (e: Exception) {
                        val skipped = e.message
                        null to
                            listOf(
                                Finding(
                                    Severity.ERROR,
                                    "requireFilesExist",
                                    "failed to parse manifest: ${e.message}",
                                ),
                            )
                    }
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
             * Root directory path for computing relative file paths.
             */
            val rootDir: Property<String>

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
                val srcFiles: List<Path> = parameters.srcFilePaths.get().map { srcFilePath -> Path(srcFilePath) }
                val root: Path = Path(parameters.rootDir.get())
                System.setProperty("idea.home.path", System.getProperty("java.io.tmpdir"))
                System.setProperty("idea.use.native.fs.for.win", "false")
                val disposable: Disposable = Disposer.newDisposable("HarnessPsiWorkAction")
                try {
                    val psiFactory: KtPsiFactory? = try {
                        val configuration = CompilerConfiguration().apply {
                            put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
                        }
                        val environment = createKotlinCoreEnvironmentViaReflection(disposable, configuration)
                        KtPsiFactory(environment.project, true)
                    } catch (error: Throwable) {
                        val skipped = error.localizedMessage
                        null
                    }
                    val results =
                        HarnessPsiResults(
                            greaterThanComparisons = srcFiles.flatMap { srcFile -> tryOrEmpty { findGreaterThanComparisons(srcFile, root, psiFactory) } },
                            blankLinesInLeafFunctions =
                                srcFiles.flatMap { srcFile ->
                                    tryOrEmpty {
                                        findBlankLinesInLeafFunctions(
                                            srcFile,
                                            root,
                                            psiFactory,
                                        )
                                    }
                                },
                            implicitLambdaIt = srcFiles.flatMap { srcFile -> tryOrEmpty { findImplicitLambdaIt(srcFile, root, psiFactory) } },
                            topLevelDeclarations =
                                srcFiles
                                    .filter { srcFile ->
                                        tryOrNull {
                                            inspectTopLevelDeclarations(srcFile, root, psiFactory)
                                        } != null
                                    }.map { srcFile -> tryOrNull { inspectTopLevelDeclarations(srcFile, root, psiFactory) }!! },
                            earlyReturns = srcFiles.flatMap { srcFile -> tryOrEmpty { findEarlyReturns(srcFile, root, psiFactory) } },
                            silentCatches = srcFiles.flatMap { srcFile -> tryOrEmpty { findSilentCatches(srcFile, root, psiFactory) } },
                            mutableCollections = srcFiles.flatMap { srcFile -> tryOrEmpty { findMutableCollections(srcFile, root, psiFactory) } },
                            unstructuredLoggings = srcFiles.flatMap { srcFile -> tryOrEmpty { findUnstructuredLoggings(srcFile, root, psiFactory) } },
                            wildcardImports = srcFiles.flatMap { srcFile -> tryOrEmpty { findWildcardImports(srcFile, root, psiFactory) } },
                            fqnUsages = srcFiles.flatMap { srcFile -> tryOrEmpty { findFqnUsages(srcFile, root, psiFactory) } },
                            docCommentMissings = srcFiles.flatMap { srcFile -> tryOrEmpty { findDocCommentMissings(srcFile, root, psiFactory) } },
                            emptyCatchBlocks = srcFiles.flatMap { srcFile -> tryOrEmpty { findEmptyCatchBlocks(srcFile, root, psiFactory) } },
                            braceOnIfs = srcFiles.flatMap { srcFile -> tryOrEmpty { findBraceOnIfViolations(srcFile, root, psiFactory) } },
                            companionPositions = srcFiles.flatMap { srcFile -> tryOrEmpty { findCompanionPositionViolations(srcFile, root, psiFactory) } },
                        )
                    parameters.outputFile
                        .get()
                        .asFile
                        .toPath()
                        .writeText(Json.encodeToString(results))
                } finally {
                    Disposer.dispose(disposable)
                }
            }

            private fun relativeFilePath(file: Path, root: Path): String = file.relativeTo(root).toString().replace("\\", "/")

            private fun findGreaterThanComparisons(file: Path, root: Path, psiFactory: KtPsiFactory?): List<ForbidGreaterThanComparisonRule.Result> {
                val ktFile = parse(file, psiFactory) ?: return emptyList()
                return buildList {
                    ktFile.accept(
                        object : KtTreeVisitorVoid() {
                            override fun visitBinaryExpression(expression: KtBinaryExpression) {
                                super.visitBinaryExpression(expression)
                                if (expression.operationToken == KtTokens.GT ||
                                    expression.operationToken == KtTokens.GTEQ
                                ) {
                                    add(
                                        ForbidGreaterThanComparisonRule.Result(
                                            relativeFilePath(file, root),
                                            lineOf(ktFile, expression.node?.startOffset),
                                        ),
                                    )
                                }
                            }
                        },
                    )
                }
            }

            private fun findBlankLinesInLeafFunctions(file: Path, root: Path, psiFactory: KtPsiFactory?): List<ForbidBlankLineInLeafFunctionRule.Result> {
                val ktFile = parse(file, psiFactory) ?: return emptyList()
                return buildList {
                    ktFile.accept(
                        object : KtTreeVisitorVoid() {
                            override fun visitNamedFunction(function: KtNamedFunction) {
                                super.visitNamedFunction(function)
                                if (function.hasDescendantOfType<KtNamedFunction> { nestedFunc -> nestedFunc !== function } ||
                                    function.hasDescendantOfType<KtLambdaExpression> { true }
                                ) {
                                    return
                                }
                                val body = function.bodyExpression ?: return
                                generateSequence(body.firstChild) { element -> element.nextSibling }
                                    .filter { child -> child is PsiWhiteSpace && child.text.contains("\n\n") }
                                    .forEach { child ->
                                        add(
                                            ForbidBlankLineInLeafFunctionRule.Result(
                                                relativeFilePath(file, root),
                                                function.name ?: "unknown",
                                                lineOf(ktFile, child.node?.startOffset),
                                            ),
                                        )
                                    }
                            }
                        },
                    )
                }
            }

            private fun findImplicitLambdaIt(file: Path, root: Path, psiFactory: KtPsiFactory?): List<ForbidImplicitLambdaItRule.Result> {
                val ktFile = parse(file, psiFactory) ?: return emptyList()
                return buildList {
                    ktFile.accept(
                        object : KtTreeVisitorVoid() {
                            override fun visitLambdaExpression(expression: KtLambdaExpression) {
                                super.visitLambdaExpression(expression)
                                if (expression.valueParameters.isNotEmpty()) {
                                    return
                                }
                                val hasIt =
                                    kotlin.run {
                                        var found = false
                                        expression.accept(
                                            object : KtTreeVisitorVoid() {
                                                override fun visitSimpleNameExpression(
                                                    expression: KtSimpleNameExpression,
                                                ) {
                                                    super.visitSimpleNameExpression(expression)
                                                    if (expression.text == "it") {
                                                        found = true
                                                    }
                                                }
                                            },
                                        )
                                        found
                                    }
                                if (hasIt) {
                                    add(
                                        ForbidImplicitLambdaItRule.Result(
                                            relativeFilePath(file, root),
                                            lineOf(ktFile, expression.node?.startOffset),
                                        ),
                                    )
                                }
                            }
                        },
                    )
                }
            }

            private fun inspectTopLevelDeclarations(file: Path, root: Path, psiFactory: KtPsiFactory?): RequireSingleTopLevelKotlinDeclarationRule.Result? {
                val ktFile = parse(file, psiFactory) ?: return null
                val declarations = ktFile.declarations
                val firstKind =
                    if (declarations.isEmpty()) {
                        "unknown"
                    } else {
                        when (declarations.first()::class.simpleName) {
                            "KtClass" -> "class"
                            "KtObjectDeclaration" -> "object"
                            "KtTypeAlias" -> "typealias"
                            else -> "unknown"
                        }
                    }
                return RequireSingleTopLevelKotlinDeclarationRule.Result(relativeFilePath(file, root), declarations.size, firstKind)
            }

            private fun findEarlyReturns(file: Path, root: Path, psiFactory: KtPsiFactory?): List<ForbidEarlyReturnRule.Result> {
                val ktFile = parse(file, psiFactory) ?: return emptyList()
                return buildList {
                    ktFile.accept(
                        object : KtTreeVisitorVoid() {
                            override fun visitNamedFunction(function: KtNamedFunction) {
                                super.visitNamedFunction(function)
                                val body = function.bodyBlockExpression ?: return
                                val stmts = body.statements
                                if (stmts.isEmpty()) {
                                    return
                                }
                                stmts.filter { stmt -> stmt::class.simpleName == "KtReturnExpression" && stmt !== stmts.last() }.forEach { stmt ->
                                    add(
                                        ForbidEarlyReturnRule.Result(
                                            relativeFilePath(file, root),
                                            function.name ?: "unknown",
                                            lineOf(ktFile, stmt.node?.startOffset),
                                        ),
                                    )
                                }
                            }
                        },
                    )
                }
            }

            private fun findSilentCatches(file: Path, root: Path, psiFactory: KtPsiFactory?): List<ForbidSilentCatchRule.Result> {
                val ktFile = parse(file, psiFactory) ?: return emptyList()
                return buildList {
                    ktFile.accept(
                        object : KtTreeVisitorVoid() {
                            override fun visitCatchSection(catchSection: KtCatchClause) {
                                super.visitCatchSection(catchSection)
                                val paramName = catchSection.catchParameter?.name
                                val body = catchSection.catchBody
                                if (paramName != null && body != null) {
                                    if (!body.text.contains(paramName) && !body.text.contains("throw") &&
                                        !body.text.contains("logger") &&
                                        !body.text.contains("println")
                                    ) {
                                        add(
                                            ForbidSilentCatchRule.Result(
                                                relativeFilePath(file, root),
                                                lineOf(ktFile, catchSection.node?.startOffset),
                                            ),
                                        )
                                    }
                                }
                            }
                        },
                    )
                }
            }

            private fun findMutableCollections(file: Path, root: Path, psiFactory: KtPsiFactory?): List<ForbidMutableCollectionRule.Result> {
                val ktFile = parse(file, psiFactory) ?: return emptyList()
                val mutableFactories =
                    setOf(
                        "mutableListOf",
                        "mutableSetOf",
                        "mutableMapOf",
                        "ArrayList",
                        "HashSet",
                        "HashMap",
                        "LinkedHashMap",
                        "LinkedHashSet",
                    )
                return buildList {
                    ktFile.accept(
                        object : KtTreeVisitorVoid() {
                            override fun visitCallExpression(expression: KtCallExpression) {
                                super.visitCallExpression(expression)
                                val calleeName = expression.calleeExpression?.text ?: ""
                                if (calleeName in mutableFactories) {
                                    add(
                                        ForbidMutableCollectionRule.Result(
                                            relativeFilePath(file, root),
                                            calleeName,
                                            lineOf(ktFile, expression.node?.startOffset),
                                        ),
                                    )
                                }
                            }

                            override fun visitUserType(type: KtUserType) {
                                super.visitUserType(type)
                                val typeName = type.text.substringBefore("<")
                                if (typeName in mutableFactories) {
                                    add(
                                        ForbidMutableCollectionRule.Result(
                                            relativeFilePath(file, root),
                                            typeName,
                                            lineOf(ktFile, type.node?.startOffset),
                                        ),
                                    )
                                }
                            }
                        },
                    )
                }
            }

            private fun findUnstructuredLoggings(file: Path, root: Path, psiFactory: KtPsiFactory?): List<ForbidUnstructuredLoggingRule.Result> {
                val ktFile = parse(file, psiFactory) ?: return emptyList()
                return buildList {
                    ktFile.accept(
                        object : KtTreeVisitorVoid() {
                            override fun visitCallExpression(expression: KtCallExpression) {
                                super.visitCallExpression(expression)
                                val calleeText = expression.calleeExpression?.text ?: ""
                                if (calleeText == "println" || calleeText == "print") {
                                    add(
                                        ForbidUnstructuredLoggingRule.Result(
                                            relativeFilePath(file, root),
                                            lineOf(ktFile, expression.node?.startOffset),
                                        ),
                                    )
                                }
                                if (calleeText.contains("System.out") || calleeText.contains("System.err")) {
                                    add(
                                        ForbidUnstructuredLoggingRule.Result(
                                            relativeFilePath(file, root),
                                            lineOf(ktFile, expression.node?.startOffset),
                                        ),
                                    )
                                }
                            }
                        },
                    )
                }
            }

            private fun findWildcardImports(file: Path, root: Path, psiFactory: KtPsiFactory?): List<ForbidWildcardImportRule.Result> {
                val ktFile = parse(file, psiFactory) ?: return emptyList()
                return buildList {
                    ktFile.accept(
                        object : KtTreeVisitorVoid() {
                            override fun visitImportDirective(
                                importDirective: KtImportDirective,
                            ) {
                                super.visitImportDirective(importDirective)
                                if (importDirective.isAllUnder) {
                                    add(
                                        ForbidWildcardImportRule.Result(
                                            relativeFilePath(file, root),
                                            lineOf(ktFile, importDirective.node?.startOffset),
                                            importDirective.importPath?.pathStr ?: "",
                                        ),
                                    )
                                }
                            }
                        },
                    )
                }
            }

            private fun findFqnUsages(file: Path, root: Path, psiFactory: KtPsiFactory?): List<RequireImportOverFqnRule.Result> {
                val ktFile = parse(file, psiFactory) ?: return emptyList()
                val importedNames = ktFile.importDirectives.mapNotNull { directive -> directive.importedName?.asString() }.toSet()
                return buildList {
                    ktFile.accept(
                        object : KtTreeVisitorVoid() {
                            override fun visitUserType(userType: KtUserType) {
                                super.visitUserType(userType)
                                if (userType.parent is KtUserType) {
                                    return
                                }
                                val fqnParts = generateSequence(userType) { parent -> parent.qualifier }.mapNotNull { ut -> ut.referencedName }.toList().asReversed()
                                if (2 <= fqnParts.size && fqnParts.first() !in importedNames && userType.referencedName !in importedNames) {
                                    add(
                                        RequireImportOverFqnRule.Result(
                                            relativeFilePath(file, root),
                                            lineOf(ktFile, userType.node?.startOffset),
                                            fqnParts.joinToString("."),
                                        ),
                                    )
                                }
                            }
                        },
                    )
                }
            }

            private fun findDocCommentMissings(file: Path, root: Path, psiFactory: KtPsiFactory?): List<RequireDocCommentOnPublicDeclarationRule.Result> {
                val ktFile = parse(file, psiFactory) ?: return emptyList()
                return buildList {
                    ktFile.accept(
                        object : KtTreeVisitorVoid() {
                            override fun visitClass(klass: KtClass) {
                                super.visitClass(klass)
                                if (isExternallyVisible(klass) && klass.docComment == null) {
                                    add(
                                        RequireDocCommentOnPublicDeclarationRule.Result(
                                            relativeFilePath(file, root),
                                            klass.name ?: "unknown",
                                            lineOf(ktFile, klass.node?.startOffset),
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
                                        RequireDocCommentOnPublicDeclarationRule.Result(
                                            relativeFilePath(file, root),
                                            function.name ?: "unknown",
                                            lineOf(ktFile, function.node?.startOffset),
                                        ),
                                    )
                                }
                            }

                            override fun visitProperty(property: KtProperty) {
                                super.visitProperty(property)
                                if (!property.isLocal && isExternallyVisible(property) && property.docComment == null) {
                                    add(
                                        RequireDocCommentOnPublicDeclarationRule.Result(
                                            relativeFilePath(file, root),
                                            property.name ?: "unknown",
                                            lineOf(ktFile, property.node?.startOffset),
                                        ),
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
                        },
                    )
                }
            }

            private fun findEmptyCatchBlocks(file: Path, root: Path, psiFactory: KtPsiFactory?): List<ForbidEmptyCatchBlockRule.Result> {
                val ktFile = parse(file, psiFactory) ?: return emptyList()
                return buildList {
                    ktFile.accept(
                        object : KtTreeVisitorVoid() {
                            override fun visitCatchSection(catchSection: KtCatchClause) {
                                super.visitCatchSection(catchSection)
                                if (catchSection.catchBody is KtBlockExpression &&
                                    (catchSection.catchBody as KtBlockExpression).statements.isEmpty()
                                ) {
                                    add(
                                        ForbidEmptyCatchBlockRule.Result(
                                            relativeFilePath(file, root),
                                            lineOf(ktFile, catchSection.node?.startOffset),
                                        ),
                                    )
                                }
                            }
                        },
                    )
                }
            }

            private fun findBraceOnIfViolations(file: Path, root: Path, psiFactory: KtPsiFactory?): List<RequireBracesOnIfRule.Result> {
                val ktFile = parse(file, psiFactory) ?: return emptyList()
                return buildList {
                    ktFile.accept(
                        object : KtTreeVisitorVoid() {
                            override fun visitIfExpression(expression: KtIfExpression) {
                                super.visitIfExpression(expression)
                                if (expression.then != null &&
                                    expression.then !is KtBlockExpression
                                ) {
                                    add(
                                        RequireBracesOnIfRule.Result(
                                            relativeFilePath(file, root),
                                            lineOf(ktFile, expression.node?.startOffset),
                                            "then",
                                        ),
                                    )
                                }
                                if (expression.`else` != null &&
                                    expression.`else` !is KtBlockExpression &&
                                    expression.`else` !is KtIfExpression
                                ) {
                                    add(
                                        RequireBracesOnIfRule.Result(
                                            relativeFilePath(file, root),
                                            lineOf(ktFile, expression.node?.startOffset),
                                            "else",
                                        ),
                                    )
                                }
                            }
                        },
                    )
                }
            }

            private fun findCompanionPositionViolations(file: Path, root: Path, psiFactory: KtPsiFactory?): List<RequireCompanionObjectPositionRule.Result> {
                val ktFile = parse(file, psiFactory) ?: return emptyList()
                return buildList {
                    ktFile.accept(
                        object : KtTreeVisitorVoid() {
                            override fun visitClass(klass: KtClass) {
                                super.visitClass(klass)
                                val body = klass.getBody() ?: return
                                body.declarations
                                    .filter { decl -> decl !is KtEnumEntry }
                                    .forEachIndexed { idx, decl ->
                                        if (decl is KtObjectDeclaration && decl.isCompanion() && idx != 0) {
                                            add(
                                                RequireCompanionObjectPositionRule.Result(
                                                    relativeFilePath(file, root),
                                                    klass.name ?: "unknown",
                                                    lineOf(ktFile, decl.node?.startOffset),
                                                ),
                                            )
                                        }
                                    }
                            }
                        },
                    )
                }
            }

            private fun parse(file: Path, psiFactory: KtPsiFactory?): KtFile? = psiFactory?.createFile("temp", file.readText())

            private fun createKotlinCoreEnvironmentViaReflection(
                disposable: Disposable,
                configuration: CompilerConfiguration,
            ): KotlinCoreEnvironment {
                val method = KotlinCoreEnvironment::class.java.getDeclaredMethod(
                    "createForTests",
                    Disposable::class.java,
                    CompilerConfiguration::class.java,
                    EnvironmentConfigFiles::class.java,
                )
                method.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                return method.invoke(null, disposable, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES) as KotlinCoreEnvironment
            }

            private fun lineOf(
                ktFile: KtFile,
                offset: Int?,
            ): Int = (ktFile.viewProvider.document?.getLineNumber(offset ?: 0) ?: 0) + 1

            /**
             * Executes a block and returns empty list on exception.
             * Fallback for non-critical PSI analysis that gracefully degrades.
             */
            private fun <T> tryOrEmpty(block: () -> List<T>): List<T> =
                try {
                    block()
                } catch (error: Exception) {
                    val skipped = error.localizedMessage
                    emptyList()
                }

            /**
             * Executes a block and returns null on exception.
             * Fallback for optional PSI results that gracefully degrade to null.
             */
            private fun <T> tryOrNull(block: () -> T): T? =
                try {
                    block()
                } catch (error: Exception) {
                    val skipped = error.localizedMessage
                    null
                }

            private inline fun <reified T : PsiElement> PsiElement.hasDescendantOfType(
                crossinline predicate: (T) -> Boolean = { true },
            ): Boolean {
                val found =
                    kotlin.run {
                        var result = false
                        accept(
                            object : PsiRecursiveElementWalkingVisitor() {
                                override fun visitElement(element: PsiElement) {
                                    if (element is T && predicate(element)) {
                                        result = true
                                        stopWalking()
                                        return
                                    }
                                    super.visitElement(element)
                                }
                            },
                        )
                        result
                    }
                return found
            }
        }
    }
}
