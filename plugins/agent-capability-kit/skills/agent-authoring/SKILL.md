---
name: agent-authoring
description: >-
  Create or refactor Claude Code agents with clear trigger descriptions, bounded tool access, and strong system prompts for autonomous work.
  Triggers on agent frontmatter fields, tool allowlist boundaries, or system-prompt structure that keeps agents scoped to a single responsibility.
---

# Agent Authoring

Create or refactor one reusable Claude Code agent so it is easy to trigger, safe to run, and self-explanatory without external documentation.

## Scope

This skill owns agent files in `agents/*.md`.

Keep the scope on one agent role per file.
Preserve the existing job the agent covers unless the task explicitly changes that scope.

## Operating rules

1. Write one agent file for one clear role.
2. Keep repository-facing and agent-facing instructions in English.
3. Make the `description` field the discovery surface.
   - It must say when to use the agent.
4. Give the agent the smallest safe tool boundary for the job.
5. Make the body read like a system prompt for autonomous execution, not like release notes or background prose.
6. Put the normal authoring path in the agent file itself.
   - Use in-file guidance for the ordinary path.
7. State the output shape explicitly so the caller can use the result without guessing.
8. Keep the ordinary path self-sufficient inside the agent file.
   - Put required workflow context in the prompt or agent body.
9. Make process verbs and declared tools agree.
   - A report-only agent returns findings; an editing agent has the mutation tools its process requires.
10. Ordinary authoring remains offline, but maintainers changing host-specific agent behavior should verify against official host documentation when available and record any verification blocker.

## Required frontmatter

Every agent file should include these fields in frontmatter:

- `name`
- `description`
- `color`

Omit `tools` by default.
Use it only when the agent needs a bounded tool surface or a tool boundary different from the default environment.

Other optional frontmatter fields may be kept only when the host actually supports them and the field changes runtime behavior in a meaningful way.

## Frontmatter rules

### `name`

- Use kebab-case exclusively, and the `name` field MUST match the file basename exactly.
  - `agents/schema-reviewer.md` must use `name: schema-reviewer`.
- Make it role-oriented, not task-ticket-oriented.
- Prefer stable names such as `schema-reviewer`, `docs-refiner`, or `release-checker`.
- Keep temporary request details in the caller prompt.

### `description`

The description is the main trigger surface.
It should do all of the following:

- Open with an imperative capability clause that names what the agent does (for example "Enforce…", "Review…", "Detect…", "Author…", "Reproduce…").
- Follow with a trigger clause such as `Use this agent when...`.
- Name the job, inputs, or system clearly.
- Make it obvious why this agent is the right fit.

Agent descriptions use direct trigger language with concrete nouns and task terms.

Weak:

```markdown
description: >-
  Helps with schemas.
```

Stronger:

```markdown
description: >-
  Inspect schemas, contracts, and config files for defects, risks, and missing structure.
  Use this agent when a schema, contract, or config file needs focused read-only review before implementation, release, or migration.
```

### `color`

- Pick a stable color that helps distinguish the agent visually.
- Keep the existing color on refactors unless there is a reason to change it.

### `tools`

- Omit `tools` when the default tool set is already appropriate.
- Add `tools` when the role needs a tighter boundary.
- Grant only the tools the agent genuinely needs for its ordinary path.

Review-only agents usually need read/search tools only.
Direct editing agents need mutation tools only when editing is their normal job.

## Minimal body structure

The body should stay short, direct, and executable.

Use this shape unless the role has a strong reason to vary:

1. One role statement
2. `## Responsibilities`
3. `## Process`
4. `## Output`

Each section should be concrete enough that the agent can act without extra prompting.

### Role statement

Open with one or two lines that define the agent's job.

Example:

```text
You are a specialized review agent for schemas, contracts, and structured configuration.
```

### Responsibilities

List the durable duties of the role.

Good:

```markdown
## Responsibilities

1. Inspect the target file closely.
2. Identify concrete defects, risks, or missing structure.
3. Support findings with direct evidence from the file.
```

### Process

Use an ordered process when the execution path matters.

Good:

```markdown
## Process

1. Read the provided files before drawing conclusions.
2. Check the highest-risk issues first.
3. Keep the review bounded to the requested scope.
4. Verify that every finding is supported by evidence.
```

### Output

Tell the agent exactly what to return.

Good:

```markdown
## Output

Return:

1. Findings in priority order
2. Supporting evidence with file references
3. Remaining uncertainty or blockers
```

## Ordinary authoring procedure

1. Read the existing agent file if you are refactoring.
   - Otherwise start from `assets/agent-template.md` or use the Minimal example below as an inline fallback.
2. Define the agent role in one sentence.
3. Check that the role is narrow enough to be discoverable and autonomous.
4. Draft or revise frontmatter:
   - `name` matches the file basename and is stable and role-based
   - `description` opens with an imperative capability clause, then says when to use the agent
   - model selection belongs to the caller
   - `color` is stable and distinguishable
   - `tools` appears only when a bounded tool surface is needed
