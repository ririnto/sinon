---
name: commit-convention
description: >-
  Write Conventional Commits-style commit messages with appropriate type, optional scope, breaking-change markers, and structured body or footer.
---

# Commit Convention

## Goal

Establish clear, machine-parseable commit messages following Conventional Commits specification. A well-formed commit message makes history readable, enables automated tooling (version bumping, changelog generation), and provides future developers with both what changed and why.

## Scope

This skill covers composing and validating commit message text only. It does not cover:

- Staging or unstaging files (use `git add`, `git reset`, or other workflow tools).
- Interactive rebase, squashing, or rewriting history (separate workflow skill).
- Merge conflict resolution.
- Pushing commits to remote.

## Operating Rules

- Every commit MUST contain exactly one logical change. A logical change is a single feature, bug fix, documentation update, or refactor—not a mix of independent concerns.
- The first line (subject) MUST be concise, imperative mood, no trailing period. SHOULD be 50 characters or fewer; MUST NOT exceed 72 characters.
- If a commit has a body, it MUST begin with a blank line after the subject.
- Body lines SHOULD wrap at 72 characters to ensure readability in terminal and email contexts.
- Commit messages SHOULD be written in English by default, or in the language of the project documentation and team communication (per `CLAUDE.md`). Consistency within a single commit MUST be maintained.
- Code identifiers, CLI tool names, and file paths MUST retain their original form regardless of the surrounding language.
- The commit type, scope, and breaking-change marker MUST follow the Conventional Commits format specification.

## Conventional Commits Format

A Conventional Commit has the structure:

```
<type>(<scope>)?(!)?: <subject>

<body>

<footers>
```

### Format Tokens

- `type` (required): Semantic category. One of: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`.
- `scope` (optional): Affected module, package, or component. Use kebab-case (e.g., `auth`, `api-client`). Omit if it adds noise or affects many areas.
- `!` (optional): Marks a breaking change (incompatible API or behavior modification). Goes before the colon. Can appear with or without a scope.
- `subject` (required): Summary in imperative mood. No trailing period. Lowercase preferred.
- `body` (optional but recommended for non-trivial changes): Explains context, motivation, or design decisions. The diff explains what; the body explains why.
- `footers` (optional): Issue references, co-authors, or breaking-change details. Format: `Token: value` on separate lines.

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

```
fix(auth): resolve session token expiration on logout

Previously, invalidating a session did not clear the token from
storage, allowing re-use after logout. Now clearing storage happens
atomically with session invalidation.
```

### Feature with Scope

```
feat(api): add pagination support to user list endpoint

Users requested the ability to fetch large user lists in batches.
Endpoint now accepts ?page=<n>&size=<n> parameters. Default page
size is 20.
```

### Breaking Change Marker

```
feat(auth)!: require OAuth2 for all API endpoints

BREAKING CHANGE: Basic auth is no longer supported. All clients
must migrate to OAuth2 bearer tokens. See migration guide in
docs/migration-oauth2.md.
```

### Multiple Concerns in Body

```
refactor(parser): split lexer and parser modules

This change improves maintainability by separating concerns:

- Lexer now handles tokenization only
- Parser handles AST construction
- Error recovery is clearer in each module

No behavior change. Existing tests pass.
```

### Fix Without Scope (When Scope Adds Noise)

```
fix: handle edge case in date parsing for year 9999

Year 9999 was incorrectly parsed as a 2-digit number in some
locales. Root cause was missing century boundary check.
```

### Revert

```
revert: undo refactoring of email service (commit abc1234)

The async refactor introduced subtle timing bugs in test suite.
Reverting to stable version pending investigation.
```

## Choosing the Right Type

Ask these questions in order:

1. **Does the code behavior change?**
   - If no: `docs`, `style`, `refactor`, `test`, `build`, `ci`, or `chore`.
   - If yes: `feat`, `fix`, or `perf`.

2. **If behavior changed, is it an improvement or a correction?**
   - Improvement (new capability): `feat`.
   - Correction (removes error): `fix`.
   - Speed/memory (no new capability): `perf`.

3. **If no behavior change, what kind of change is it?**
   - Documentation only: `docs`.
   - Formatting or style (spaces, quotes, indentation): `style`.
   - Restructuring code without changing behavior: `refactor`.
   - Adding/modifying tests only: `test`.
   - Dependencies, build config: `build`.
   - CI/CD, automation: `ci`.
   - Cleanup, version bumps, other maintenance: `chore`.

4. **Is this reverting a previous commit?**
   - Use `revert`.

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
No behavior change; all tests pass.
```

## When to Split a Commit

Split a change into multiple commits when the staged changes contain **more than one independent concern**. Independent concerns are changes that:

- Fix different bugs.
- Implement separate features.
- Update documentation unrelated to code changes.
- Include cleanup or refactoring alongside a feature (the cleanup should be separate).

### Signals to Split

- Commit message reads "and" (e.g., "add user login and fix sidebar layout").
- `git diff --staged` shows changes in unrelated modules or files.
- Changes serve different purposes (feature + cosmetic cleanup + test refactor).
- One concern is critical; another is optional for release.

Example: a developer stages both a bug fix to `auth.js` and a style update to `form.css`. These SHOULD be split into two commits.

```sh
# Suggested split:
git reset
git add auth.js
git commit -m "fix(auth): resolve token expiration bug"
git add form.css
git commit -m "style: align button spacing in form"
```

## Pitfalls

Vague subjects without verbs:

```
# Wrong
fix: stuff
chore: updates
docs: changes
```

```
# Correct
fix: handle null pointer in user lookup
chore: upgrade lodash to 4.17.21
docs: add setup instructions for macOS
```

Body restates the diff instead of explaining why:

```
# Wrong
fix: update login handler

Modified login.js to check token expiry. Also updated session.js
to clear storage.
```

```
# Correct
fix: resolve session token expiration on logout

When a user logs out, the token must be cleared from storage
immediately to prevent reuse in a hijacking scenario.
```

Mixing unrelated changes:

```
# Wrong
feat: add two-factor auth and update dependencies

Two independent concerns forced into one message. Should be split.
```

Breaking change missing the `!` marker:

```
# Wrong
feat(api): remove deprecated endpoint

User will miss the breaking-change signal if checking git log or
running tooling.
```

```
# Correct
feat(api)!: remove deprecated endpoint

BREAKING CHANGE: /api/v1/users endpoint has been removed. Use
/api/v2/users instead.
```

## First Safe Commands

Inspect the repository state before authoring:

```sh
git status
git diff --staged
git log --oneline -n 10
```

Write a commit message:

```sh
git commit -m "feat(scope): subject line"
```

With a body, use an editor:

```sh
git commit -v
```

The `-v` flag shows `git diff --cached` in the editor for reference while you type.

If staged changes include multiple concerns, unstage and split:

```sh
git reset
git add <file1>
git commit -m "type(scope): message for file1"
git add <file2>
git commit -m "type(scope): message for file2"
```

## Output Contract

A commit message MUST be:

- **Valid Conventional Commit format**: type, optional scope, subject.
- **Imperative mood**: verbs are commands ("add", "fix", "remove"), not past tense.
- **Concise subject**: 50 chars or fewer preferred, max 72.
- **No trailing period** on subject.
- **Blank line** between subject and body (if body exists).
- **Wrapped body** at 72 characters for readability.
- **Single logical unit**: one feature, fix, refactor, or doc update per commit.
- **Language consistent**: English by default; if translated, entire message MUST be consistent; code identifiers retain original form.
