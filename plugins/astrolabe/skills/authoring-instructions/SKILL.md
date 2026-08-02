---
name: authoring-instructions
description: Use when writing or reviewing durable agent instructions, skill references, configuration guidance, or other portable engineering artifacts.
---

# Authoring Instructions

## Write the Current End State

Describe the supported end state and active contracts.

Do not add legacy labels, deprecated alternatives, migration guides, or historical transition narrative.

State the outcome, scope, authority, acceptance criteria, and stopping condition before procedure.

Give each rule and detailed contract one canonical owner, use progressive disclosure for conditional detail, and remove duplicate prose instead of adding exceptions.

## Separate Durable Rules From Session Goals

Put reusable decisions and supported procedures in the package's owned instruction or reference.

Keep temporary goals, scratch plans, generated helpers, and session-only coordination outside the repository unless the user requests them as deliverables.

Do not turn a session goal into a durable rule without deciding which package asset owns it.

## Keep the Main Skill Portable

Keep common guidance in `SKILL.md` and load a reference only when a concrete trigger requires conditional or heavy detail.

Use package-relative paths, command names, schema identifiers, and documented runtime contracts.

Resolve contradictions before delivery instead of adding exceptions that leave two competing rules.

Avoid machine-specific paths, credentials, network assumptions, generated output, incident history, and transition narrative.

Open [references/portable-artifacts.md](references/portable-artifacts.md) when conditional, heavy, or runtime-sensitive portability details need a dedicated owner.

## Use Stable Writing and Shell Forms

Write direct English in active voice and keep one sentence on each physical Markdown line.

Use `{{name}}` for prose placeholders and preserve angle brackets only when a format requires them.

Use block YAML lists and kebab-case keys when the format permits them.

Use `sh` fences for portable shell examples.

POSIX `sh` scripts use `#!/usr/bin/env sh`, `# -*- coding: utf-8 -*-`, and `set -e`.

Bash-only scripts use `#!/usr/bin/env bash`, `# -*- coding: utf-8 -*-`, and `set -eo pipefail`.

Let project tooling failures propagate so checks cannot report false success, while user-code commands may handle exceptions when their behavior requires it.

## Verify the Artifact

Check headings, links, placeholders, code fences, encoding, portability, and duplicate-content risks before delivery.

Confirm that each changed rule has one owner and that references load only under their stated trigger.

Return changed paths, owned decisions, link and schema evidence, duplicate-content findings, and unresolved blockers.
