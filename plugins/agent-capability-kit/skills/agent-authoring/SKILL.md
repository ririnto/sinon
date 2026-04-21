---
name: agent-authoring
description: Create or refactor Claude Code agents with clear trigger descriptions, bounded tool access, and strong system prompts for autonomous work. Use when authoring or revising agent files offline.
---

# Agent Authoring

Create or refactor one reusable Claude Code agent so it is easy to trigger, safe to run, and self-explanatory without external documentation.

## Scope

This skill owns agent files in `agents/*.md`.

Keep the scope on one agent role per file. Preserve the existing job the agent covers unless the task explicitly changes that scope.

## Operating rules

1. Write one agent file for one clear role.
2. Keep repository-facing and agent-facing instructions in English.
3. Make the `description` field the discovery surface. It must say when to use the agent and include concrete trigger examples.
4. Give the agent the smallest safe tool boundary for the job.
5. Make the body read like a system prompt for autonomous execution, not like release notes or background prose.
6. Put the normal authoring path in the agent file itself. Do not rely on external web pages or hidden conventions.
7. State the output shape explicitly so the caller can use the result without guessing.
8. Keep the ordinary path self-sufficient inside the agent file; do not require the caller to load another skill, hidden prompt, or external document just to execute the agent's main workflow.
9. Make process verbs and declared tools agree. A report-only agent MUST NOT claim file edits, and an editing agent MUST have the mutation tools its process requires.

## Required frontmatter

Every agent file should include these fields in frontmatter:

- `name`
- `description`
- `model`
- `color`

Omit `tools` by default. Use it only when the agent needs a bounded tool surface or a tool boundary different from the default environment.

Other optional frontmatter fields may be kept only when the host actually supports them and the field changes runtime behavior in a meaningful way.

## Frontmatter rules

### `name`

- Use kebab-case.
- Make it role-oriented, not task-ticket-oriented.
- Prefer stable names such as `schema-reviewer`, `docs-refiner`, or `release-checker`.
- Do not encode one temporary request into the name.

### `description`

The description is the main trigger surface. It should do all of the following:

- start with `Use this agent when...`
- name the job, inputs, or system clearly
- include 2 to 4 concrete `<example>` blocks
- make it obvious why this agent is the right fit

Use examples that look like realistic user intent, not abstract labels.

Weak:

```markdown
description: Helps with schemas.
```

Stronger:

```markdown
description: Use this agent when a schema, contract, or config file needs focused review. Examples:

  <example>
  Context: API contract review before release
  user: "Review this OpenAPI file for consistency and missing fields"
  assistant: "I'll use the schema-reviewer agent to inspect the file closely."
  <commentary>
  The task is narrow, analytical, and does not require direct edits.
  </commentary>
  </example>
```

### `model`

- Default to `inherit`.
- Choose a specific model only when the agent has a durable, role-specific need that should not depend on the caller.

### `color`

- Pick a stable color that helps distinguish the agent visually.
- Keep the existing color on refactors unless there is a reason to change it.

### `tools`

- Omit `tools` when the default tool set is already appropriate.
- Add `tools` when the role needs a tighter boundary.
- Grant only the tools the agent genuinely needs for its ordinary path.

Examples:

- review-only agent: `tools: ["Read", "Grep"]`
- direct editing agent: `tools: ["Read", "Write"]`
- multi-file refactor agent: add mutation tools only if the role must change files as part of its normal job

## Minimal body structure

The body should stay short, direct, and executable. Use this shape unless the role has a strong reason to vary:

1. One role statement
2. `## Responsibilities`
3. `## Process`
4. `## Output`

Each section should be concrete enough that the agent can act without extra prompting.

### Role statement

Open with one or two lines that define the agent's job.

Example:

