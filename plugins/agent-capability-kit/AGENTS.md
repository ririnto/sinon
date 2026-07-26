# Repository Guidelines

Keep published agents in this plugin's `agents/` directory.

## Project Structure

Open `skills/agent-authoring/SKILL.md` for agent contracts and `skills/skill-authoring/SKILL.md` for portable skill work.

## Build, Test, and Development Commands

Run `claude plugin validate plugins/agent-capability-kit` after agent changes.
Follow the review procedure in `skills/skill-authoring/SKILL.md` after changing a skill.

## Coding Style and Testing

Agents use direct kebab-case filenames with matching names, explicit model and effort, leaf topology, boundaries, escalation, and output.
Skills keep `name` and capability-first `description` frontmatter, then provide a self-sufficient common path.
Put conditional depth in references or assets.

## Commit and Publication

Do not add a general orchestrator profile or child-delegation tools to installable agents.

## Security and Configuration

Read-only roles expose no mutation tools.
Open the authoring skill when tool boundaries, frontmatter, or host behavior needs detailed guidance.

Do not duplicate agent inventories or current model catalogs here.
The authoring skills own runtime field guidance.
