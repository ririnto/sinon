# Workflow

`WORKFLOW.md` defines the target repository's delivery lifecycle.
The user-facing session is the root orchestrator described below.

## Root Authority

The user-facing root session MUST own scope, decomposition, integration, completion, and publication decisions.
The root works as an orchestrator: it turns each request into coherent bounded worker tasks, dispatches workers, redirects them when goals or findings change, and synthesizes their results into one integrated outcome.
The root SHOULD keep its foreground work to scoping, routing, integration, review, and reporting, and delegate bulk exploration and bulk implementation to workers.
The workflow MUST NOT require shipped agent profiles, a specific CLI, or vendor-specific model identifiers.

## Autonomy Boundaries

- For a request to answer, explain, review, diagnose, or plan: inspect the relevant material and report the result; do not implement changes.
- For a request to change, build, or fix: make the requested in-scope changes and run relevant non-destructive validation without asking first.
- Require explicit approval for remote mutation, destructive actions, credential access, hook activation, or a material expansion of scope.
- Ask for direction only when an ambiguity would change the requested result; otherwise proceed.

## Planning and Routing

For non-trivial work, the root MUST state the affected scope, material assumptions, verifiable success criteria, and validation approach before implementation.

Model capability and reasoning effort are separate choices, and routing MUST weigh token cost and accuracy together.
Describe models by capability tier, never by vendor identifier:

- fast, low-cost tier: bulk exploration, structure mapping, inventory, and mechanical collection, where a mistake is cheap to detect and correct;
- balanced tier: routine implementation, documentation edits, and moderate analysis;
- highest-accuracy tier: security review, contract-consistency rulings, risky design decisions, architecture, and non-trivial debugging, where a wrong decision is expensive.

Start with the lowest tier and effort that can meet the success criteria, and escalate only on evidence that the result misses the bar.
Never route bulk reading or enumeration to the highest tier, and never route security or contract judgment to the fast tier.

## Delegation and Context

The root SHOULD delegate meaningful independent parts by default: investigation, implementation, documentation, audit, review, and validation each fit a bounded worker.
A trivial single-step change MAY run directly in the root when dispatch overhead exceeds the gain.
Favor parallel workers for work that divides cleanly into independent parts; use concurrent writers only when their benefit exceeds coordination cost.

Each dispatch MUST give the worker a self-contained task:

- a bounded goal, the context the worker needs, hard constraints, and completion criteria;
- expected evidence and validation;
- write authority and exact ownership when writing is allowed;
- a stop condition, and a retry limit for transient failures.

Workers MUST NOT depend on unstated parent context; a dispatch option that shares or forks the parent conversation on one host MUST NOT be assumed on another.
A worker that cannot meet its goal MUST return a structured blocker report - what was attempted, what blocked it, and the owner of the next action - instead of guessing or expanding scope.
Workers MUST preserve unrelated changes.
Worker reports SHOULD lead with the outcome, include the evidence and caveats the root needs, and omit unfiltered logs.
Direct workers MUST remain bounded leaves.
The root MUST verify required authority before dispatch; a worker cannot obtain new approval in a non-interactive run.

## Ownership and Fan-In

Concurrent writers MUST receive disjoint ownership and use isolated workspaces when the host supports them.
The root MUST serialize writes when the host cannot provide isolation.
Do not repeat a completed worker's task; route review fixes and follow-up defects back to the owning writer when practical.

The root MUST wait for every dispatched worker before integrating results or drawing dependent conclusions.
A failed, missing, or blocked worker MUST block fan-in until the root resolves the blocker.

## Validation and Completion

The root MUST run focused validation for changed behavior.
It SHOULD add or update a focused regression when the repository has an appropriate test surface for the behavior.
It SHOULD run broader checks only when repository rules, scope, or integration risk warrants them.

Before completion, the root MUST inspect the integrated diff and review it for defects, regressions, unsafe effects, and missing documentation.
High-risk work SHOULD receive independent review routed to the highest-accuracy tier.
The root MUST resolve review findings and rerun affected checks before handoff.
It MUST report validation results, skipped checks, and blockers with their reasons, then decide whether the evidence satisfies the stated success criteria.

## Remote Effects and Autonomous Work

When the approval required by Autonomy Boundaries is absent, the root MUST prepare local evidence and report the required handoff instead of acting remotely.
Autonomous follow-through applies only to the authorized scope.
It MUST NOT grant authority for unrelated changes, credentials, remote effects, or broader publication decisions.
Use the installed `autonomous-execution` and `issue-mining` skills only within each skill's stated scope and authority.

## Runtime Adaptation

Use the host's native mechanism for worker dispatch, parallel execution, workspace isolation, and model selection, whatever the host calls it.
