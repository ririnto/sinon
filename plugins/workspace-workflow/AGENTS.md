# Workspace Workflow Rules

These rules apply to `plugins/workspace-workflow/`.
Normative keywords follow BCP 14.

## Host Selection

Common workflow MUST inspect repository state before loading host-specific guidance:

```sh
git remote -v
git status --short --branch
git branch -vv
```

Resolve the publication host in this order:

1. explicit user choice
2. existing pull or merge request metadata
3. repository policy
4. current branch upstream
5. remote URL as a clue

An upstream or remote URL alone is not proof when multiple hosts are plausible.
If GitHub and GitLab both remain candidates, stop for a focused choice before publication.

## Progressive Disclosure

The common `pr-mr-convention/SKILL.md` MUST contain host-neutral title, body, validation, risk, and template-selection workflow.

Load only one host reference after selection:

- `references/github.md` for GitHub template locations and `gh` commands
- `references/gitlab.md` for GitLab template locations and `glab` commands

Do not preload both CLI catalogs.
Do not check `gh` or `glab` authentication until its host is selected.
Unsupported or local hosts use the repository-approved local review flow without inventing a remote command.

## Agent Boundaries

- `workspace-architect` is a read-only leaf router, not a general orchestrator.
- `commit-message-architect` reads staged state and drafts text without staging or committing.
- `pr-body-architect` reads repository state, loads only the selected host reference, and drafts text without publishing.
- Every agent uses Sonnet/Terra medium and MUST NOT delegate.
- Publication remains with the user-facing top-level session.

## Safety

- Preserve dirty worktrees and unrelated branches.
- Inspect every involved worktree before merge or rebase advice.
- Treat force-push and published-history rewriting as explicit decisions.
- Separate the review target branch from the repository default branch.
- List only validation that actually ran.
- Preserve a repository template exactly when one applies.
- Report stale remote-tracking data and host-managed template uncertainty.

## Validation

Changes to host routing MUST test:

- GitHub-only selection
- GitLab-only selection
- ambiguous multiple remotes
- explicit user override
- no supported host
- loading only the selected reference

Run:

```sh
claude plugin validate plugins/workspace-workflow
bun test scripts/workflow-contract.test.ts
bun run check
```
