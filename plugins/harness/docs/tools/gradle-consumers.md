# Gradle consumer comparison

This comparison uses checked-in source from the named revisions.
The [Gradle profile](gradle.md) defines the shared standard.

## Project selection

The Harness installer routes Java and eligible Kotlin projects to the [Gradle profile](gradle.md).
Its bundled [multi-module sample](../../samples/gradle-multi-module/README.md) supplies the baseline.
Regiscope supplied a Gradle quality audit for this comparison.
File Exchange has a local Gradle reference and matching ruleset, service registration, and check wiring.
The File Exchange source establishes the shared pattern, while the installation history is unverified.
Workgraph's `THIRD_PARTY_NOTICES.md` credits Sinon's compatibility guidance and Gradle ktlint setup for its bundled consumer asset.

The search covered primary clones available under the repository owner's local checkout collection.
It matched profile references, Gradle build roots, ktlint providers, and attribution.
The MCP server bundle uses Gradle for build and release packaging without matching lint wiring or profile attribution.
The Gradle examples in the docs repository yielded no verified link to this profile.
Coverage of external, unavailable, and undocumented consumers remains unverified.

## Observed source

| Project and revision | Lint and format | Check and consumers | Source paths |
| --- | --- | --- | --- |
| Sinon sample at `d66aeae0196a251fa49ef5da88566c7c7516356c` | ktlint applies at the root, in both modules, and in buildSrc. Root lint includes `buildSrc/src/**/*.kt`. The plugin supplies `ktlintFormat`. | Root `:check` depends on both module checks. Both sample hooks call the pnpm scripts that run `:check :buildSrc:check`. | Under `plugins/harness/samples/gradle-multi-module/`: `settings.gradle.kts`, root, app, library, and buildSrc `build.gradle.kts` files, `package.json`, and `.husky/`. The generic CI catalogs are under `plugins/harness/ci/`. |
| File Exchange at `7ddad96fc0f2028c140fb81d3670cb815e79186e` | Kotlin subprojects use ktlint and take the ruleset from the Gradle-loaded buildSrc artifact. buildSrc runs its own ktlint without declaring that artifact as a ruleset. The plugin supplies `ktlintFormat`. | Root `:check` aggregates subproject checks, which include `integrationTest`. Pre-commit runs static checks and `ktlintCheck`. Pre-push and manually dispatched CI run `pnpm run check`, which includes `./gradlew check :buildSrc:check`. | `.claude/skills/docs/tools/gradle.md`, `settings.gradle.kts`, `build.gradle.kts`, `buildSrc/build.gradle.kts`, `buildSrc/AGENTS.md`, `RepositoryRuleSetArtifact.kt` and `RepositoryRuleSetProvider.kt` under `buildSrc/src/main/kotlin/com/ririnto/fileexchange/ktlint/`, the buildSrc provider descriptor, `.editorconfig`, `package.json`, `.husky/`, and `.github/workflows/check.yaml`. |
| Workgraph at `ba1eb51392bc35590ccd164863a1b19cf33e880a` | The bundled Gradle sample attaches the JAR containing the loaded provider. Its fixture runs read-only ktlint against passing and failing inputs. The sample plugin supplies `ktlintFormat`. | The pnpm Kotlin check runs buildSrc lint and the fixture's root `ktlintCheck`. Scoped pre-commit selects this lane for affected Kotlin assets. Pre-push and CI reach it through `pnpm run check`. The Kotlin lane does not invoke a Gradle lifecycle check or a buildSrc unit-test task. | `THIRD_PARTY_NOTICES.md`, `skills/development/references/kotlin.md`, `skills/development/assets/gradle/` (root and buildSrc builds, catalog, wrapper, EditorConfig, and provider descriptor), `scripts/check-ktlint-consumer.ts`, `scripts/pre-commit-scope.ts`, `package.json`, `.husky/`, and `.github/workflows/check.yaml`. |
| Regiscope at `e51fde685b0a2e21e7e46e121236a54160cdfc9a` | All projects attach `buildSrc/build/libs/buildSrc.jar`. buildSrc also attaches its own output and adds a JAR dependency to its ktlint worker tasks. The plugin supplies `ktlintFormat`. | The root build registers no aggregate `:check`. Pre-commit runs pnpm checks and repository-wide Kotlin lint. Pre-push adds `check :buildSrc:check` and the application acceptance command. Manually dispatched CI runs buildSrc tests and lint, application acceptance, and a separate PostgreSQL job. | `settings.gradle.kts`, root and buildSrc `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`, `buildSrc/src/main/kotlin/com/regiscope/ktlint/RuleSetProvider.kt`, the buildSrc provider descriptor, root and module `.editorconfig`, `package.json`, `.husky/`, `.github/workflows/verification.yaml`, `apps/server/build.gradle.kts`, and `modules/sdk/build.gradle.kts`. |

