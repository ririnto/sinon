---
name: harness-install
description: >-
  Install the Harness implement and review skills into a target repository.
  Use when setting up Harness in a repository, installing the shared engineering
  rules, or refreshing an existing target installation from the current plugin.
---

# Harness Install

Install the current Harness implementation and review guidance into the target repository.
Materialize independent project skills under `.claude/skills/`.
Materialize selected language and tool guidance under the same target-owned documentation tree.
Do not install this installer skill into the target.

## Installed Layout

Create these paths relative to the target repository root:

```text
.claude/skills/
+-- implement/SKILL.md
+-- review/SKILL.md
+-- docs/
    +-- rules.md
    +-- languages/
    |   +-- selected language documents
    +-- tools/
        +-- selected tool documents
```

Install `implement` and `review` for every target.
The target installation always contains exactly these two skills.
Profile documentation is support material, not another installed skill.
Select one or more of the seven supported profiles independently for each detected or explicitly selected project root:
`bun`, `gradle`, `maven`, `uv`, `go`, `rust`, and `shell`.
Associate `bun` with JavaScript, TypeScript, and JSX guidance.
Associate `gradle` and `maven` with Java, and load Kotlin guidance only when the target has verified Kotlin integration.
Associate `uv` with Python, `go` with Go, `rust` with Rust, and `shell` with POSIX shell or Bash scripts.
Use explicit selection when automatic detection is ambiguous.
Inspect every independent root in a monorepo and repeat profile selection per root.
For polyglot roots, load every selected language document and every selected tool document once.
Keep profile associations explicit when one tool serves multiple languages.

The installed `implement` and `review` skills read `../docs/rules.md` from their own directories.
The common rules document references language and tool documents with paths relative to its own `.claude/skills/docs/` directory.
Copy the shared rules and all small referenced language and tool documents as ordinary target-owned files by default.
The implement and review procedures load only the language and tool documents applicable to the selected profile and changed files.
When filtering the common index to selected files is simpler, retain only those entries and copy every linked companion.
Otherwise retain the complete canonical index and copy all eight language documents and seven tool documents.
Never leave a relative reference to a file that was not copied.
Keep all installed guidance self-contained.
Do not add an installer manifest, cache, hash record, source checkout, symlink, agent, execution-plan file, autonomous skill, or compatibility alias.
Do not install native tooling files into `.claude/skills/`; place them at the existing target-owned configuration or CI paths described by each tool document.
When Kotlin is selected for a Gradle root, copy the complete `tooling/kotlin-ktlint/` module into the target-owned tooling path and apply the Gradle wiring described by `docs/tools/gradle.md`.
The Kotlin module is a normal Gradle project with source, resources, tests, and a local JAR output; do not copy only a prebuilt JAR.
When Kotlin is selected for Maven, apply only the verified conditional classpath path described by `docs/tools/maven.md`.
Do not install the Kotlin module when Kotlin is not selected.

## Source Mapping

Read the current canonical files from the installed Harness plugin before composing output.
Resolve the plugin root from `${CLAUDE_PLUGIN_ROOT}`.
When that variable is unavailable, use the absolute installed plugin path supplied by the host.
Do not read a source checkout from the target repository.

Use this source mapping:

| Target path | Canonical source |
| --- | --- |
| `.claude/skills/implement/SKILL.md` | `${CLAUDE_PLUGIN_ROOT}/skills/implement/SKILL.md` |
| `.claude/skills/review/SKILL.md` | `${CLAUDE_PLUGIN_ROOT}/skills/review/SKILL.md` |
| `.claude/skills/docs/rules.md` | `${CLAUDE_PLUGIN_ROOT}/docs/rules.md` |
| `.claude/skills/docs/languages/<selected>.md` | `${CLAUDE_PLUGIN_ROOT}/docs/languages/<selected>.md` |
| `.claude/skills/docs/tools/<selected>.md` | `${CLAUDE_PLUGIN_ROOT}/docs/tools/<selected>.md` |
| `<target-root>/.markdownlint-cli2.jsonc` | `${CLAUDE_PLUGIN_ROOT}/tooling/bun/.markdownlint-cli2.jsonc` when the Bun profile is selected |
| `<target-tooling>/kotlin-ktlint/` | `${CLAUDE_PLUGIN_ROOT}/tooling/kotlin-ktlint/` when Kotlin is selected |

Read the two skill files and common rules together with every selected language and tool file.
When Kotlin is selected, also read every file under the canonical `tooling/kotlin-ktlint/` module before composing its target copy and native build wiring.
Preserve their content and remap only relative references that must resolve from the installed locations.
In both installed skill files, resolve the canonical `docs/rules.md` reference as `../docs/rules.md`.
In the installed common rules, keep language references as `languages/<name>.md` and tool references as `tools/<name>.md`.
In installed language and tool files, resolve the common rules reference as `../rules.md`.
Resolve language-to-tool references from `languages/` as `../tools/<name>.md`, and tool-to-language references from `tools/` as `../languages/<name>.md` in the installed `docs/` directory.
Do not point installed files back to `${CLAUDE_PLUGIN_ROOT}` or any other source path.
Do not copy unselected language or tool documents merely to satisfy a link; select all linked companions for each active profile.

## Preconditions

