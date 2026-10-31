# Contributing

Describe the problem and acceptance criteria in an issue before proposing a change.
Use the bug report or change request template in `.github/ISSUE_TEMPLATE/`.
Keep new requirements in separate issues and link related work.

## Scope And Instructions

Follow repository guidance supplied to the working context and consult component documentation as needed.
Check open issues and pull requests for overlapping work before editing shared files.
Keep one writer per shared resource and preserve unrelated changes.

## Documentation

Write one complete prose sentence per source line.
Separate paragraphs with blank lines and leave spacing around headings, lists, and fences.
Keep independent instructions on separate lines instead of joining them with semicolons or other punctuation.
Preserve technical meaning, permissions, code, metadata, exact quotations, licenses, and valid table syntax.
Exclude generated and vendored material from mechanical prose edits.

Use `.yaml` when the consuming platform supports it.
Preserve required `.yml` names and record the consumer's filename requirement before renaming files.
Update references and validate consumers when changing a filename.
This repository uses Markdown issue templates, so it needs no YAML issue-form filenames.
GitHub requires `.yml` for issue forms if contributors introduce them later.
See [GitHub's template documentation](https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-pull-requests/about-issue-and-pull-request-templates) for that exception.

## Verification

Use the Node.js range and pnpm version declared in `package.json`.
Install locked dependencies with `pnpm install --frozen-lockfile` when the environment needs them.
Run `pnpm run check` for repository-wide validation or the relevant named `check:*` commands for a bounded change.
Use the [verification map](docs/verification.md) to select checks and understand their runtime coverage.
Run `claude plugin validate .` from an affected package after changing its packaged content.
Check Markdown links and rendered readability for changed human-facing documents.
Record exact commands, outcomes, and material limitations.
Keep failed and unrun checks distinct from passing evidence.
Reuse passing checks only when relevant behavior, inputs, configuration, and tools remain unchanged.

## Commits And Pull Requests

Create a feature branch from the current named base branch.
Never push directly to protected branches or the named base branch.
Push the feature branch and open its pull request before independent review.
Keep commits small and reviewable.
Separate each commit title from its body with a blank line.
Explain the reason, main changes, verification, and material limitations in the body.
Write each complete body sentence on its own line and separate paragraphs with blank lines.

Use the [pull request template](.github/pull_request_template.md) for the resulting behavior, scope, verification, and limitations.
Link the issue and use a closing reference only when the change satisfies its acceptance criteria.
Complete independent review of the complete posted change and resolve acceptance blockers and required checks before normal integration.
After the platform records the PR merge, locate the base worktree.
Confirm `git -C /path/to/base-worktree branch --show-current` reports `<base>`.
If no base worktree can be located or verified on `<base>`, including one with detached `HEAD`, stop and report a blocker.
Do not switch another worktree's branch, create a competing worktree, or run the pull from an unverified checkout.
Continue only through an authorized recovery path.
Run `git -C /path/to/base-worktree pull --ff-only <remote> <base>` there before dependent work or cleanup.
If fast-forward synchronization fails, report the blocker without creating a local merge or rewriting history.
Compare the local named base ref, its remote-tracking ref, and the platform's posted PR merge record.
For `main`, compare local `main`, `<remote>/main`, and the recorded merge result.
Require both refs to point to the same commit and the recorded merge result to be an ancestor of that commit.
Account for later base advances rather than requiring the current tips to equal an older merge result.
Resolve readback discrepancies before dependent work or removing task-owned branches or worktrees.
