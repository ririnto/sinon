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
Use the [sentence-boundary detection limits](plugins/harness/docs/tools/pnpm.md#markdown-sentence-boundaries) when interpreting prose lint diagnostics.

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
Run `claude plugin validate .` from an affected package after changing its packaged content.
Check Markdown links and rendered readability for changed human-facing documents.
Record exact commands, outcomes, and material limitations.
Keep failed and unrun checks distinct from passing evidence.
Reuse passing checks only when relevant behavior, inputs, configuration, and tools remain unchanged.

### Root And Profile Lint Modes

The root `check:react-effects` and `ultracite:check` scripts intentionally run Ultracite without `--type-aware` for the current root configuration.
Installing `oxlint-tsgolint` does not enable type-aware execution in those scripts.
The separate root `check:typescript` script runs `tsc --noEmit`.
Its command-line option does not apply to Ultracite's TypeScript configuration.

Adding `--type-aware` to either root lint command produced exit code 1 with this diagnostic:

```text
typescript(tsconfig-error): Invalid tsconfig
Option 'allowImportingTsExtensions' can only be used when one of 'noEmit', 'emitDeclarationOnly', or 'rewriteRelativeImportExtensions' is set.
```

The root `tsconfig.json` enables `allowImportingTsExtensions` without those emission options.
Both root lint commands passed without the flag in the recorded comparison.
Enabling root type-aware linting requires a separately verified configuration change.

The Harness profile has a separate execution contract in `plugins/harness/tooling/pnpm/package.fragment.json`.
Its Ultracite check and fix scripts explicitly pass `--type-aware`.
Its base `oxlint.config.ts` enables `typescript/no-floating-promises` with `ignoreVoid: false`.
Passing root checks does not establish that profile's type-aware promise diagnostics.

## Commits And Pull Requests

Create a feature branch from the current named base branch.
Keep commits small and reviewable.
Separate each commit title from its body with a blank line.
Explain the reason, main changes, verification, and material limitations in the body.
Write each complete body sentence on its own line and separate paragraphs with blank lines.

Use the [pull request template](.github/pull_request_template.md) for the resulting behavior, scope, verification, and limitations.
Link the issue and use a closing reference only when the change satisfies its acceptance criteria.
Open pull requests for independent review of the complete posted change.
Resolve acceptance blockers and required checks before normal integration.
Verify the named base branch after integration before removing task-owned branches or worktrees.
