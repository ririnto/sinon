---
name: skill-authoring
description: Author new Agent Skills that conform to the open Agent Skills format (SKILL.md with YAML frontmatter, optional scripts/, references/, assets/ directories). Use when the user asks to create, scaffold, write, design, draft, package, or publish a "skill", "agent skill", "SKILL.md", or wants to give an agent new capabilities via an agentskills.io-compatible package. Also use when the user wants to refactor an existing prompt, workflow, or runbook into a reusable skill, or when they ask how to structure a skill's frontmatter, description, scripts, or reference files. Do NOT use when the user only wants to *use* an existing skill, is asking general prompting questions, or is building an MCP server (use `mcp-builder` instead).
license: MIT
compatibility: Requires uvx (shipped with uv — https://docs.astral.sh/uv/) and python3. Validation delegates to the official skills-ref reference library at https://github.com/agentskills/agentskills/tree/main/skills-ref, pulled on demand via uvx.
metadata:
  source: https://agentskills.io
  version: "1.0"
---

# Agent Skill authoring

## Overview

An Agent Skill is a folder containing a `SKILL.md` file with YAML frontmatter (at minimum, `name` and `description`) plus Markdown instructions. Skills may also bundle executable `scripts/`, detailed `references/`, and static `assets/`. Agents use progressive disclosure: only `name` + `description` load at startup, the full `SKILL.md` loads on activation, and bundled files load on demand.

This skill packages the full agentskills.io specification, best-practices guidance, and tested scaffolding/validation scripts so the agent can produce a high-quality, spec-compliant skill in one pass.

## Quick reference

| Task | Approach |
| --- | --- |
| Scaffold a new skill directory | `sh scripts/new_skill.sh <name> [target-dir]` |
| Validate frontmatter and layout | `sh scripts/validate_skill.sh <skill-dir>` (wraps `uvx --from skills-ref agentskills validate`) |
| Generate `<available_skills>` XML for agent prompts | `uvx --from skills-ref agentskills to-prompt <skill-dir>...` |
| Look up spec details | Read `references/specification.md` |
| Author best practices | Read `references/best-practices.md` |
| Tune the `description` for triggering | Read `references/optimizing-descriptions.md` |
| Design executable scripts | Read `references/using-scripts.md` |
| Build a skills-compatible client | Read `references/client-implementation.md` |

## When to use this skill

Activate this skill whenever the user wants to create, edit, or package an Agent Skill. Typical prompts:

- "Make a skill that does X."
- "I want to give my agent a workflow for Y."
- "Turn this runbook/prompt into a skill."
- "Review/validate this SKILL.md."
- "How do I structure my `description` so it triggers on Z?"

Do NOT activate this skill for generic prompt-engineering questions or for building MCP servers (prefer the `mcp-builder` skill for the latter).

## Workflow

Follow these steps in order. Do not skip validation.

### Step 1: Clarify scope before authoring

Ask the user only the questions needed to produce a coherent skill. In most cases the answers to the following are enough:

1. What task should the skill accomplish? (one sentence)
2. What triggering phrases / user intents should activate it?
3. Are there existing artifacts (runbooks, scripts, prompts, examples) to draw from?
4. Target audience: general agents, or a specific client (Claude Code, Cursor, etc.)?
5. Any environment prerequisites (runtimes, network, secrets)?

If the user already supplied enough context, skip the questions and proceed.

> [!IMPORTANT]
> A common failure mode is generating a skill from generic LLM knowledge. Ground the skill in real artifacts (runbooks, incident reports, API specs, fix patches, code-review comments) whenever possible. See `references/best-practices.md` → "Start from real expertise".

### Step 2: Scaffold the directory

Use the bundled script to create the skeleton:

```bash
sh scripts/new_skill.sh my-skill-name ./path/to/parent
```

The script creates:

```text
my-skill-name/
├── SKILL.md          # Pre-filled from assets/SKILL.template.md
├── scripts/          # Empty, keep if scripts are bundled
├── references/       # Empty, keep for progressive-disclosure content
└── assets/           # Empty, keep for templates and static data
```

Remove any of `scripts/`, `references/`, or `assets/` that will not be used. Directory names other than `SKILL.md` are not required by the spec.

### Step 3: Write the frontmatter

The frontmatter is the most scrutinized part of the file. Follow the spec exactly:

| Field | Required | Constraints |
| --- | --- | --- |
| `name` | Yes | 1-64 chars. Lowercase `a-z`, digits, hyphens. No leading/trailing/consecutive hyphens. MUST match the parent directory name. |
| `description` | Yes | 1-1024 chars. Non-empty. Covers what the skill does AND when to use it. |
| `license` | No | License name or reference to a bundled license file. |
| `compatibility` | No | 1-500 chars. Only when environment constraints matter. |
| `metadata` | No | Arbitrary string-to-string map. Use unique keys to avoid collisions. |
| `allowed-tools` | No | Space-separated list of pre-approved tools (experimental). |

Minimal frontmatter:

```markdown
---
name: my-skill-name
description: One or two sentences stating what the skill does and when to use it.
---
```

For full field semantics and valid/invalid `name` examples, read `references/specification.md`.

### Step 4: Write a description that triggers reliably

The `description` is the only text the agent sees at startup when deciding whether to activate the skill. Apply these principles:

- Imperative framing. "Use this skill when..." beats "This skill does...".
- User-intent focus. Describe what the user is trying to achieve, not implementation details.
- Be pushy. Explicitly enumerate the triggering contexts, including ones where the user does not name the domain (e.g. "even if they do not mention 'CSV' or 'analysis'").
- Set boundaries. Include a "Do NOT use..." clause when adjacent skills or tools could be confused with this one.
- Stay under 1024 characters. Descriptions tend to grow; trim at the end.

Good example:

```yaml
description: Analyze CSV and tabular data files — compute summary statistics, add derived columns, generate charts, and clean messy data. Use when the user has a CSV, TSV, or Excel file and wants to explore, transform, or visualize data, even if they do not explicitly mention "CSV" or "analysis". Do NOT use for database ETL or Excel formula editing.
```

Poor example:

```yaml
description: Helps with CSVs.
```

Iteratively refine using the optimization loop in `references/optimizing-descriptions.md` if the skill is misfiring.

### Step 5: Write the body

The body is the Markdown content after the closing `---`. There are no structural restrictions, but these sections work well in most skills:

1. `## Overview` — one-paragraph summary of the skill's purpose.
2. `## When to use this skill` — restated triggers, negative cases, and prerequisites.
3. `## Workflow` or `## Quick reference` — stepwise procedure or lookup table.
4. `## Gotchas` — environment-specific facts that defy reasonable assumptions. This is often the highest-value section.
5. `## Examples` — short input/output pairs.
6. `## References` — pointers to `references/*.md` with explicit load triggers ("Read `references/api-errors.md` if the API returns a non-200 status.").

Keep the body under ~500 lines / ~5,000 tokens. Move longer material into `references/` and tell the agent when to load each file.

Apply the patterns from `references/best-practices.md` (gotchas sections, output templates, checklists, validation loops, plan-validate-execute, bundled scripts) as appropriate. Not every skill needs every pattern.

### Step 6: Add scripts, references, and assets (optional)

- `scripts/` — Bundled executable code. Prefer self-contained scripts with inline dependency declarations (PEP 723 for Python, `npm:` specifiers for Deno, etc.) so the agent can run them with a single command. See `references/using-scripts.md`.
- `references/` — Additional Markdown loaded on demand. Keep files focused and topical. Name them descriptively (`api-errors.md`, `schema.md`, not `reference.md` alone).
- `assets/` — Templates, schemas, images, data files. Reference them from `SKILL.md` or `scripts/` via relative paths.

Reference files via relative paths from the skill directory root. Keep references one level deep. Avoid deeply nested reference chains.

> [!TIP]
> When bundling scripts, design them for agentic use: no interactive prompts, helpful `--help` output, structured output (JSON/CSV), clear error messages, meaningful exit codes, and predictable output size. See `references/using-scripts.md` → "Designing scripts for agentic use".

### Step 7: Validate

Run the validator before declaring the skill done:

```bash
sh scripts/validate_skill.sh ./my-skill-name
```

The wrapper delegates to the official [`skills-ref`](https://github.com/agentskills/agentskills/tree/main/skills-ref) reference library via `uvx`. The equivalent direct invocation is:

```bash
uvx --from skills-ref agentskills validate ./my-skill-name
```

`skills-ref` checks that:

- The directory contains a `SKILL.md` with well-formed YAML frontmatter.
- `name` is present, 1-64 chars, lowercase-hyphen-digit only, and matches the parent directory basename.
- `description` is present and within 1-1024 chars.
- Optional fields satisfy their spec constraints if present.

First invocation downloads and caches `skills-ref`; subsequent runs are near-instant.

### Step 8: Smoke-test activation

Load the skill into the target agent (Claude Code, VS Code + Copilot, Cursor, Gemini CLI, etc.) and issue 3-5 prompts that should trigger it and 2-3 near-misses that should not. If it mis-triggers, iterate on the `description` using `references/optimizing-descriptions.md`.

## Critical constraints

These are non-negotiable. Re-check them on every edit.

- `name` regex (effectively): `^[a-z0-9]+(-[a-z0-9]+)*$`, length 1-64, must equal the parent directory basename.
- `description` is required and capped at 1024 characters.
- `SKILL.md` must contain YAML frontmatter delimited by `---` at the very top of the file.
- Every bundled file referenced from `SKILL.md` must exist at its stated relative path.
- Keep `SKILL.md` under 500 lines / ~5,000 tokens. Push detail into `references/`.

## Gotchas

- Do not hide triggering context in references. The agent only sees the `description` at discovery time. Anything needed to decide activation must be in `description`, not in the body or `references/`.
- Do not embed secrets in `SKILL.md`, scripts, or assets. Skills are usually version-controlled and shared.
- Do not assume global installs. For scripts, prefer runners that self-provision dependencies (`uv run`, `npx <pkg>@<ver>`, `deno run`, `bun run`) over `pip install` + direct invocation.
- Project-level skills can shadow user-level skills in most clients. If a user reports a skill "not working", check for a same-named skill earlier in the scope precedence.
- Frontmatter YAML with unquoted colons is invalid even if some clients parse it. Quote any `description: Use this when: X` style values.
- File-read activation vs. tool activation varies by client. Make the skill work either way — do not rely on a specific activation mechanism in the body.

## References

Load the relevant reference file on demand rather than reading everything:

- `references/specification.md` — Full format spec (frontmatter fields, constraints, directory rules, progressive disclosure). Load when unsure about a frontmatter field or layout rule.
- `references/best-practices.md` — Authoring patterns (gotchas, templates, checklists, validation loops, plan-validate-execute, bundled scripts). Load when designing the body of a non-trivial skill.
- `references/optimizing-descriptions.md` — Trigger-rate optimization loop for the `description` field. Load when a skill is misfiring or you need to tune triggers.
- `references/using-scripts.md` — One-off commands vs. bundled scripts, self-contained script formats, agentic design rules. Load when the skill ships executable code.
- `references/client-implementation.md` — How clients discover and activate skills. Load when targeting an unusual runtime (sandboxed, cloud, custom agent) or building a skills-compatible client.

## Output contract

When finished, report to the user:

1. The absolute path of the created/edited skill directory.
2. The frontmatter (rendered verbatim).
3. The output of `scripts/validate_skill.sh` (must be clean).
4. Suggested next steps (install location for the target client, smoke-test prompts, link to `references/optimizing-descriptions.md` if tuning is needed).
