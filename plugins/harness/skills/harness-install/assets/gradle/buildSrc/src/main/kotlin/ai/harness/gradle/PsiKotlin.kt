package ai.harness.gradle

import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiFileFactory
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtFile
import kotlin.io.path.readText
import java.nio.file.Path

internal object PsiKotlin {
	private val disposable = Disposer.newDisposable()
	private val environment = KotlinCoreEnvironment.createForProduction(
		disposable,
		CompilerConfiguration(),
		EnvironmentConfigFiles.JVM_CONFIG_FILES,
	)
	private val psiFileFactory = PsiFileFactory.getInstance(environment.project)

	fun parse(filePath: Path): KtFile {
		val text = filePath.readText()
		return psiFileFactory.createFileFromText(filePath.fileName.toString(), KotlinLanguage.INSTANCE, text) as KtFile
	}
}
