package ai.harness.gradle

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.workers.WorkParameters
import java.io.File

/**
 * Parameters passed to the PSI analysis worker.
 */
interface HarnessPsiWorkParameters : WorkParameters {
    /**
     * Kotlin source files to analyze.
     */
    val sourceFiles: ListProperty<File>

    /**
     * Output file path for serialized PSI analysis results as JSON.
     */
    val outputJson: RegularFileProperty
}
