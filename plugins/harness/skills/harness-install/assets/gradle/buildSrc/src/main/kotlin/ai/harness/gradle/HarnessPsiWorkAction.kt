package ai.harness.gradle

import kotlinx.serialization.json.Json
import org.gradle.workers.WorkAction
import java.nio.file.Path

/**
 * Worker action that performs PSI-based analysis of Kotlin source files.
 * This action runs inside an isolated classloader containing kotlin-compiler-embeddable.
 */
abstract class HarnessPsiWorkAction : WorkAction<HarnessPsiWorkParameters> {
    /**
     * Execute PSI analysis on all source files and write results as JSON.
     */
    override fun execute() {
        val sourceFiles = parameters.sourceFiles.get()
        val outputJsonFile = parameters.outputJson.asFile.get()

        val greaterThanComparisons = buildList {
            sourceFiles.forEach { file ->
                try {
                    val hits = findGreaterThanComparisons(file.toPath())
                    addAll(hits)
                } catch (_: Exception) {
                }
            }
        }

        val blankLinesInLeafFunctions = buildList {
            sourceFiles.forEach { file ->
                try {
                    val hits = findBlankLinesInLeafFunctions(file.toPath())
                    addAll(hits)
                } catch (_: Exception) {
                }
            }
        }

        val implicitLambdaIt = buildList {
            sourceFiles.forEach { file ->
                try {
                    val hits = findImplicitLambdaIt(file.toPath())
                    addAll(hits)
                } catch (_: Exception) {
                }
            }
        }

        val topLevelDeclarations = buildList {
            sourceFiles.forEach { file ->
                try {
                    val info = inspectTopLevelDeclarations(file.toPath())
                    add(info)
                } catch (_: Exception) {
                }
            }
        }

        val results = HarnessPsiResults(
            greaterThanComparisons = greaterThanComparisons,
            blankLinesInLeafFunctions = blankLinesInLeafFunctions,
            implicitLambdaIt = implicitLambdaIt,
            topLevelDeclarations = topLevelDeclarations,
        )

        outputJsonFile.parentFile.mkdirs()
        outputJsonFile.writeText(Json.encodeToString(HarnessPsiResults.serializer(), results))
    }

    private fun parseFile(path: Path): Any {
        val psiFileFactoryName = "org.jetbrains.kotlin.com.intellij.psi.PsiFileFactory"
        val ktLanguageName = "org.jetbrains.kotlin.idea.KotlinLanguage"
        val disposerName = "org.jetbrains.kotlin.com.intellij.openapi.util.Disposer"
        val kceName = "org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment"
        val compilerConfigName = "org.jetbrains.kotlin.config.CompilerConfiguration"
        val envConfigFilesName = "org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles"
        val commonConfigKeysName = "org.jetbrains.kotlin.config.CommonConfigurationKeys"
        val messageRendererName = "org.jetbrains.kotlin.cli.common.messages.MessageRenderer"
        val messageCollectorName = "org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector"

        val disposerClass = Class.forName(disposerName)
        val kceClass = Class.forName(kceName)
        val compilerConfigClass = Class.forName(compilerConfigName)
        val envConfigFilesClass = Class.forName(envConfigFilesName)
        val commonConfigKeysClass = Class.forName(commonConfigKeysName)
        val messageRendererClass = Class.forName(messageRendererName)
        val messageCollectorClass = Class.forName(messageCollectorName)

        val newDisposableMethod = disposerClass.getMethod("newDisposable")
        val disposable = newDisposableMethod.invoke(null)

        val jvmConfigFilesField = envConfigFilesClass.getField("JVM_CONFIG_FILES")
        val jvmConfigFiles = jvmConfigFilesField.get(null)

        val compilerConfigConstructor = compilerConfigClass.getConstructor()
        val config = compilerConfigConstructor.newInstance()

        val messageCollectorKeyField = commonConfigKeysClass.getField("MESSAGE_COLLECTOR_KEY")
        val messageCollectorKey = messageCollectorKeyField.get(null)
        val plainField = messageRendererClass.getField("PLAIN_RELATIVE_PATHS")
        val plainRenderer = plainField.get(null)
        val messageCollectorConstructor = messageCollectorClass.getConstructor(java.io.PrintStream::class.java, messageRendererClass, Boolean::class.java)
        val messageCollector = messageCollectorConstructor.newInstance(System.err, plainRenderer, false)

        val putMethod = compilerConfigClass.getMethod("put", Any::class.java, Any::class.java)
        putMethod.invoke(config, messageCollectorKey, messageCollector)

        val createForProductionMethod = kceClass.getMethod(
            "createForProduction",
            disposerClass,
            compilerConfigClass,
            envConfigFilesClass
        )
        val env = createForProductionMethod.invoke(null, disposable, config, jvmConfigFiles)

        val psiFileFactoryClass = Class.forName(psiFileFactoryName)
        val ktLanguageClass = Class.forName(ktLanguageName)

        val envClass = env.javaClass
        val projectGetter = envClass.getMethod("getProject")
        val project = projectGetter.invoke(env)

        val getInstance = psiFileFactoryClass.getMethod("getInstance", project.javaClass)
        val psiFileFactory = getInstance.invoke(null, project)

        val instanceField = ktLanguageClass.getField("INSTANCE")
        val ktLanguageInstance = instanceField.get(null)

        val createFileMethod = psiFileFactoryClass.getMethod(
            "createFileFromText",
            String::class.java,
            ktLanguageClass,
            CharSequence::class.java
        )
        val text = java.nio.file.Files.readString(path)
        val fileName = path.fileName.toString()

        return createFileMethod.invoke(psiFileFactory, fileName, ktLanguageInstance, text)
            ?: error("createFileFromText returned null")
    }

