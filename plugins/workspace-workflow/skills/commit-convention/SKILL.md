---
name: commit-convention
description: Draft or review Conventional Commit messages, including type, scope, breaking changes, and commit cohesion.
---

# Commit Convention

## Goal

Establish clear, machine-parseable commit messages following the Conventional Commits 1.0.0 specification, read on 2026-09-13: [conventionalcommits.org](https://www.conventionalcommits.org/en/v1.0.0/).
The spec is licensed CC BY 3.0; the format summary here is an authored condensation, not a verbatim copy.
A well-formed commit message makes history readable, enables automated tooling (version bumping, changelog generation), and provides future developers with both what changed and why.

## Scope

This skill covers composing and validating commit message text only.
It does not cover:

- Staging or unstaging files.
- Interactive rebase, squashing, or rewriting history (separate workflow skill).
- Merge conflict resolution.
- Pushing commits to remote.

## Operating Rules

- Draft from the requested diff or supplied evidence.
  Inspect staged changes when composing the next commit and history when repository conventions are unclear.
- Message drafting does not authorize staging, commits, history edits, or publication.
- Keep rationale self-contained, with portable paths and no private environment details, external work-item identifiers, or review URLs.
- Keep each commit to one logical change when the task and repository workflow support that split.
  - A logical change is a single feature, bug fix, documentation update, or refactor rather than a mix of independent concerns.
- Keep the first line concise, imperative, and free of a trailing period.
  - Follow repository length policy; otherwise prefer 50 characters and keep within 72 when practical.
- If a commit has a body, it MUST begin with a blank line after the subject.
- Body lines SHOULD wrap at 72 characters to ensure readability in terminal and email contexts.
- Commit messages SHOULD be written in English by default, or in the language specified by project documentation and team communication.
  - Consistency within a single commit MUST be maintained.
- Code identifiers, CLI tool names, and file paths MUST retain their original form regardless of the surrounding language.
- The commit type, scope, and breaking-change marker MUST follow the Conventional Commits format specification.

## Conventional Commits Format

A Conventional Commit has the structure:

```text
<type>(<scope>)?(!)?: <subject>

<body>

<footers>
```

### Format Tokens

- `type` (required): Semantic category.
  - One of: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`.
- `scope` (optional): Affected module, package, or component.
  - Use kebab-case (e.g., `auth`, `api-client`).
  - Omit if it adds noise or affects many areas.
- `!` (optional): Marks a breaking change (incompatible API or behavior modification).
  - Goes before the colon.
  - Can appear with or without a scope.
- `subject` (required): Summary in imperative mood.
  - No trailing period.
  - Lowercase preferred.
- `body` (optional but recommended for non-trivial changes): Explains context, motivation, or design decisions.
- `footers` (optional): Co-authors or breaking-change details.
  - Format: `Token: value` on separate lines.

### Type Reference

| Type | When to use |
| --- | --- |
| `feat` | New feature or capability |
| `fix` | Bug fix |
| `docs` | Documentation changes only (no code changes) |
| `style` | Code formatting, whitespace, semicolons (no logic change) |
| `refactor` | Code restructure without changing behavior or adding features |
| `perf` | Performance improvement |
| `test` | Test additions or modifications (no feature/fix) |
| `build` | Build system, dependencies, tooling (e.g., npm, webpack, docker) |
| `ci` | CI/CD configuration or scripts (e.g., GitHub Actions, GitLab CI) |
| `chore` | Maintenance, cleanup, version bumps (no feature/fix/docs impact) |
| `revert` | Revert a previous commit |

## Templates and Examples

### Simple Bug Fix

```text
fix(auth): resolve session token expiration on logout

Previously, invalidating a session did not clear the token from
storage, allowing re-use after logout. Now clearing storage happens
atomically with session invalidation.
```

### Feature with Scope

```text
feat(api): add pagination support to user list endpoint

Users requested the ability to fetch large user lists in batches.
Endpoint now accepts ?page=<n>&size=<n> parameters. Default page
size is 20.
```

### Breaking Change Marker

```text
feat(auth)!: require OAuth2 for all API endpoints

BREAKING CHANGE: Basic auth is no longer supported. All clients
must migrate to OAuth2 bearer tokens. See migration guide in
docs/migration-oauth2.md.
```

### Multiple Concerns in Body

```text
refactor(parser): split lexer and parser modules

This change improves maintainability by separating concerns:

- Lexer now handles tokenization only
- Parser handles AST construction
- Error recovery is clearer in each module

No behavior change. Existing tests pass.
```

### Fix Without Scope (When Scope Adds Noise)

```text
fix: handle edge case in date parsing for year 9999

Year 9999 was incorrectly parsed as a 2-digit number in some
locales. Root cause was missing century boundary check.
```

### Revert

```text
revert: undo refactoring of email service (commit abc1234)

The async refactor introduced subtle timing bugs in test suite.
Reverting to stable version pending investigation.
```

## Choosing a Scope

Scope is optional and SHOULD be used only when it adds clarity.

- Use a scope when the change affects a clear, named module or subsystem (e.g., `auth`, `api-client`, `database`).
- Use kebab-case for multi-word scopes (e.g., `email-service`, `user-auth`).
- Omit scope when:
  - The change affects many modules (scope becomes too broad or vague).
  - The repository has no clear module structure.
  - Scope adds noise without improving clarity.

Example: if a refactor touches the entire codebase, omit scope and explain in the body instead.

```text
refactor: modernize async patterns throughout codebase

Migrated all Promise-based patterns to async/await for consistency.
No behavior changed.
All tests pass.
```

## When to Split a Commit

Split a change into multiple commits when the staged changes contain more than one independent concern.
Independent concerns are changes that:

- Fix different bugs.
- Implement separate features.
- Update documentation unrelated to code changes.
- Include unrelated cleanup or refactoring alongside a feature.
  Keep changes needed for one complete behavior together.

### Signals to Split

- Commit message reads "and" (e.g., "add user login and fix sidebar layout").
- `git diff --staged` shows changes in unrelated modules or files.
- Changes serve different purposes (feature + cosmetic cleanup + test refactor).
- One concern is required for release, while another is optional.

Example: a developer stages both a bug fix to `auth.js` and a style update to `form.css`.
These SHOULD be split into two commits.

Return suggested split boundaries and messages; do not execute the split from a message-drafting request.

## Pitfalls

Vague subjects without verbs:

```text
# Wrong
fix: stuff
chore: updates
docs: changes
```

```text
# Correct
fix: handle null pointer in user lookup
chore: upgrade lodash to 4.17.21
docs: add setup instructions for macOS
```

Body restates the diff instead of explaining why:

```text
# Wrong
fix: update login handler

Modified login.js to check token expiry. Also updated session.js
to clear storage.
```

```text
# Correct
fix: resolve session token expiration on logout

When a user logs out, the token must be cleared from storage
immediately to prevent reuse in a hijacking scenario.
```

Mixing unrelated changes:

```text
# Wrong
feat: add two-factor auth and update dependencies

Two independent concerns forced into one message. Should be split.
```

Breaking change missing the `!` marker:

```text
# Wrong
feat(api): remove deprecated endpoint

Git log hides the breaking-change signal.
Release tooling may miss it.
```

```text
# Correct
feat(api)!: remove deprecated endpoint

BREAKING CHANGE: /api/v1/users endpoint has been removed. Use
/api/v2/users instead.
```

## Evidence And Completion

For a staged commit draft, inspect the staged diff and relevant history:

```sh
git -C /path/to/repo status --short
git -C /path/to/repo diff --staged
git -C /path/to/repo log --oneline -n 10
```

If nothing is staged, say so rather than describing unstaged work as the next commit.
A supplied diff can still support a clearly scoped message draft.
Return the message and material cohesion or breaking-change concerns.
Do not add runtime tests for commit-message prose or repeat its format as an output checklist.
