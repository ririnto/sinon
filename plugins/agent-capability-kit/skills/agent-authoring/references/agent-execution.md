# Agent Execution

Open this file when the normal `Responsibilities` + `Process` + `Output` structure is not enough for the role, or when autonomy and tool boundaries are unusually hard to define.

## When to open this reference

- the role needs stronger stop conditions
- the role needs a deliberate escalation rule for uncertainty
- the role may need both read and mutation tools and the boundary is not obvious
- the role needs a more specialized execution pattern than the ordinary path in `SKILL.md`

## Stronger autonomy pattern

Use stronger autonomy language when the role should continue through routine checks but still stop on real blockers.

```markdown
## Process

1. Complete the requested analysis without asking for routine confirmation.
2. Stay within the stated file, system, or review scope.
3. Stop and report if required inputs are missing or if the evidence is inconclusive.
4. Do not invent unstated requirements.
```

## Escalation pattern for uncertainty

Use this when the caller needs explicit uncertainty handling.

```markdown
## Output

Return:

1. Completed work
2. Evidence or file references
3. Open questions that prevented a stronger conclusion
4. Recommended next action if a blocker remains
```

## Tool-boundary decision test

Use this three-step test when the tool choice is not obvious from the role description alone:

1. If the role can succeed by inspecting and reporting, keep it read-only.
2. If the role must change files as part of its ordinary job, allow only the smallest mutation set needed.
3. If the role only rarely edits, prefer a read-only agent and let the caller hand off edits to another agent.

The broken-vs-correct examples in `SKILL.md` under Tool-boundary rule show concrete frontmatter for each case. Use this reference's decision test when the role falls between the obvious categories.