```markdown
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

1. Read the existing agent file if you are refactoring; otherwise start from `assets/agent-template.md` or use the Minimal example below as an inline fallback.
2. Define the agent role in one sentence.
3. Check that the role is narrow enough to be discoverable and autonomous.
4. Draft or revise frontmatter:
   - `name` is stable and role-based
   - `description` says when to use the agent and includes concrete `<example>` blocks
   - `model` defaults to `inherit`
   - `color` is stable and distinguishable
   - `tools` appears only when a bounded tool surface is needed
5. Write the body with a role statement plus `Responsibilities`, `Process`, and `Output` sections.
6. Make the autonomy level explicit:
   - the agent should complete its narrow role without asking for routine confirmation
   - the agent should stay inside the requested scope
   - the agent should report blockers or uncertainty instead of inventing missing facts
7. Check the tool boundary against the ordinary path:
   - read-only roles stay read-only
   - editing roles get mutation tools only when direct edits are part of the role
   - broad tool access must be justified by the role, not by convenience
8. Verify that the output section is directly usable by the caller.
9. Check that the ordinary path is self-sufficient inside the agent body. If the draft says to 'load skill X first' or depends on hidden runtime guidance, fold the required instructions back into the agent file.
10. Check that the tool boundary matches the process and output claims. Remove file-updating claims from read-only agents, or add the minimal mutation tools only when direct edits are genuinely part of the role.

## Autonomy defaults

Use these defaults unless the role needs a stricter rule:

- The agent should act independently inside its bounded role.
- The agent should not broaden scope on its own.
- The agent should not claim work it did not perform.
- The agent should surface uncertainty, blockers, and risks explicitly.
- The agent should prefer deterministic checks and direct evidence over speculation.

For most agents, that means: do the requested role fully, stay narrow, and return a structured result.

If the role depends on repository-specific invariants such as worktree isolation, observability-backed validation, or execution-plan lifecycle rules, state those invariants directly in the body instead of assuming they are known elsewhere.

## Tool-boundary rule

Choose the narrowest tool set that still lets the agent complete its ordinary job.

- Review, triage, and analysis agents should usually stay read-only.
- Writing or editing agents may receive mutation tools when editing is part of the core role.
- Avoid giving a role both broad read access and broad mutation access unless the role genuinely needs both every time.
- If a role can succeed with fewer tools, remove the extras.

Broken:

```markdown
tools: ["Read", "Write", "Edit", "Bash", "Grep"]
```

Correct for a read-only reviewer:

```markdown
tools: ["Read", "Grep"]
```

Correct for a bounded editor:

```markdown
tools: ["Read", "Write"]
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
4. Confirm that the `description` examples and `tools` choice match the role.
5. Confirm that the process does not require hidden skill loading or contradict the tool boundary.

## Minimal example

Use this as a smallest useful agent starting point:

```markdown
---
name: schema-reviewer
description: Use this agent when a schema, contract, or config file needs focused review. Examples:

  <example>
  Context: API contract review before release
  user: "Review this OpenAPI file for consistency and missing fields"
  assistant: "I'll use the schema-reviewer agent to inspect the file closely."
  <commentary>
  The task is narrow, analytical, and does not require direct edits.
  </commentary>
  </example>

  <example>
  Context: configuration audit before deployment
  user: "Check this config for invalid defaults and risky settings"
  assistant: "I'll use the schema-reviewer agent to review the configuration file."
  <commentary>
  The request is a focused file review with a clear analytical outcome.
  </commentary>
  </example>
model: inherit
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
- If the body is long because of many examples, keep one short example in the agent and move extra patterns to assets.
- If the tool boundary is hard to choose, default to the read-only or narrower set first.
- If the agent needs exceptional autonomy or unusually broad tools, document the reason directly in the body or open the deeper reference for that blocker.

## Pitfalls

- Do not write a generic description without concrete trigger examples.
- Do not make the agent responsible for multiple unrelated roles.
- Do not grant broad tools when read-only or narrower mutation access is enough.
- Do not leave the output contract implicit.
- Do not depend on external documentation for the ordinary authoring path.
- Do not tell the caller to load another skill or prompt in the agent's main workflow.
- Do not mix report-only wording with claims about updated files, grades, or pull requests unless the tools and process actually support those actions.

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
- `assets/agent-frontmatter-patterns.md` - copy when you need more frontmatter examples for different role shapes
