# Workflow

`WORKFLOW.md` is the operational playbook for this repository's GitLab review flow.

## Role

- Orchestrate GitLab-backed repository work from intake through publication.
- Choose direct execution or scoped subagent delegation for each phase.
- Integrate subagent results, validation evidence, review findings, and merge request state.
- Publish issues and merge requests through `glab`.

## Subagent Use

Use subagents as the normal tool for bounded exploration, implementation, and review when the work needs isolated context or independent judgment.
The orchestrator owns workflow selection, agent type selection, model class selection, prompt scope, fan-in, and final decisions.

Repository subagents:

- `implementation`: implements bounded changes with clear acceptance criteria.
- `review`: reviews changes and validation evidence for risks and contract drift.

Choose the narrowest agent type that can complete the assignment.

| Need | Agent type |
| --- | --- |
| Read-only search, issue duplicate checks, dependency scan | explorer or read-only research agent |
| Bounded code or documentation change | `implementation` |
| Independent quality, risk, validation, or contract review | `review` |
| GitLab issue or merge request publication | main orchestrator |

Choose the lightest model class that can complete the assignment.
If a model appears in more than one class, use the lower class for mechanical work and the higher class only when task risk requires stronger reasoning.

| Difficulty | Model class |
| --- | --- |
| Narrow lookup, mechanical formatting, short evidence collection | Haiku-class: Haiku, GPT-5.4-nano, or GPT-5.4-mini |
| Routine implementation, validation triage, issue-mining synthesis | Sonnet-class: Sonnet, GPT-5.4, GPT-5.4-mini, or GPT-5.3-codex |
| Ambiguous architecture, security-sensitive work, broad autonomous planning, final high-risk review | Opus-class: Opus or GPT-5.5 |

Include the inputs the assignment needs:

- scope
- acceptance criteria or question to answer
- workflow decisions that affect the assigned scope
- validation command or blocker, when validation is part of the assignment
- publication or completion target, when the assignment owns that record
- expected output fields

Add context paths only when the assignment has known files, directories, records, or commands.
Pass only the branch, validation, review, and publication decisions needed for the assignment.
Integrate returned changes or findings through the active orchestration workflow.
Wait for delegated results before making dependent decisions.

## Orchestration Workflow

| Phase | Action |
| --- | --- |
| Intake | Confirm the issue, plan, merge request, or user request that owns the work. |
| Explore | Assign or perform architecture, docs, code, validation, and GitLab-context exploration. |
| Plan | Define subagent scopes, changed files, acceptance criteria, validation, manual QA, and merge request target. |
| Implement | Assign or perform the change set needed to satisfy the criteria and preserve the contracts that cover changed files. |
| Review | Assign independent review for correctness, security, contract drift, and missing evidence when the change is non-trivial. |
| Validate | Run the active validation command and active hooks after integrating subagent output. |
| Publish | Create or update the GitLab issue or merge request that owns the work. |

## Issue Mining

Use the `issue-mining` skill when the user asks to investigate a specific GitLab issue, duplicate or related reports, likely code cause, or open-ended issue candidates.
Issue mining ends with a report or requested GitLab issue registration.
It does not implement fixes.
The orchestrator may assign internal or external exploration and then integrates findings before reporting or registration.

## Evidence

Record these items before publishing:

- issue source or mining rationale
- validation command and result
- test names or CI job names
- manual QA action and observed output
- review findings or approval record
- unresolved blockers and owner

## Git and Records

Branch names use `<type>/<short-description>`.

Use built-in worktree tooling if the runtime provides it.
Fallback to Git worktrees from the repository-approved base ref.
Run `git fetch <remote>` first when `<base-ref>` is remote.

```sh
git worktree add <worktree-path> -b <type>/<short-description> <base-ref>
```

Draft issue and merge request bodies in `.tmp/`.
Pass draft files as API descriptions.
When the repository operates GitLab milestones and the work belongs to one, add `--field milestone_id="<milestone-id>"`.

```sh
mkdir -p .tmp
$EDITOR .tmp/issue.md
glab api --method POST projects/:fullpath/issues --field title="<title>" --field description=@.tmp/issue.md
$EDITOR .tmp/review.md
glab api --method POST projects/:fullpath/merge_requests --field title="Draft: <title>" --field source_branch="$(git branch --show-current)" --field target_branch=main --field description=@.tmp/review.md
glab mr merge <mr-iid> --squash --yes
```

Use `glab api --method PUT` when the record already exists.

## Autonomous Execution Loop

Use the `autonomous-execution` skill only when the user explicitly asks for autonomous follow-through beyond one scoped work item.
The skill uses this workflow's GitLab policy, subagent rules, model-class rules, evidence requirements, and merge request path.
The orchestrator may process non-overlapping issues in separate worktrees when their files, contracts, validation surfaces, and merge request targets do not conflict.
