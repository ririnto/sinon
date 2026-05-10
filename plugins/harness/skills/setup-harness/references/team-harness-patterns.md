# Team Harness Patterns

Open this reference when a target repository wants an explicit agent-team operating model, role handoffs, or review loop inspired by `revfactory/harness`.

## Boundary

`revfactory/harness` is a Claude Code team-architecture factory: it generates agents, skills, and an orchestrator from a domain description. This setup skill is different. It installs repository docs, config, validators, CI, hooks, and evidence paths. Use the upstream team-factory model as a planning reference, not as a reason to add runtime generation or daemon behavior to this plugin.

## Transferable patterns

| Pattern | Use when | Local harness expression |
| --- | --- | --- |
| Pipeline | Work has a stable sequence | `spec-writer` drafts or updates the plan, `e2e-driver` implements and validates, `code-reviewer` reviews, then `doc-gardener` checks drift |
| Fan-out/Fan-in | Independent checks can run in parallel | `architecture-guard`, `doc-gardener`, and targeted validation commands report findings before one owner resolves them |
| Expert Pool | The task needs one specialist at a time | Route architecture, documentation, delivery, review, or planning requests to the matching bundled agent |
| Producer-Reviewer | Output must be challenged before handoff | Pair `e2e-driver` or `spec-writer` with `code-reviewer` and require evidence in a proof packet |
| Supervisor | One role must coordinate several checks | Use the caller or target workflow as the supervisor; keep the supervisor policy in `docs/harness/guardrails.md` or a workflow doc |
| Hierarchical Delegation | Work decomposes across subdomains | Record the decomposition in an execution plan, then dispatch bounded agents against configured docs and validation paths |

## Execution mode comparison

Use this comparison only for target-owned workflow documentation. Harness setup itself must still work without team mode.

| Mode | Use when | Data flow | Constraint |
| --- | --- | --- | --- |
| Agent team | Two or more roles need live coordination, challenge, or shared task ownership | Team messages, shared tasks, and durable artifacts | Host support may require an experimental flag or a specific runtime |
| Subagents | Roles can work independently and return summarized results to one caller | Parallel task results returned to the caller | Subagents cannot coordinate directly with each other |
| Hybrid | Different phases need different coordination styles | Files or proof packets bridge phase boundaries | Each mode switch must name the handoff artifact |

## Data transfer protocols

Record the chosen protocol beside the workflow that uses it.

| Protocol | Use when | Required record |
| --- | --- | --- |
| Message-based | A role must unblock or challenge another role during the run | Sender, receiver, purpose, and expected response |
| Task-based | Work can be claimed, updated, and reviewed from a shared task list | Task owner, status, dependencies, and completion evidence |
| File-based | Later phases or audits must read durable output | Path under `_workspace/`, `docs/harness/`, generated docs, or the proof packet |
| Return-value | A short one-shot result is enough | Caller, command or agent, and summary format |

## Team sizing

- Use one coordinator plus two to five specialist roles for most target workflows.
- Split into phases instead of growing one large team when roles exceed five specialists.
- Prefer fan-out/fan-in for independent audits, producer-reviewer for risky changes, and supervisor only when work assignment changes during execution.
- Preserve intermediate artifacts so a later phase, reviewer, or doc-gardening pass can verify what happened.

## Documentation shape

Record the selected pattern in target-owned docs instead of extending the canonical config template.

```markdown
# Agent Team Workflow: {Name}

## Pattern

| Field | Value |
| --- | --- |
| Pattern | {pipeline, fan-out/fan-in, expert pool, producer-reviewer, supervisor, hierarchical delegation} |
| Harness config | `docs/harness/config.json` |
| Owner | {human or agent role} |
| Exit evidence | `{proof packet, command output, review verdict, or runtime trace}` |

## Roles

| Role | Agent Or Command | Responsibility | Handoff |
| --- | --- | --- | --- |
| Planner | `spec-writer` | Create or update the execution plan | Plan path and acceptance criteria |
| Implementer | `e2e-driver` | Change behavior and collect runtime evidence | Proof packet and command output |
| Reviewer | `code-reviewer` | Identify blocking issues | Review verdict |
| Guard | `architecture-guard` | Validate layer and guardrail checks | Finding list |

## Required Evidence

1. Harness validation output
2. Targeted test or runtime command output
3. Review verdict
4. Residual risk or known-violation entry
```

## Application rules

- Keep generated or target-specific agent definitions outside this plugin unless the plugin itself is intentionally extended.
- Do not create new agents until a target workflow names the missing role, trigger, tools, and evidence contract.
- Do not encode team patterns in `docs/harness/config.json`; keep them in workflow docs, execution plans, or guardrail docs.
- Prefer producer-reviewer or fan-out/fan-in for maturity ratchets because they produce independent evidence before gates become `error` gates.

## CLAUDE.md pointer shape

Use this shape when the target repository wants root instructions to point at the operating model without becoming an encyclopedia.

```markdown
## Harness Workflow: {Name}

Goal: {one sentence describing the repository workflow}

Trigger: For {domain or workflow} work, read `docs/harness/config.json`, then follow `{workflow doc path}`. Direct answers are acceptable for simple questions that do not change repository files.

History:

| Date | Change | Surface | Reason |
| --- | --- | --- | --- |
| {YYYY-MM-DD} | Initial workflow record | `{workflow doc path}` | {reason} |
```
