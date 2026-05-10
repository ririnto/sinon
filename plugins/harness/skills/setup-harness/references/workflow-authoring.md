# Workflow Authoring Reference
Open this reference when a target repository needs a durable workflow definition or agent-team handoff model.


Copy this template when a target repository needs a durable, repository-local workflow for agent or human execution. Keep the workflow tied to `docs/harness/config.json`, but keep project-specific paths and commands in the document body.

````markdown
# {Workflow Name} Workflow

## Purpose

{One paragraph describing the repeated job this workflow makes safe and observable.}

## Profile

| Field | Value |
| --- | --- |
| Harness config | `docs/harness/config.json` |
| Owner | {team, agent, or role} |
| Applies to | {repo area, package, service, or docs path} |
| Trigger | {manual request, pull request, release, scheduled check} |
| Exit signal | {observable result that means the workflow is complete} |

## Operating Model

| Field | Value |
| --- | --- |
| Pattern | {pipeline, fan-out/fan-in, expert pool, producer-reviewer, supervisor, or hierarchical delegation} |
| Coordinator | {human, caller, workflow owner, or bundled agent} |
| Team mode requirement | {none, optional, or required by target runtime} |
| Durable evidence | `{proof packet, readiness entry, known-violation entry, or command output}` |

## Roles And Handoffs

| Role | Agent Or Command | Responsibility | Handoff Artifact |
| --- | --- | --- | --- |
| Planner | `spec-writer` or `{role}` | Define the plan and acceptance criteria | `{plan path}` |
| Implementer | `e2e-driver` or `{role}` | Change behavior and collect runtime evidence | `{proof packet path}` |
| Reviewer | `code-reviewer` or `{role}` | Review against harness docs and gates | `{review verdict}` |
| Guard | `architecture-guard`, `doc-gardener`, or `{command}` | Check architecture, docs, or configured validation | `{finding list or command output}` |

## Inputs

| Input | Path Or Command | Required |
| --- | --- | --- |
| Context map | `CLAUDE.md` | yes |
| Architecture | `ARCHITECTURE.md` | yes |
| Spec | `docs/product-specs/{spec}.md` | yes |
| Validation | `{command}` | yes |

## Steps

Progression inspired by agent-orchestration workflow patterns. Each step gates the next; do not skip validation or review.

### Step 0: Inspect and route

1. Read the harness config (`docs/harness/config.json`) and the listed inputs before editing.
2. Confirm the operating model and the role that owns final integration.
3. Determine the current state of the target surface and route to the matching flow.

### Step 1: Plan and align

1. Record the intended change and affected surfaces.
2. Write or update acceptance criteria and a validation checklist.
3. Sync with the latest main branch and record the sync result.

### Step 2: Implement

1. Make the smallest coherent change across docs, code, and checks.
2. Update the workpad or plan checklist as work progresses.
3. Do not expand scope; file a separate follow-up issue for out-of-scope improvements.

### Step 3: Validate

1. Run the validation commands listed in this workflow.
2. Check all acceptance criteria and close any gaps.
3. Confirm that temporary proof edits are reverted before commit.

### Step 4: Review and handoff

1. Review the change against harness docs, guardrails, and gates.
2. Write or update the proof packet for the completed run.
3. Update readiness or known-violation records if the change affects harness posture.

## Validation

```bash
{command}
```

## Proof Packet

| Evidence | Path Or Command | Result |
| --- | --- | --- |
| Config validation | `sh scripts/harness/validate_harness.sh` | {pass, warn, or fail} |
| Behavior check | `{command}` | {observed result} |
| Changed surfaces | `{path}` | {summary} |

## Boundaries

- Do not add external service dependencies to complete this workflow.
- Do not add daemon, ticketing, or hosted runtime assumptions unless a separate integration owns them.
- Do not expand the harness config contract from this workflow; update the owning docs or integration pack instead.
````