    private fun findGreaterThanComparisons(file: Path): List<GreaterThanComparisonResult> {
        val ktFile = parseFile(file)
        val ktBinaryExpressionName = "org.jetbrains.kotlin.psi.KtBinaryExpression"
        val ktTokensName = "org.jetbrains.kotlin.lexer.KtTokens"
        val ktVisitorName = "org.jetbrains.kotlin.psi.KtVisitor"

        val ktBinaryExpressionClass = Class.forName(ktBinaryExpressionName)
        val ktTokensClass = Class.forName(ktTokensName)
        val ktVisitorClass = Class.forName(ktVisitorName)

        val gtField = ktTokensClass.getField("GT")
        val gteqField = ktTokensClass.getField("GTEQ")
        val gtToken = gtField.get(null)
        val gteqToken = gteqField.get(null)

        val binExprs = buildList {
            val exprs = mutableListOf<Any>()

            val visitorInstance = java.lang.reflect.Proxy.newProxyInstance(
                ktVisitorClass.classLoader,
                arrayOf(ktVisitorClass)
            ) { _, method, args ->
                if (method.name == "visitBinaryExpression" && args != null && args.size >= 2) {
                    exprs.add(args[0])
                }
                null
            }

            val acceptMethod = ktFile::class.java.getMethod("accept", ktVisitorClass, Any::class.java)
            acceptMethod.invoke(ktFile, visitorInstance, null)

            addAll(exprs)
        }

        val fileName = file.fileName.toString()
        return buildList {
            binExprs.forEach { binExpr ->
                val operationTokenField = ktBinaryExpressionClass.getMethod("getOperationToken")
                val operationToken = operationTokenField.invoke(binExpr)

                if (operationToken === gtToken || operationToken === gteqToken) {
                    val nodeField = ktBinaryExpressionClass.getMethod("getNode")
                    val node = nodeField.invoke(binExpr)
                    val startOffsetMethod = node?.javaClass?.getMethod("getStartOffset")
                    val startOffset = startOffsetMethod?.invoke(node) as? Int ?: 0

                    val viewProviderMethod = ktFile::class.java.getMethod("getViewProvider")
                    val viewProvider = viewProviderMethod.invoke(ktFile)
                    val documentMethod = viewProvider::class.java.getMethod("getDocument")
                    val document = documentMethod.invoke(viewProvider)
                    val getLineNumberMethod = document?.javaClass?.getMethod("getLineNumber", Int::class.java)
                    val lineNumber = ((getLineNumberMethod?.invoke(document, startOffset) as? Int) ?: 0).plus(1)

                    add(GreaterThanComparisonResult(fileName, lineNumber))
                }
            }
        }
    }

