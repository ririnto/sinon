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

Sinon publishes Claude Code and Codex plugins with portable Agent Skills.

## Project Structure

- `plugins/` contains publishable packages.
- `.claude-plugin/marketplace.json` owns local package roots and external catalog registrations.
- `.agents/plugins/marketplace.json` is the generated Codex view of that inventory.
- `docs/agent-references/` contains repository authoring conventions.
- `scripts/` contains repository validation checks.
- `rules/` contains repository Markdown custom lint rules.
- `.github/` contains GitHub repository automation configuration.

Keep one documented responsibility per top-level component.
Update architecture, dependencies, consumers, and documentation when adding, removing, or moving a component.
Do not add legacy parallel surfaces or compatibility shims without an external contract.

## Authoring

Write all repository guidance and agent-to-agent communication in English.
Use official OpenAI documentation for Codex behavior and shared instruction design.
Use matching host and model documentation for host-specific or model-specific behavior.
Use official vendor documentation for technical claims, and identify repository preferences as local rules.

## Task References

Use [repository conventions](docs/agent-references/repository-conventions.md) for the language or file type under change.
Use [TypeScript conventions](docs/agent-references/typescript.md) for TypeScript source under `scripts/` or `rules/`.
Use [instruction authoring](docs/agent-references/instruction-authoring.md) when changing agent guidance, skill activation or content, host hooks, or model prompts.

## Completion And Checks

Make the smallest complete change that meets the acceptance criteria.
Continue through implementation, relevant proof, and proportional diff review.
Preserve unrelated work and report a precise blocker when required evidence or authority is missing.
When guidance blocks progress, name its file, quote the relevant instruction, and distinguish the requirement from your interpretation.
Use the existing TypeScript, Node.js, and pnpm commands.
Choose checks for changed behavior and explicit acceptance criteria.
Reuse passing evidence for unaffected checks.
Run `pnpm install` after authorized dependency changes.
The repository-wide check is `pnpm run check`.
Its named `check:*` commands support narrower validation.
Report exact commands, exit codes, and any required check that could not run.

## Authority And Publication

Explicit user instructions override this guidance within system, safety, and tool constraints.
Read-only inspection does not authorize changing the inspected resource.
Continue authorized edits and checks without asking again for each step.
The user-facing root session owns integration and publication, and grants any delegated Git actions explicitly.
Publish only capability-scoped plugin components.
Keep general orchestration profiles out of plugins.

## Security And Configuration

Do not edit credentials, local configuration, caches, or vendored files unless the task names them.
Keep task rationale and evidence self-contained in Git, without work-item identifiers, review URLs, or local environment details.
Use repository-relative paths and portable examples in committed content and messages.
Use branch names for work tracking, handoffs, and GitHub tracking content.
Do not base tracking documents or links on fixed commit, file, or content hashes.
Review changes that cross command, filesystem, network, credential, or publication boundaries for safety risks.
