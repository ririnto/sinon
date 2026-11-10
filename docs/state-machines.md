# State Machine Inventory

This reference inventories maintained first-party executable entrypoints and instruction workflows by their stateful behavior.
External reference examples and test scaffolding are not separate application state machines.
The linked tests cover selected behavior, not every possible filesystem race, process interleaving, or agent action.

## Runtime lifecycles

- The [baseline installer](../plugins/harness/scripts/configure.ts) checks eligibility, prepares files, previews conflicts, and creates missing files.
  The [baseline state reference](baseline-state.md) describes its outcomes, partial-write limitation, and property coverage.
- The [sample materializer](../plugins/harness/scripts/materialize-sample.ts) validates an empty regular target and source entries before copying the selected sample.
  Copy or filesystem failures can leave a partially populated target.
  Its [tests](../plugins/harness/tests/) cover the maintained sample and installer behavior.
- The [specification validator](../plugins/spec-driven-development/skills/spec-driven-development/scripts/sdd/commands/validate.ts) discovers documents, validates them, and aggregates results for each invocation.
  The [validation state reference](spec-validation-state.md) describes its outcomes and property coverage.
- The [Promtool runner](../plugins/observability-assets/tests/promtool/run.ts) downloads the release archive and checksum index concurrently, aborts and settles both on failure, verifies the archive, and runs the selected executable.
  It forwards interruption signals to the child and removes its temporary directory in a `finally` block after installation or execution.
  The [Promtool reference](../plugins/observability-assets/tests/promtool/README.md) describes supported hosts, commands, and integrity checks, and the [release tests](../plugins/observability-assets/tests/promtool/release.test.ts) cover selected failure and child-process cases.
- The [Java wrapper](../plugins/java/scripts/jdtls-wrapper.sh) optionally selects an override or project Lombok jar, adjusts Java options when a usable jar is selected, and launches JDTLS.
  The [Lombok detector](../plugins/java/scripts/has-lombok.sh) resolves project candidates, and the [script tests](../plugins/java/tests/scripts.test.ts) cover selection and launch behavior.

## One-shot flows and selectors

- The [version snapshot command](../.github/scripts/sync-versions.mjs) computes snapshots from root configuration, reports differences in check mode, or writes changed files in write mode.
  Concurrent writes are not a transaction, so a write failure can leave partial updates.
  The [version tests](../.github/scripts/sync-versions.test.ts) cover the command.
- The [pre-commit selector](../.github/scripts/pre-commit-scope.ts) maps staged paths to check names for one invocation.
  Its selection is stateless, and its [tests](../.github/scripts/pre-commit-scope.test.ts) cover path selection.
- Lint rules and parsers evaluate their inputs for an invocation and do not maintain an application lifecycle across invocations.

## Instruction workflows

- The [specification workflow](../plugins/spec-driven-development/skills/spec-driven-development/references/workflow.md) defines approval, review, implementation, and revision stages for people and agents.
  The [SPEC schema](../plugins/spec-driven-development/skills/spec-driven-development/assets/schemas/spec-frontmatter.schema.json) validates status membership, not whether a transition or approval occurred.
- The [workspace workflow](../plugins/workspace-workflow/README.md) provides Git instructions for worktrees, commits, rebases, merges, and change descriptions.
  Its [PR-first review reference](../plugins/workspace-workflow/references/pr-first-review.md) describes publication, review, and integration boundaries.
  These instructions describe decisions and actions by people or agents rather than an enforced runtime state machine.

## Verification boundaries

The existing state references and linked tests describe their own scope.
The [Promtool lifecycle models](../plugins/observability-assets/tests/promtool/model/README.md) check bounded download and child-process interleavings alongside the runtime tests.
A model describes its stated assumptions and transitions, and does not establish that arbitrary agent behavior follows an instruction workflow.
