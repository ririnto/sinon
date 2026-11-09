# Gradle Multi-Module Sample

This standalone Kotlin project has an application and a library.
The application calls the library, and each module owns executable tests.
Running `./gradlew :check :buildSrc:check` checks both modules and the ruleset.
Versions live in the project's Gradle and pnpm catalogs.
Generate this sample with the materializer before running it.
The generated `buildSrc` supplies the custom ktlint rules to both modules and the root check.

Use JDK 25.
The application modules target Java 21 and the Kotlin ruleset builds with Java 25.
Run `./gradlew :check :buildSrc:check` to build, test, and lint the modules.
Run `./gradlew :app:run --args=Ada` to run the application.

Use Node 26 and pnpm 12 to install the optional Markdown checks and Git hooks:

```sh
pnpm install
pnpm run pre-commit
pnpm run pre-push
```

Both hooks run Markdown and the full root and buildSrc checks.
GitHub and GitLab configurations run the same Gradle checks.
