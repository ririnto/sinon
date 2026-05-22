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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
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
import javax.inject.Inject
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
import kotlin.io.path.Path as kPath

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
            val findings =
                buildSet {
                    addAll(manifestFindings)
                    if (manifest != null) {
                        val knownMetadataKeys =
                            setOf(
                                "name",
                                "description",
                                "\$schema",
                                "seedFiles",
                                "generatedArtifacts",
                                "harnessEvolution",
                                "teamPatterns",
                            )
                        val unknownKeys =
                            manifest.keys - HarnessCheck.entries.map { it.category }.toSet() - knownMetadataKeys
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
            findings.sortedWith(compareBy({ it.severity.ordinal }, { findings.indexOf(it) })).forEach { finding ->
                when (finding.severity) {
                    Severity.ERROR -> project.logger.error("[${finding.severity}] ${finding.message}")
                    Severity.WARN -> project.logger.warn("[${finding.severity}] ${finding.message}")
                    Severity.INFO -> project.logger.info("[${finding.severity}] ${finding.message}")
                }
            }
            if (findings.any { it.severity == Severity.ERROR }) {
                throw GradleException("Harness validation failed")
            }
            project.logger.lifecycle("Harness validation passed")
        }

        private fun computePsiResults(root: Path): HarnessPsiResults {
            val srcRoots =
                listOf(
                    root / "buildSrc" / "src" / "main" / "kotlin",
                    root / "buildSrc" / "src" / "test" / "kotlin",
                ).filter { it.isDirectory() }
            val srcFiles =
                srcRoots.flatMap { dir ->
                    dir.walk().filter { it.isRegularFile() && it.extension == "kt" }
                }
            val outputFile = temporaryDir.toPath() / "psi-results.json"
            val workQueue =
                workerExecutor.classLoaderIsolation {
                    classpath.from(kotlinCompiler)
                }
            workQueue.submit(HarnessPsiWorkAction::class.java) {
                srcFilePaths.set(srcFiles.map { it.toString() })
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
                val results =
                    HarnessPsiResults(
                        greaterThanComparisons = srcFiles.flatMap { tryOrEmpty { findGreaterThanComparisons(it) } },
                        blankLinesInLeafFunctions =
                            srcFiles.flatMap {
                                tryOrEmpty {
                                    findBlankLinesInLeafFunctions(
                                        it,
                                    )
                                }
                            },
                        implicitLambdaIt = srcFiles.flatMap { tryOrEmpty { findImplicitLambdaIt(it) } },
                        topLevelDeclarations =
                            srcFiles
                                .filter { srcFile ->
                                    tryOrNull {
                                        inspectTopLevelDeclarations(srcFile)
                                    } != null
                                }.map { srcFile -> tryOrNull { inspectTopLevelDeclarations(srcFile) }!! },
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
                parameters.outputFile
                    .get()
                    .asFile
                    .toPath()
                    .writeText(Json.encodeToString(results))
            }

            private fun findGreaterThanComparisons(file: Path): List<ForbidGreaterThanComparisonRule.Result> {
                val ktFile = parse(file)
                return buildList {
                    ktFile.accept(
                        object : KtVisitorVoid() {
                            override fun visitBinaryExpression(expression: KtBinaryExpression) {
                                super.visitBinaryExpression(expression)
                                if (expression.operationToken == KtTokens.GT ||
                                    expression.operationToken == KtTokens.GTEQ
                                ) {
                                    add(
                                        ForbidGreaterThanComparisonRule.Result(
                                            file.name,
                                            lineOf(ktFile, expression.node?.startOffset),
                                        ),
                                    )
                                }
                            }
                        },
                    )
                }
            }

            private fun findBlankLinesInLeafFunctions(file: Path): List<ForbidBlankLineInLeafFunctionRule.Result> {
                val ktFile = parse(file)
                return buildList {
                    ktFile.accept(
                        object : KtVisitorVoid() {
                            override fun visitNamedFunction(function: KtNamedFunction) {
                                super.visitNamedFunction(function)
                                if (function.hasDescendantOfType<KtNamedFunction> { it !== function } ||
                                    function.hasDescendantOfType<KtLambdaExpression> { true }
                                ) {
                                    return
                                }
                                val body = function.bodyExpression ?: return
                                generateSequence(body.firstChild) { it.nextSibling }.forEach { child ->
                                    if (child.text.count { c -> c == '\n' } >= 2) {
                                        add(
                                            ForbidBlankLineInLeafFunctionRule.Result(
                                                file.name,
                                                function.name ?: "unknown",
                                                lineOf(ktFile, child.node?.startOffset),
                                            ),
                                        )
                                    }
                                }
                            }
                        },
                    )
                }
            }

            private fun findImplicitLambdaIt(file: Path): List<ForbidImplicitLambdaItRule.Result> {
                val ktFile = parse(file)
                return buildList {
                    ktFile.accept(
                        object : KtVisitorVoid() {
                            override fun visitLambdaExpression(expression: KtLambdaExpression) {
                                super.visitLambdaExpression(expression)
                                if (expression.valueParameters.isNotEmpty()) {
                                    return
                                }
                                val hasIt =
                                    kotlin.run {
                                        var found = false
                                        expression.accept(
                                            object : KtVisitorVoid() {
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
                                            file.name,
                                            lineOf(ktFile, expression.node?.startOffset),
                                        ),
                                    )
                                }
                            }
                        },
                    )
                }
            }

            private fun inspectTopLevelDeclarations(file: Path): RequireSingleTopLevelKotlinDeclarationRule.Result {
                val ktFile = parse(file)
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
                return RequireSingleTopLevelKotlinDeclarationRule.Result(file.name, declarations.size, firstKind)
            }

            private fun findEarlyReturns(file: Path): List<ForbidEarlyReturnRule.Result> {
                val ktFile = parse(file)
                return buildList {
                    ktFile.accept(
                        object : KtVisitorVoid() {
                            override fun visitNamedFunction(function: KtNamedFunction) {
                                super.visitNamedFunction(function)
                                val body = function.bodyBlockExpression ?: return
                                val stmts = body.statements
                                if (stmts.isEmpty()) {
                                    return
                                }
                                stmts.forEach { stmt ->
                                    if (stmt::class.simpleName == "KtReturnExpression") {
                                        if (stmt !== stmts.last()) {
                                            add(
                                                ForbidEarlyReturnRule.Result(
                                                    file.name,
                                                    function.name ?: "unknown",
                                                    lineOf(ktFile, stmt.node?.startOffset),
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                        },
                    )
                }
            }

            private fun findSilentCatches(file: Path): List<ForbidSilentCatchRule.Result> {
                val ktFile = parse(file)
                return buildList {
                    ktFile.accept(
                        object : KtVisitorVoid() {
                            override fun visitCatchSection(catchSection: org.jetbrains.kotlin.psi.KtCatchClause) {
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
                                                file.name,
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

            private fun findMutableCollections(file: Path): List<ForbidMutableCollectionRule.Result> {
                val ktFile = parse(file)
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
                        object : KtVisitorVoid() {
                            override fun visitCallExpression(expression: org.jetbrains.kotlin.psi.KtCallExpression) {
                                super.visitCallExpression(expression)
                                val calleeName = expression.calleeExpression?.text ?: ""
                                if (calleeName in mutableFactories) {
                                    add(
                                        ForbidMutableCollectionRule.Result(
                                            file.name,
                                            calleeName,
                                            lineOf(ktFile, expression.node?.startOffset),
                                        ),
                                    )
                                }
                            }

                            override fun visitUserType(type: org.jetbrains.kotlin.psi.KtUserType) {
                                super.visitUserType(type)
                                val typeName = type.text.substringBefore("<")
                                if (typeName in mutableFactories) {
                                    add(
                                        ForbidMutableCollectionRule.Result(
                                            file.name,
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

            private fun findUnstructuredLoggings(file: Path): List<ForbidUnstructuredLoggingRule.Result> {
                val ktFile = parse(file)
                return buildList {
                    ktFile.accept(
                        object : KtVisitorVoid() {
                            override fun visitCallExpression(expression: org.jetbrains.kotlin.psi.KtCallExpression) {
                                super.visitCallExpression(expression)
                                val calleeText = expression.calleeExpression?.text ?: ""
                                if (calleeText == "println" || calleeText == "print") {
                                    add(
                                        ForbidUnstructuredLoggingRule.Result(
                                            file.name,
                                            lineOf(ktFile, expression.node?.startOffset),
                                        ),
                                    )
                                }
                                if (calleeText.contains("System.out") || calleeText.contains("System.err")) {
                                    add(
                                        ForbidUnstructuredLoggingRule.Result(
                                            file.name,
                                            lineOf(ktFile, expression.node?.startOffset),
                                        ),
                                    )
                                }
                            }
                        },
                    )
                }
            }

            private fun findWildcardImports(file: Path): List<ForbidWildcardImportRule.Result> {
                val ktFile = parse(file)
                return buildList {
                    ktFile.accept(
                        object : KtVisitorVoid() {
                            override fun visitImportDirective(
                                importDirective: org.jetbrains.kotlin.psi.KtImportDirective,
                            ) {
                                super.visitImportDirective(importDirective)
                                if (importDirective.isAllUnder) {
                                    add(
                                        ForbidWildcardImportRule.Result(
                                            file.name,
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

            private fun findFqnUsages(file: Path): List<RequireImportOverFqnRule.Result> {
                val ktFile = parse(file)
                val imports =
                    ktFile.importDirectives
                        .filter { directive ->
                            directive.importedName?.asString() != null
                        }.map { directive ->
                            directive.importedName?.asString()!!
                        }.toSet()
                return buildList {
                    ktFile.accept(
                        object : KtVisitorVoid() {
                            override fun visitDotQualifiedExpression(
                                expression: org.jetbrains.kotlin.psi.KtDotQualifiedExpression,
                            ) {
                                super.visitDotQualifiedExpression(expression)
                                val depth =
                                    generateSequence(expression.receiverExpression) {
                                        (it as? org.jetbrains.kotlin.psi.KtDotQualifiedExpression)?.receiverExpression
                                    }.count() +
                                        1
                                if (depth >= 2) {
                                    val simpleName = expression.selectorExpression?.text ?: ""
                                    if (simpleName.isNotEmpty() && simpleName !in imports) {
                                        add(
                                            RequireImportOverFqnRule.Result(
                                                file.name,
                                                lineOf(ktFile, expression.node?.startOffset),
                                            ),
                                        )
                                    }
                                }
                            }
                        },
                    )
                }
            }

            private fun findDocCommentMissings(file: Path): List<RequireDocCommentOnPublicDeclarationRule.Result> {
                val ktFile = parse(file)
                return buildList {
                    ktFile.accept(
                        object : KtVisitorVoid() {
                            override fun visitClass(klass: org.jetbrains.kotlin.psi.KtClass) {
                                super.visitClass(klass)
                                val visibility = klass.visibilityModifier()?.text
                                if (visibility != "private" && visibility != "internal" && klass.docComment == null) {
                                    add(
                                        RequireDocCommentOnPublicDeclarationRule.Result(
                                            file.name,
                                            klass.name ?: "unknown",
                                            lineOf(ktFile, klass.node?.startOffset),
                                        ),
                                    )
                                }
                            }

                            override fun visitNamedFunction(function: KtNamedFunction) {
                                super.visitNamedFunction(function)
                                val visibility = function.visibilityModifier()?.text
                                if (visibility != "private" && visibility != "internal" &&
                                    !function.hasModifier(KtTokens.OVERRIDE_KEYWORD) &&
                                    function.docComment == null
                                ) {
                                    add(
                                        RequireDocCommentOnPublicDeclarationRule.Result(
                                            file.name,
                                            function.name ?: "unknown",
                                            lineOf(ktFile, function.node?.startOffset),
                                        ),
                                    )
                                }
                            }

                            override fun visitProperty(property: org.jetbrains.kotlin.psi.KtProperty) {
                                super.visitProperty(property)
                                val visibility = property.visibilityModifier()?.text
                                if (visibility != "private" && visibility != "internal" &&
                                    property.docComment == null
                                ) {
                                    add(
                                        RequireDocCommentOnPublicDeclarationRule.Result(
                                            file.name,
                                            property.name ?: "unknown",
                                            lineOf(ktFile, property.node?.startOffset),
                                        ),
                                    )
                                }
                            }
                        },
                    )
                }
            }

            private fun findEmptyCatchBlocks(file: Path): List<ForbidEmptyCatchBlockRule.Result> {
                val ktFile = parse(file)
                return buildList {
                    ktFile.accept(
                        object : KtVisitorVoid() {
                            override fun visitCatchSection(catchSection: org.jetbrains.kotlin.psi.KtCatchClause) {
                                super.visitCatchSection(catchSection)
                                if (catchSection.catchBody is org.jetbrains.kotlin.psi.KtBlockExpression &&
                                    (catchSection.catchBody as org.jetbrains.kotlin.psi.KtBlockExpression).statements.isEmpty()
                                ) {
                                    add(
                                        ForbidEmptyCatchBlockRule.Result(
                                            file.name,
                                            lineOf(ktFile, catchSection.node?.startOffset),
                                        ),
                                    )
                                }
                            }
                        },
                    )
                }
            }

            private fun findBraceOnIfViolations(file: Path): List<RequireBracesOnIfRule.Result> {
                val ktFile = parse(file)
                return buildList {
                    ktFile.accept(
                        object : KtVisitorVoid() {
                            override fun visitIfExpression(expression: org.jetbrains.kotlin.psi.KtIfExpression) {
                                super.visitIfExpression(expression)
                                if (expression.then != null &&
                                    expression.then !is org.jetbrains.kotlin.psi.KtBlockExpression
                                ) {
                                    add(
                                        RequireBracesOnIfRule.Result(
                                            file.name,
                                            lineOf(ktFile, expression.node?.startOffset),
                                        ),
                                    )
                                }
                                if (expression.`else` != null &&
                                    expression.`else` !is org.jetbrains.kotlin.psi.KtBlockExpression &&
                                    expression.`else` !is org.jetbrains.kotlin.psi.KtIfExpression
                                ) {
                                    add(
                                        RequireBracesOnIfRule.Result(
                                            file.name,
                                            lineOf(ktFile, expression.node?.startOffset),
                                        ),
                                    )
                                }
                            }
                        },
                    )
                }
            }

            private fun findCompanionPositionViolations(file: Path): List<RequireCompanionObjectPositionRule.Result> {
                val ktFile = parse(file)
                return buildList {
                    ktFile.accept(
                        object : KtVisitorVoid() {
                            override fun visitClass(klass: org.jetbrains.kotlin.psi.KtClass) {
                                super.visitClass(klass)
                                val body = klass.getBody() ?: return
                                body.declarations.forEachIndexed { idx, decl ->
                                    if (decl is org.jetbrains.kotlin.psi.KtObjectDeclaration && decl.isCompanion()) {
                                        if (idx != 0) {
                                            add(
                                                RequireCompanionObjectPositionRule.Result(
                                                    file.name,
                                                    klass.name ?: "unknown",
                                                    lineOf(ktFile, decl.node?.startOffset),
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                        },
                    )
                }
            }

            private fun parse(file: Path) = KtPsiFactory(null, true).createFile("temp", file.readText())

            private fun lineOf(
                ktFile: org.jetbrains.kotlin.psi.KtFile,
                offset: Int?,
            ): Int = (ktFile.viewProvider.document?.getLineNumber(offset ?: 0) ?: 0) + 1

            private fun <T> tryOrEmpty(block: () -> List<T>): List<T> =
                try {
                    block()
                } catch (_: Exception) {
                    emptyList()
                }

            private fun <T> tryOrNull(block: () -> T): T? =
                try {
                    block()
                } catch (_: Exception) {
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
