plugins {
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

repositories {
    mavenCentral()
}

ktlint {
    version.set("1.8.0")
    android.set(false)
    ignoreFailures.set(false)
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
    }
}

tasks.named("ktlintCheck") {
    doLast {
        val hooksDir = layout.projectDirectory.dir(".git/hooks").asFile
        if (hooksDir.isDirectory) {
            listOf("pre-commit", "pre-push").forEach { hookName ->
                val source = layout.projectDirectory.file("docs/harness/git-hooks/$hookName").asFile
                if (source.isFile) {
                    val target = hooksDir.resolve(hookName)
                    source.copyTo(target, overwrite = true)
                    target.setExecutable(true)
                }
            }
        }
    }
}
