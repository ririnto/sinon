# Workflow

`WORKFLOW.md` is the host-neutral operational policy.
The installer copies `WORKFLOW.github.md`, `WORKFLOW.gitlab.md`, and `WORKFLOW.none.md` as separate addenda.
Each addendum owns only its namespaced authentication, record, and CLI details.

```workflow-policy
{
  "schemaVersion": 1,
  "root": {
    "backgroundCliFallback": {
      "admission": "native-delegation-unavailable",
      "authority": "root-only",
      "leafLimit": "one-bounded-leaf",
      "leafProhibitions": ["no-delegation", "no-integration", "no-commit", "no-publication"],
      "record": "recorded",
      "unavailableHandoff": "eligible-root-or-human"
    },
    "opusSol": {
      "directExploration": "bounded-read-only",
      "requiredFor": ["unresolved-ambiguity", "security-sensitive-decision", "contradictory-fan-in", "high-risk-publication"],
      "delegates": ["implementation", "validation", "review"]
    },
    "terra": {
      "requires": ["bounded-work", "executable-plan"],
      "delegates": ["exploration", "implementation", "validation", "review"]
    },
    "unavailable": {
      "requiredCapability": "opus-sol-medium",
      "handoffOwner": "eligible-root-or-human"
    }
  },
  "failure": {
    "discardRetryRequires": ["discarded", "changed-remediation", "fresh-replacement-identity"],
    "failedCycles": "prior-retryable-failures",
    "phases": ["exploration", "implementation", "review", "validation"],
    "retryBudget": "two-failed-cycles"
  },
  "validation": {
    "executor": "validation-executor",
    "phases": ["focused", "integrated"]
  },
  "review": {
    "lowDispositions": ["accepted", "deferred"],
    "sourceFixRequires": "fresh-rereview"
  },
  "completion": {
    "requires": ["fan-in", "review-clearance", "focused-validation", "integrated-validation", "required-evidence", "selected-record-updated", "root-publication-authority"],
    "blockedReport": "blocked-report"
  },
  "documentEdits": {
    "requires": ["stop-slop", "semantic-line-breaks"],
    "manualFallback": "recorded-manual-fallback"
  },
  "records": {
    "addenda": "install-all",
    "selection": "per-work-item"
  }
}
```

## Root Authority

The user-facing root session is the sole authority for decomposition, routing, branch and worktree decisions, fan-in, review disposition, validation disposition, and publication or completion.
Installed agents are bounded leaves; do not create a packaged `project-orchestrator` or explorer.

For bounded work with an executable recorded plan, a Sonnet/Terra-equivalent root with medium effort MAY orchestrate.
The plan records acceptance criteria, ownership, isolation, validation, review, evidence, and completion.
That root MUST delegate exploration, implementation, validation, and independent review.
An Opus/Sol-equivalent root with medium effort MAY perform bounded read-only exploration directly for ordinary work.
It delegates implementation, validation, and independent review.
Require Opus/Sol before resolving unresolved ambiguity, security-sensitive decisions, contradictory fan-in evidence, or high-risk publication decisions.
If Opus/Sol is unavailable, gather permitted evidence, block the decision, and name the eligible root or human owner.

## Routing and Classification

Classify each work item before writer dispatch:

- An Opus/Sol-equivalent root MAY run bounded read-only exploration in the root session.
  An admitted Sonnet/Terra-equivalent root MUST dispatch a caller-provided bounded read-only worker.
  The request must state one explicit question, a bounded scope, read-only tools, and an evidence destination.
  The worker reports evidence only and has no routing authority.
- Do not refer to a packaged explorer.
- Use `scoped-implementer` only for an exhaustive supplied ownership list, specified behavior, and requested validation phases.
- Use `implementer` when the affected set requires discovery or work crosses files, modules, layers, contracts, or integrated validation.
- Use `reviewer` for independent read-only assessment of an exact diff or revisions, or an explicitly bounded audit surface, and evidence.
- The root dispatches separate fresh `validation-executor` leaves for focused and integrated phases.
  Supply each executor with its phase, target working directory, exact command list, acceptance context, and evidence destination.

Material ambiguity blocks writer dispatch.
Never send an ambiguous or incomplete file set to `scoped-implementer`.
Use Haiku/Luna low for scoped implementation and exact validation execution.
Use Sonnet/Terra medium for implementer or reviewer.
These are recommendations; installed leaf metadata supplies the runtime selection.

## Verifiable Plan

Before dispatch, record the acceptance criteria, affected behavior and known paths, read-only or writer classification, owner, base commit, branch/worktree decision, focused and integrated validation, review scope, evidence location, and publication or completion record.
Every leaf prompt must carry the applicable decisions, explicit scope, constraints, validation, and output contract.

