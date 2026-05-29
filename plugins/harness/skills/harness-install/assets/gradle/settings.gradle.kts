rootProject.name = "harness-target"

plugins {
    id("org.danilopianini.gradle-pre-commit-git-hooks") version "2.1.17" apply false
}

if (providers.gradleProperty("harness.gitHooks").orNull == "true") {
    apply(plugin = "org.danilopianini.gradle-pre-commit-git-hooks")

    gitHooks {
        preCommit {
            from(file("docs/harness/git-hooks/pre-commit"))
        }
        hook("pre-push") {
            from(file("docs/harness/git-hooks/pre-push"))
        }
        if (providers.gradleProperty("harness.gitHooks.overwrite").orNull == "true") {
            createHooks(true)
        } else {
            createHooks()
        }
    }
}