    private fun findBlankLinesInLeafFunctions(file: Path): List<BlankLineInFunctionResult> {
        val ktFile = parseFile(file)
        val ktNamedFunctionName = "org.jetbrains.kotlin.psi.KtNamedFunction"
        val ktLambdaExpressionName = "org.jetbrains.kotlin.psi.KtLambdaExpression"
        val ktVisitorName = "org.jetbrains.kotlin.psi.KtVisitor"
        val psiWhiteSpaceName = "org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace"

        val ktNamedFunctionClass = Class.forName(ktNamedFunctionName)
        val ktLambdaExpressionClass = Class.forName(ktLambdaExpressionName)
        val ktVisitorClass = Class.forName(ktVisitorName)
        val psiWhiteSpaceClass = Class.forName(psiWhiteSpaceName)

        val functions = buildList {
            val funcs = mutableListOf<Any>()

            val visitorInstance = java.lang.reflect.Proxy.newProxyInstance(
                ktVisitorClass.classLoader,
                arrayOf(ktVisitorClass)
            ) { _, method, args ->
                if (method.name == "visitNamedFunction" && args != null && args.size >= 2) {
                    funcs.add(args[0])
                }
                null
            }

            val acceptMethod = ktFile::class.java.getMethod("accept", ktVisitorClass, Any::class.java)
            acceptMethod.invoke(ktFile, visitorInstance, null)

            addAll(funcs)
        }

        val fileName = file.fileName.toString()
        return buildList {
            functions.forEach { func ->
                val hasNestedFuncMethod = ktNamedFunctionClass.getMethod("findDescendantOfType", Class::class.java)
                val hasNestedFunc = hasNestedFuncMethod.invoke(func, ktNamedFunctionClass) != null

                val hasNestedLambdaMethod = ktNamedFunctionClass.getMethod("findDescendantOfType", Class::class.java)
                val hasNestedLambda = hasNestedLambdaMethod.invoke(func, ktLambdaExpressionClass) != null

                if (!hasNestedFunc && !hasNestedLambda) {
                    val bodyExpressionMethod = ktNamedFunctionClass.getMethod("getBodyExpression")
                    val bodyExpression = bodyExpressionMethod.invoke(func) ?: return@forEach

                    val firstChildMethod = bodyExpression.javaClass.getMethod("getFirstChild")
                    var currentChild = firstChildMethod.invoke(bodyExpression)

                    while (currentChild != null) {
                        if (psiWhiteSpaceClass.isInstance(currentChild)) {
                            val textMethod = currentChild.javaClass.getMethod("getText")
                            val text = textMethod.invoke(currentChild) as String
                            if (text.count { char -> char == '\n' } >= 2) {
                                val nodeField = currentChild.javaClass.getMethod("getNode")
                                val node = nodeField.invoke(currentChild)
                                val startOffsetMethod = node?.javaClass?.getMethod("getStartOffset")
                                val startOffset = startOffsetMethod?.invoke(node) as? Int ?: 0

                                val viewProviderMethod = ktFile::class.java.getMethod("getViewProvider")
                                val viewProvider = viewProviderMethod.invoke(ktFile)
                                val documentMethod = viewProvider::class.java.getMethod("getDocument")
                                val document = documentMethod.invoke(viewProvider)
                                val getLineNumberMethod = document?.javaClass?.getMethod("getLineNumber", Int::class.java)
                                val lineNumber = ((getLineNumberMethod?.invoke(document, startOffset) as? Int) ?: 0).plus(1)

                                val funcNameMethod = ktNamedFunctionClass.getMethod("getName")
                                val funcName = funcNameMethod.invoke(func) as? String ?: "unknown"

                                add(BlankLineInFunctionResult(fileName, funcName, lineNumber))
                            }
                        }

                        val nextSiblingMethod = currentChild.javaClass.getMethod("getNextSibling")
                        currentChild = nextSiblingMethod.invoke(currentChild)
                    }
                }
            }
        }
    }