5. Write the body with a role statement plus `Responsibilities`, `Process`, and `Output` sections.
6. Make the autonomy level explicit:
   - The agent should complete its narrow role without asking for routine confirmation.
   - The agent should stay inside the requested scope.
   - The agent should report blockers or uncertainty instead of inventing missing facts.
7. Check the tool boundary against the ordinary path:
   - Read-only roles stay read-only.
   - Editing roles get mutation tools only when direct edits are part of the role.
   - Broad tool access must be justified by the role, not by convenience.
8. Verify that the output section is directly usable by the caller.
9. Check that the ordinary path is self-sufficient inside the agent body.
   - If the draft says to 'load skill X first' or depends on hidden runtime guidance, fold the required instructions back into the agent file.
10. Check that the tool boundary matches the process and output claims.
    - Remove file-updating claims from read-only agents, or add the minimal mutation tools only when direct edits are genuinely part of the role.

## Autonomy defaults

Use these defaults unless the role needs a stricter rule:

- The agent should act independently inside its bounded role.
- The agent should stay inside its declared role.
- The agent should claim only work it performed.
- The agent should surface uncertainty, blockers, and risks explicitly.
- The agent should prefer deterministic checks and direct evidence over speculation.

For most agents, that means: do the requested role fully, stay narrow, and return a structured result.

If the role depends on repository-specific invariants such as worktree isolation, observability-backed validation, or execution-plan lifecycle rules, state those invariants directly in the body.
State them where the agent reads them.

## Tool-boundary rule

Choose the narrowest tool set that still lets the agent complete its ordinary job.

- Review, triage, and analysis agents should usually stay read-only.
- Writing or editing agents may receive mutation tools when editing is part of the core role.
- Give a role both broad read access and broad mutation access only when it needs both for its ordinary path.
- If a role can succeed with fewer tools, remove the extras.

Broken:

```markdown
tools:
  - Read
  - Write
  - Edit
  - Bash
  - Grep
```

Correct for a read-only reviewer:

```markdown
tools:
  - Read
  - Grep
```

Correct for a bounded editor:

```markdown
tools:
  - Read
  - Write
```

Also keep the output contract consistent with the tools:

- report-only agent: findings, evidence, and recommended follow-up only
- editing agent: changed files, validation, and remaining risks
- execution agent with `Bash`: runtime evidence, commands or checks performed, and any cleanup or teardown status

## First safe checks

Use simple local checks first:

1. Open the target agent Markdown file.
2. Confirm that the frontmatter includes the required fields.
3. Confirm that the body contains a role statement plus `Responsibilities`, `Process`, and `Output` sections.
4. Confirm that the `description` trigger and `tools` choice match the role.
5. Confirm that the process does not require hidden skill loading or contradict the tool boundary.
6. Confirm that the `name` value matches the file basename without `.md`.

## Minimal example

Use this as a smallest useful agent starting point:

```markdown
---
name: schema-reviewer
description: >-
  Inspect schemas, contracts, and config files for defects, risks, and missing structure.
  Use this agent when a schema, contract, or config file needs focused read-only review before implementation, release, or migration.
color: cyan
tools: ["Read", "Grep"]
---

# Schema Reviewer

You are a specialized review agent for schemas, contracts, and structured configuration.

## Responsibilities

1. Inspect the target files closely.
2. Identify concrete defects, risks, or missing structure.
3. Support every finding with direct evidence.

## Process

1. Read the provided files before drawing conclusions.
2. Check the highest-risk inconsistencies first.
3. Keep the review bounded to the requested scope.
4. Verify that every finding is supported by file evidence.

## Output

Return:

1. Findings in priority order
2. Supporting evidence with file references
3. Remaining uncertainty or blockers
```

## Edge cases

- If the requested role mixes unrelated jobs, split it into one clearer role and move the other job to a separate agent.
- If the description is too vague to trigger reliably, rewrite it before changing the body.
- If the frontmatter is long, tighten the capability and trigger clauses.
- If the tool boundary is hard to choose, default to the read-only or narrower set first.
- If the agent needs exceptional autonomy or unusually broad tools, document the reason directly in the body or open the deeper reference for that blocker.

## Checklist

- Write a specific description with clear trigger conditions.
- Keep one coherent responsibility per agent.
- Grant the narrowest tool set that supports the ordinary path.
- State the output contract explicitly.
- Put ordinary-path guidance in the agent body.
- Include required workflow context in the prompt or agent body.
- Align report-only wording with findings, and editing wording with supported mutation tools.

## Output contract

Return:

1. The final agent file path
2. The full agent Markdown file
3. A short note explaining the trigger conditions
4. A short note explaining the selected tool boundary
5. Any explicit remaining risk or blocker

## Optional support files

- `references/agent-frontmatter.md` - open when a frontmatter field choice is still ambiguous after applying the rules above
- `references/agent-execution.md` - open when the agent needs exceptional autonomy, a non-obvious tool boundary, or a more specialized execution pattern
- `assets/agent-template.md` - copy when creating a new agent from scratch
- `assets/agent-frontmatter-patterns.md` - copy when you need more frontmatter patterns for different role shapes
