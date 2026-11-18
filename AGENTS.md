---
metadata:
  reference:
    OpenAI skills:
      url: https://learn.chatgpt.com/docs/build-skills.md
    OpenAI project instructions:
      url: https://learn.chatgpt.com/docs/agent-configuration/agents-md.md
    OpenAI instruction design:
      url: https://developers.openai.com/blog/rethinking-skills-and-prompts-for-gpt-6-astra.md
---

# Repository Guidelines

Sinon publishes independently installable Claude Code and Codex plugins.
Keep each package's runtime, tests, assets, and instruction dependencies inside its package root.
Maintain the Claude and Codex marketplaces separately.
Herdr is an external catalog entry.
Keep general orchestration profiles outside published packages.

## Authoring

Write repository guidance and agent handoffs in English.
Follow [repository conventions](docs/agent-references/repository-conventions.md) for the affected file type and [TypeScript conventions](docs/agent-references/typescript.md) for TypeScript tools, rules, and tests.
For skills, hooks, prompts, or agent roles, use [instruction authoring](docs/agent-references/instruction-authoring.md).
Keep one owner for each responsibility and migrate affected callers, documentation, and dependencies together.
Retain a compatibility path only for an external contract.
Source technical claims from the vendor for the relevant host, model, or runtime and identify Sinon preferences as local policy.

## Delivery

Follow [contribution guidance](CONTRIBUTING.md) for formatting, commit bodies, PR review, and integration.
Use the [verification map](docs/verification.md) for focused checks and the full pre-push gate.
After an authorized dependency change, run `pnpm install`.
Report commands, exit status, the behavior observed, and any outstanding evidence.

Carry authorized work through the requested outcome and verify it from the consumer's entry point.
Use a reversible assumption for routine gaps and report it when it affects the result.
When a decision needs new authority, continue independent work and identify the specific decision or blocker.
If guidance blocks delivery, name the file, quote the requirement, and explain how it applies.

## Authority And Publication

User instructions govern scope within system, safety, and tool limits.
Inspection grants read access only.
The user-facing session owns integration and publication and must explicitly grant any delegated Git action.
Leave credentials, local configuration, caches, and vendored files alone unless the task names them.
Review changes across command, filesystem, network, credential, and publication boundaries.

Use repository-relative paths and portable examples in committed material.
Record rationale and evidence in Git without work-item identifiers, review URLs, or private environment details.
Use branch names for tracking and handoffs, including links to tracked work.
