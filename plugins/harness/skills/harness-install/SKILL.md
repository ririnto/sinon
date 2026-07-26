---
name: harness-install
description: >-
  Compose complete Harness resource bundles into a target repository.
  Use when setting up common repository resources, one tool, and optional GitHub or GitLab configuration.
---

# Harness Install

Compose packaged resource bundles directly into a target repository.
Do not run a setup program and do not select individual files inside a chosen bundle.

## Required Choices

Obtain these inputs before changing the target:

1. The target repository path.
2. Exactly one tool: `bun`, `gradle`, `maven`, `shell`, `uv`, `go`, or `rust`.
3. Environments: `github`, `gitlab`, both, or neither.

Resolve the resource root from the installed plugin:

```text
${CLAUDE_PLUGIN_ROOT}/skills/harness-install/assets
```

## Safety Check

Inspect the target worktree before copying.
If it is dirty, stop unless the user explicitly accepts mixing these changes with existing work.
Do not overwrite credentials, local configuration, caches, vendored files, or differing target content without explicit approval.

## Bundle Composition

Copy complete bundle trees in this order while preserving relative paths, dotfiles, and executable modes:

1. Every child resource from `common/`.
2. Every child resource from the selected tool bundle.
3. Every child resource from each selected environment bundle.

For Gradle with GitHub, this means copying all of `common/`, all of `gradle/`, and all of `github/`.
Do not filter files by name, extension, path, or repository state.

For every source path:

- Create it when the target path is absent.
- Keep it when the target bytes match.
- Preserve and report it when the target differs.
- Replace differing content only after explicit user approval.

`common/` MUST NOT provide root `AGENTS.md` or `CLAUDE.md`.
The selected tool bundle owns both root instruction files.

## Environment Configuration

Environment bundles copy all of their resources before configuration.
The CI definitions remain inert at their packaged locations until the selected tool is activated.

### GitHub

Copy the selected tool resource from `.github/ci/<tool>.yaml` to `.github/workflows/<tool>.yaml`.
Leave the packaged resources under `.github/ci/` available as the environment catalog.

### GitLab

Create the target root `.gitlab-ci.yml` with an include for the selected tool resource:

```yaml
include:
  - local: .gitlab/ci/<tool>.gitlab-ci.yml
```

Leave all packaged resources under `.gitlab/ci/` in place.

When both environments are selected, configure both surfaces.
When neither is selected, do not add an environment bundle.

## Tool Configuration

Read the copied root `AGENTS.md` from the selected tool bundle.
Follow its native setup, hook, fix, and validation guidance.
Do not invent a shared command layer across tools.

## Completion

Inspect the complete target diff and run the selected tool's documented validation.
Report:

- target path, selected tool, and selected environments
- complete bundles copied
- files written, kept, conflicted, or replaced with approval
- GitHub or GitLab CI resources activated
- tool setup and validation results