All three external builds pin ktlint Gradle plugin `14.2.0` and engine `1.8.0`.
Their checked hook and workflow paths invoke read-only Gradle tasks.
The Regiscope audit records earlier dry runs and a buildSrc test and lint run.
Those results belong to the audit's configuration.
The comparison establishes source wiring at the revisions above.

## Regiscope follow-ups

| Observed difference | Follow-up in Regiscope |
| --- | --- |
| The root and subprojects consume a fixed build output path. File Exchange and Workgraph resolve the loaded artifact. | In `build.gradle.kts`, verify provider loading on a clean checkout and on configuration-cache reuse if enabled. Use a Gradle-managed artifact if the fixed path fails either supported case. |
| buildSrc lints itself with its own JAR. Its worker tasks depend on `jar` through a task-name match. | In `buildSrc/build.gradle.kts`, prove the producer dependency with a clean `:buildSrc:check` and a disposable custom-rule violation. Preserve self-lint if it works without a cycle. |
| Regiscope applies ktlint at the app root and in every project. File Exchange applies it to Kotlin subprojects, and Workgraph checks a standalone fixture. | In `build.gradle.kts`, verify root scripts, module sources and tests, and buildSrc coverage with scoped probes. Keep failure reporting and each intentional source scope. |
| Regiscope's provider registers its own `code` rules, including assertion and prose rules. Its module EditorConfig files enable KDoc and spacing modes by source scope, and root ktlint enables trailing commas despite the IDE preferences. | In `buildSrc` and the EditorConfig files, verify allowed and violating fixtures for the local rules and effective editor modes. Preserve security and assertion rules. Require reviewed formatter output before adopting any correction. |
| Unqualified `check` selects module checks, including SDK schema drift. The sample and File Exchange expose an aggregate root task. | In the root build, verify the current check graph and keep schema drift reachable. If callers adopt `:check`, add explicit dependencies on the checked modules and prove equivalent coverage. |
| Server MVC, network, and PostgreSQL tests sit outside ordinary server `test` and `check`. File Exchange includes its integration source sets in module checks. | In `apps/server/build.gradle.kts` and the workflow, verify each explicit integration entry point and preserve the restricted database job. Document which acceptance command owns each test group. |
| Pre-push runs the lifecycle and application acceptance commands separately. CI uses the acceptance command and separate buildSrc and PostgreSQL commands. | In `package.json` and `.github/workflows/verification.yaml`, map each required check to a maintained caller. Check whether CI needs an explicit SDK drift entry point before composing or deduplicating commands. |
| The hooks set a worktree-local Gradle user home and carry input for the staged or outgoing policy checks. | In `.husky/`, exercise both hooks through Git when changing their caller commands. Preserve the Gradle isolation and the staged and outgoing inputs. |
| Regiscope and File Exchange use Gradle `9.9.2`, and the Workgraph sample uses `9.9.1`. These pins exceed Kotlin `2.4.21`'s fully supported Gradle range. Regiscope's wrapper properties lack a distribution checksum. | In `gradle/wrapper/gradle-wrapper.properties` and the catalog, check the supported compatibility range and wrapper integrity before retaining or changing the pins. Record the verified pair. |
| The audit lists Gradle `9.8.2`, 23 rules, and a separate buildSrc pre-push script. The inspected source has Gradle `9.9.2`, 25 rules, and `:buildSrc:check` in `gradle:check`. | Refresh `docs/design-docs/gradle-quality-evidence.md` after rerunning the current commands. Record formatter execution and hosted results only when observed. |
