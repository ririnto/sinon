---
name: commit-convention
description: Draft or review Conventional Commit messages, including type, scope, breaking changes, and commit cohesion.
metadata:
  reference:
    OpenAI instruction design:
      url: https://developers.openai.com/blog/rethinking-skills-and-prompts-for-gpt-6-astra.md
    OpenAI skills:
      url: https://learn.chatgpt.com/docs/build-skills.md
    Conventional Commits:
      version: 1.0.0
      url: https://www.conventionalcommits.org/en/v1.0.0/
---

# Commit Convention

## Goal

Establish clear, machine-parseable commit messages following the Conventional Commits 1.0.0 specification.
The spec is licensed CC BY 3.0.
The format summary here is an authored condensation, not a verbatim copy.
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
- Keep rationale self-contained, with portable paths and no private environment details or credentials.
  Follow the task's and target repository's requirements for issue references and external links.
- Keep each commit to one logical change when the task and repository workflow support that split.
  - A logical change is a single feature, bug fix, documentation update, or refactor rather than a mix of independent concerns.
- Keep the first line concise, imperative, and free of a trailing period.
  - Follow repository length policy.
    Otherwise prefer 50 characters and keep within 72 when practical.
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
  - Conventional Commits defines `feat` and `fix`.
    Other types follow repository policy.
  - Common additional types are `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, and `revert`.
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
  - Separate the footer token and value with a colon and space, or a space and `#`.
  - Use hyphens inside tokens, except the allowed `BREAKING CHANGE` token.
  - Footer values may span lines until another valid footer starts.

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

Use [commit message examples](./references/message-examples.md) when a concrete template or comparison helps draft or review a subject, body, footer, breaking change, or revert.

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

Return suggested split boundaries and messages.
Do not execute the split from a message-drafting request.

Avoid vague subjects, bodies that merely restate the diff, mixed unrelated changes, and missing breaking-change markers.

## Evidence And Completion

For a staged commit draft, inspect the staged diff and relevant history:

```sh
git -C /path/to/repo status --short
git -C /path/to/repo diff --staged
git -C /path/to/repo log --oneline -n 10
```

If nothing is staged, say so rather than describing unstaged work as the next commit.
A supplied diff can still support a clearly scoped message draft.
Write the message for a future maintainer reading Git history and identify any breaking change or unrelated staged edit.
Do not add runtime tests for commit-message prose or repeat its format as an output checklist.
