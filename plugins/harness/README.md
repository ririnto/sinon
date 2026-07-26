---
description: >-
  Overview of the Harness plugin for composing repository resource bundles.
---

# Harness

Harness provides agent-readable guidance and packaged resource bundles for repository setup.
It does not ship or run a setup program.

## Setup

Register the marketplace and install Harness:

```sh
claude plugin marketplace add /path/to/sinon
claude plugin install harness@sinon
```

Ask an agent to use `harness-install` with one tool and zero or more repository environments.
The skill composes complete resource bundles into the target repository.

## Bundle Model

Every composition starts with the complete `common/` bundle.
Choose exactly one tool bundle:

- `bun`
- `gradle`
- `maven`
- `shell`
- `uv`
- `go`
- `rust`

Choose `github`, `gitlab`, both environments, or neither.
For example, Gradle with GitHub places every child resource from `common/`, `gradle/`, and `github/`.
Chosen bundles are copied as complete trees without file-level selection.

`common/` contains only resources shared by every target.
It does not contain root `AGENTS.md` or `CLAUDE.md`; the selected tool bundle owns those target instructions.

Environment bundles keep CI definitions inert until the agent configures the selected tool:

- `github/.github/ci/<tool>.yaml`
- `gitlab/.gitlab/ci/<tool>.gitlab-ci.yml`

## Skills

| Skill | Use |
| --- | --- |
| `harness-install` | Compose common, tool, and environment bundles into a target. |
| `harness-evolve` | Assess changes to an installed target or future bundle defaults. |

## Package Inventory

- `.claude-plugin/plugin.json`: plugin metadata.
- `skills/harness-install/SKILL.md`: bundle composition and configuration procedure.
- `skills/harness-install/assets/common/`: resources shared by every target.
- `skills/harness-install/assets/{tool}/`: tool-owned configuration and root guidance.
- `skills/harness-install/assets/github/`: GitHub templates and inert CI resources.
- `skills/harness-install/assets/gitlab/`: GitLab templates and inert CI resources.
- `skills/harness-evolve/`: report-first evolution guidance.

Root marketplace tests and support stay outside published plugin roots.
A tool bundle may intentionally include tests for validation logic it distributes, such as Gradle `buildSrc` ktlint rules.

## Ownership And Safety

The agent preserves relative paths and executable modes while copying complete bundles.
Existing matching files are kept.
Differing target files are preserved until the user explicitly approves replacement.
After composition, the agent configures the selected CI resource and follows the selected tool's `AGENTS.md` for setup and validation.
