# Repository Guidelines

## Project Structure

`ARCHITECTURE.md` owns system boundaries, dependency direction, data flow, and verification boundaries.
The Gradle build and `buildSrc` own ktlint rules and their tests.

## Build, Test, and Development Commands

Run `./gradlew ktlintCheck` for lint, `./gradlew ktlintFormat` for approved formatting, and `./gradlew check` for the full gate.
The Gradle build owns optional Markdownlint behavior and must keep missing tooling visible.
The `org.danilopianini.gradle-pre-commit-git-hooks` settings plugin is the sole Git hook mechanism.
The settings plugin creates hooks during local Gradle configuration when the `CI` environment variable is absent.
Local Gradle configuration may run only when its documented hook-creation side effect is approved.
Hooks delegate to these Gradle tasks and define no second policy.

## Coding Style and Testing

Use structured logging and preserve configured public declaration documentation.

## Security and Configuration

Do not add parallel policy files for Gradle, ktlint, hook, or Markdownlint configuration.
Review hook tasks and Gradle execution for filesystem, network, credential, and publication effects before changing them.
