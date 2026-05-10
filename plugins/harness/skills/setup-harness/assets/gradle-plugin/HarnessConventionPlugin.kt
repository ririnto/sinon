import org.gradle.api.Plugin
import org.gradle.api.Project

/** Registers the repository-local harness validator as a Gradle verification task. */
class HarnessConventionPlugin : Plugin<Project> {
    /** Applies the harnessCheck task to the target project. */
    override fun apply(project: Project) {
        project.tasks.register("harnessCheck", HarnessCheckTask::class.java) {
            group = "verification"
            description = "Run Gradle-native harness validation."
            configFile.set(project.layout.projectDirectory.file("docs/harness/config.json"))
        }
    }
}
