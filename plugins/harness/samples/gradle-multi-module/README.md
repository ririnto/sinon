# Gradle Multi-Module Sample

This standalone Kotlin project has an application and a library.
The application calls the library, and each module owns executable tests.
The root `check` and `ktlintCheck` tasks include both modules.
Versions live in the project's Gradle and pnpm catalogs.
Generate this sample with the materializer before running it.
The generated `tooling/kotlin` build supplies the custom ktlint rules to both modules.

Use JDK 25; the application modules target Java 21 and the included Kotlin ruleset builds with Java 25.
Run `./gradlew check ktlintCheck` to build, test, and lint the modules.
Run `./gradlew :app:run --args=Ada` to run the application.

Use Node 26 and pnpm 12 to install the optional Markdown checks and Git hooks:

```sh
pnpm install
pnpm run pre-commit
pnpm run pre-push
```

The pre-commit hook runs Markdown and Kotlin lint checks.
The pre-push hook runs Markdown and the root Gradle check.
GitHub and GitLab configurations run the native Gradle check.