    private fun findImplicitLambdaIt(file: Path): List<ImplicitLambdaItResult> {
        val ktFile = parseFile(file)
        val ktLambdaExpressionName = "org.jetbrains.kotlin.psi.KtLambdaExpression"
        val ktSimpleNameExpressionName = "org.jetbrains.kotlin.psi.KtSimpleNameExpression"
        val ktVisitorName = "org.jetbrains.kotlin.psi.KtVisitor"

        val ktLambdaExpressionClass = Class.forName(ktLambdaExpressionName)
        val ktSimpleNameExpressionClass = Class.forName(ktSimpleNameExpressionName)
        val ktVisitorClass = Class.forName(ktVisitorName)

        val lambdas = buildList {
            val lams = mutableListOf<Any>()

            val visitorInstance = java.lang.reflect.Proxy.newProxyInstance(
                ktVisitorClass.classLoader,
                arrayOf(ktVisitorClass)
            ) { _, method, args ->
                if (method.name == "visitLambdaExpression" && args != null && args.size >= 2) {
                    lams.add(args[0])
                }
                null
            }

            val acceptMethod = ktFile::class.java.getMethod("accept", ktVisitorClass, Any::class.java)
            acceptMethod.invoke(ktFile, visitorInstance, null)

            addAll(lams)
        }

        val fileName = file.fileName.toString()
        return buildList {
            lambdas.forEach { lambda ->
                val valueParametersMethod = ktLambdaExpressionClass.getMethod("getValueParameters")
                val valueParameters = valueParametersMethod.invoke(lambda) as? List<*> ?: emptyList<Any>()

                if (valueParameters.isEmpty()) {
                    var hasIt = false
                    try {
                        val visitorForDesc = java.lang.reflect.Proxy.newProxyInstance(
                            ktVisitorClass.classLoader,
                            arrayOf(ktVisitorClass)
                        ) { _, method, args ->
                            if (method.name == "visitSimpleNameExpression" && args != null && args.size >= 2) {
                                val expr = args[0]
                                val textMethod = expr.javaClass.getMethod("getText")
                                val exprText = textMethod.invoke(expr) as String
                                if (exprText == "it") {
                                    hasIt = true
                                }
                            }
                            null
                        }

                        val lambdaAcceptMethod = lambda.javaClass.getMethod("accept", ktVisitorClass, Any::class.java)
                        lambdaAcceptMethod.invoke(lambda, visitorForDesc, null)
                    } catch (_: Exception) {
                    }

                    if (hasIt) {
                        val nodeField = lambda.javaClass.getMethod("getNode")
                        val node = nodeField.invoke(lambda)
                        val startOffsetMethod = node?.javaClass?.getMethod("getStartOffset")
                        val startOffset = startOffsetMethod?.invoke(node) as? Int ?: 0

                        val viewProviderMethod = ktFile::class.java.getMethod("getViewProvider")
                        val viewProvider = viewProviderMethod.invoke(ktFile)
                        val documentMethod = viewProvider::class.java.getMethod("getDocument")
                        val document = documentMethod.invoke(viewProvider)
                        val getLineNumberMethod = document?.javaClass?.getMethod("getLineNumber", Int::class.java)
                        val lineNumber = ((getLineNumberMethod?.invoke(document, startOffset) as? Int) ?: 0).plus(1)

                        add(ImplicitLambdaItResult(fileName, lineNumber))
                    }
                }
            }
        }
    }

    private fun inspectTopLevelDeclarations(file: Path): TopLevelDeclarationResult {
        val ktFile = parseFile(file)
        val declarationsMethod = ktFile::class.java.getMethod("getDeclarations")
        val declarations = declarationsMethod.invoke(ktFile) as? List<*> ?: emptyList<Any>()

        val firstKind = if (declarations.isNotEmpty()) {
            val firstDecl = declarations.first()
            when (firstDecl?.javaClass?.simpleName) {
                "KtClass" -> "class"
                "KtObjectDeclaration" -> "object"
                "KtTypeAlias" -> "typealias"
                else -> "unknown"
            }
        } else {
            "unknown"
        }

        return TopLevelDeclarationResult(file.fileName.toString(), declarations.size, firstKind)
    }
}
