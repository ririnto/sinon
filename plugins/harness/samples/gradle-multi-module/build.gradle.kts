import org.jlleitschuh.gradle.ktlint.tasks.KtLintCheckTask

plugins {
    base
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        val projectCheck = tasks.named("check")
        rootProject.tasks.named("check") {
            dependsOn(projectCheck)
        }
    }
}

ktlint {
    version.set(libs.versions.ktlint.engine)
}

tasks.named<KtLintCheckTask>("runKtlintCheckOverMainSourceSet") {
    source(fileTree("buildSrc/src") { include("**/*.kt") })
}