Obtain the target repository path before changing files.
Resolve it to an existing directory and inspect its root instruction files, `.claude/` tree, and Git status.
Read the target's applicable `AGENTS.md`, `CLAUDE.md`, or host-specific instruction file before editing.
Preserve all unrelated target changes.

Treat `.claude/skills/implement/`, `.claude/skills/review/`, and `.claude/skills/docs/` as Harness-owned paths only after inspecting their existing contents.
Preserve any other skill directory and every unrelated file.
Do not use a dirty-tree check as a reason to reject installation.
A dirty target is allowed when the requested installation is safe and its changes remain isolated.

## Preview And Conflict Handling

Read each destination before writing it.
Show a concrete preview with one status for every guidance and native path selected for this run:

- `create`: destination does not exist.
- `keep`: destination bytes already match the composed current input.
- `conflict`: destination exists with different bytes.
- `replace`: an explicit grant authorizes replacing a conflicting Harness-owned file.

Do not overwrite a `conflict` during the first pass.
Report the destination, whether it is Harness-owned, and a diff or concise byte-level change summary.
Ask for explicit approval before replacing differing content.
Treat a conflicting file under the mapped Harness paths as a clear boundary; do not infer approval from a dirty worktree or from a prior installation.

Create missing parent directories only for the mapped destinations.
Protect symlink ancestors and verify that every destination remains inside the target repository.
Reject a file-versus-directory mismatch before writing.
Write only after the preview is reviewed and any required replacement grant is explicit.
Preserve file mode when an existing mapped file is replaced.
Use ordinary files, not links.
Do not traverse or modify paths outside the target root.

## Installation

Compose each destination from the current canonical input set on every run.
Keep matching files byte-identical.
Create missing files.
Replace only explicitly approved conflicting Harness-owned files.
Leave unapproved conflicts unchanged and report them as incomplete installation paths.
Never remove target files that are absent from the mapped layout.
Never overwrite custom skills outside the mapped paths.

Apply profile-specific native changes only after reading the target manifest, build files, editor configuration, existing CI, and relevant project identity.
For the Bun profile, merge the canonical `.markdownlint-cli2.jsonc` at the target project root as described by `docs/tools/bun.md`: preserve existing target values on conflict, create the file only when no target-owned configuration exists, and add the `markdownlint-cli2` dependency and `check:markdownlint-cli2` and `fix:markdownlint-cli2` scripts through the manifest merge.
The canonical config owns a `node_modules` ignore in addition to the target `.gitignore`, so a fresh target does not require a pre-existing `.gitignore` to avoid vendor Markdown.
For a selected Kotlin Gradle root, read its complete existing Gradle build and source layout, then add the local module and explicit `ktlintRuleset(...)` dependency without changing project identity or source roots.
Wire the consumer lint task to the module `jar` task through the native Gradle graph or composite build when the target supports that coupling.
Merge missing settings into existing configuration when the tool reference permits merging.
Preserve target values when a conflict exists.
Do not replace package manifests, Gradle or Maven project identity, source roots, toolchains, test frameworks, existing formatters, existing checkers, hooks, or unrelated CI jobs.
Create a native configuration file only when the tool reference permits creation and no target-owned file conflicts.
Create GitHub or GitLab catalog files only when explicitly selected, and adapt working directories, versions, stages, and existing test ownership to the target.
Use only the catalog's least-privilege read permissions.
Omit a test task when the target has no test owner, and report it as not applicable.

Update installed guidance from the same input set on every refresh.
Do not maintain a second target-specific implementation.
Do not add compatibility aliases for older paths.

## Verification

After writing, read every installed destination through the native filesystem.
Verify that:

1. All mapped files exist as regular files.
2. Installed skill frontmatter names are `implement` and `review`.
3. Each installed skill points to `../docs/rules.md`.
4. The installed common rules point only to selected `languages/<name>.md` and `tools/<name>.md` files.
5. Installed language and tool files point to `../rules.md`, and selected language-to-tool links resolve within the target tree.
6. A selected Kotlin installation contains the complete ruleset module source, resources, build files, and tests.
7. A selected Kotlin Gradle build attaches the module JAR through `ktlintRuleset(...)` and its lint task runs after the module `jar` task.
8. No installed file contains `${CLAUDE_PLUGIN_ROOT}`, a source checkout path, or a symlink-only dependency.
9. A selected Bun installation resolves its `.markdownlint-cli2.jsonc` at the target project root, and a second run with unchanged canonical input produces only `keep` statuses.
10. Existing custom skill files and unrelated native configuration remain unchanged.

Run the target repository's documented native check when the installation changes a checkable project surface.
Do not invent a shared command or run a tool bundle setup.
Report a check that cannot run as a named gap.

## Completion Report

Report the target path and the canonical source root used.
List every selected guidance and native path with its status: `create`, `keep`, `conflict`, or `replace`.
List each selected profile, project root, language association, and CI catalog.
List preserved custom skills, manifests, native configuration, tests, hooks, and unrelated files when they were present.
Report the filesystem verification result, the idempotent second-run result, and every target-native check command with its numeric exit code or named gap.
State when a target has no test owner and mark that task not applicable.
Keep the report factual and distinguish source inspection, filesystem verification, native integration behavior, and hosted CI gaps.
