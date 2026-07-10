# Repository Guidelines

This file applies to shared agents and Agent Skills in `plugins/agent-capability-kit/`.
It overrides broader plugin guidance.
Keep shared Claude agents in `agents/` and their Codex counterparts in root `.codex/agents/`.

## Project Structure

Open `skills/agent-authoring/SKILL.md` for agent contracts and `skills/skill-authoring/SKILL.md` for portable skill work.
Open `scripts/agent-routing-manifest.json` only when routing or counterpart parity changes.

## Build, Test, and Development Commands

Run `bun scripts/agent-routing.ts`, `claude plugin validate plugins/agent-capability-kit`, and `bun run check` after agent changes.
Use the skill-authoring validation checklist after changing a `SKILL.md`.

## Coding Style and Testing

Agents use direct kebab-case filenames with matching names, explicit model and effort, leaf topology, boundaries, escalation, and output.
Skills keep `name` and capability-first `description` frontmatter, then provide a self-sufficient common path.
Put conditional depth in references or assets.

## Commit and Publication

Keep Claude and Codex counterparts aligned.
Do not add a general orchestrator profile or child-delegation tools to installable agents.
The root session integrates and publishes.

## Security and Configuration

Read-only roles expose no mutation tools.
Open the authoring skill when tool boundaries, frontmatter, or host behavior needs detailed guidance.

## Scope and Precedence

Do not duplicate agent inventories or current model catalogs here.
The routing manifest and authoring skills own those facts.
A closer skill instruction or component README controls its subtree when it differs from this file.