The root provisions each writer worktree and binds the child working directory to that worktree.
When binding is not guaranteed, serialize writers.
Discovery-capable scopes are presumed overlapping until bounded read-only preflight proves them disjoint.
Serialize overlapping writers or use read-only exploration before dispatch.

When a dispatch API exposes `fork_turns`, set `fork_turns: "none"` for each new leaf.
A prompt must stand alone; this does not apply to background CLI commands that do not expose that setting.

## Background CLI Fallback

Use a background CLI fallback only when native delegation cannot provide the required capability.
The root MAY dispatch one bounded leaf and retains sole authority for routing, integration, commits, publication, and completion.
Record the requested model and effort, prompt, worktree, ownership, job or session identifier, output, and exit status.
The fallback leaf MUST NOT delegate, integrate results, commit, or publish.
It returns evidence to the root.
If the CLI cannot provide the required capability, block and hand off to an eligible root or human.

## Branch and Worktree Isolation

A single task kept as one unsplit writer assignment MAY create and switch to a new branch in the current checkout without a worktree only when it has one owner, one acceptance-criteria set, one integration unit, and no concurrent writer.
Once work is split across tasks or writers, every writer MUST use a disjoint worktree.
Independently reviewable or publishable outcomes MUST be split even when one user request contains them.
Overlapping writer scopes are serialized under one owner.
Independent read-only workers may share a checkout.
Workers do not commit, push, publish, or edit another writer's branch.

## Fan-in and Review

Wait for every requested result and reconcile contradictions before integration.
A missing, failed, or contradictory result blocks fan-in.
A failed exploration, implementation, review, or validation leaf clears only when the root records discarded disposition, changed remediation, and a passed replacement.
The root submits a historical identity ledger without the current workers and leaves under review.
It trims each current identity, rejects blank or duplicate current worker identities, and checks failed and replacement identities against the historical and current records.
The replacement identity MUST be nonblank and globally fresh in the complete work-item identity ledger.
Invalid recovery blocks fan-in and successful completion.

Assign an independent `reviewer` leaf with requirements, plan, applicable instructions, an exact diff or revisions or an explicitly bounded audit surface, and validation evidence.
The reviewer leaf reports every finding.
Correctness, security, behavior, and contract findings block.
The root may accept or defer only a genuinely non-blocking low-severity finding with an owner, rationale, and durable record.
Return every confirmed finding to its owning writer.
After a fix, require a fresh independent `reviewer` leaf over the same scope.
No blocking finding remains unresolved.

## Validation and Evidence

The root assigns `validation-executor` leaves to focused validation for changed behavior and integrated validation for the resulting repository state.
It collects every result, waits for full fan-in, and owns the final validation disposition.
Worker-branch validation does not replace integrated validation.
Apply the failed-leaf recovery lifecycle to validation executors.
Focused and integrated validation remain separate required phases.
Record acceptance criteria, worker results, focused and integrated command results, review findings, fixes, fresh re-review, manual QA, unresolved blockers, and the selected publication or completion record.

## Canonical Workflow Completion Gate

The root session may publish or report successful completion only when all planned work has fanned in, review clearance is present, focused and integrated validation pass, required evidence is present, the selected record is updated, and the root holds publication authority.
A recorded validation blocker produces a blocked report.
It does not produce successful completion.
Missing evidence, unresolved blocking findings, failed validation, missing re-review after a fix, an unrecorded low-severity disposition, or an incomplete record blocks success.

## Host Record Selection

Install all host addenda.
For each work item, the base policy selects the applicable addendum and exactly one record host from explicit user direction, existing record metadata, repository policy, upstream, then remote URL.
Select one primary host by default.
Use linked GitHub and GitLab records only when user direction or repository policy requires both, and open only the needed host addenda.
Ask when GitHub and GitLab are both plausible.
Open only the addendum or addenda for the selected record host or hosts.
`none` selects the local addendum.

## Document Edits

Use `/stop-slop` for every Markdown and TOML edit.
Keep semantic line breaks in edited prose.
When `/stop-slop` is unavailable, use this manual fallback:

1. Remove filler, adverbs, and em dashes.
2. Use active subjects.
3. Preserve semantic line breaks.
4. Record the fallback evidence without claiming that `/stop-slop` ran.

## Autonomous Execution

Use `autonomous-execution` only after explicit user authorization for continued follow-through beyond one scoped item.
The skill owns authorization and loop mechanics; this workflow remains the authority for every candidate's completion gate.

Use `issue-mining` for investigation and record preparation only.
It does not implement fixes.
