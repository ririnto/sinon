import org.gradle.kotlin.dsl.withGroovyBuilder

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        id("org.danilopianini.gradle-pre-commit-git-hooks") version "2.1.17"
    }
}

rootProject.name = "harness-target"

val useGitHooks = providers.gradleProperty("harness.gitHooks").map(String::toBoolean).getOrElse(false)

if (useGitHooks) {
    plugins.apply("org.danilopianini.gradle-pre-commit-git-hooks")

    extensions.getByName("gitHooks").withGroovyBuilder {
        "preCommit" {
            "from"(file("docs/harness/git-hooks/pre-commit"))
        }

        "hook"("pre-push") {
            "from"(file("docs/harness/git-hooks/pre-push"))
        }

        if (providers.gradleProperty("harness.gitHooks.overwrite").map(String::toBoolean).getOrElse(false)) {
            "createHooks"(true)
        } else {
            "createHooks"()
        }
    }
}
