---
name: git-change-publication
description: >-
  Use this skill when the user asks to decide whether changes are ready for one commit,
  draft a Conventional Commit message from the real diff, prepare a pull request or
  merge request body, or preserve GitHub/GitLab repository templates while publishing
  a change.
---

# Git Change Publication

## Overview

Use this skill to turn real repository state into a publishable change narrative. The common case is inspecting the working tree, deciding whether the changes are cohesive enough for one commit, drafting a Conventional Commit message from the actual diff, preparing hosted review text, and then branching into GitHub or GitLab-specific template depth only when the repository host makes that necessary.

## Use This Skill When

- You need to decide whether a change set is ready for one commit or should be split first.
- You need to draft a Conventional Commit message from staged changes.
- You need to choose a commit type or optional scope that matches the actual change intent.
- You need to prepare hosted review body text that preserves an existing repository template or falls back safely when no template exists.
- You need to branch into GitHub or GitLab-specific template handling without duplicating the shared commit workflow.
- Do not use this skill when the main issue is rewriting Git history, resolving merge conflicts, or teaching low-level Git fundamentals for beginners.

## Common-Case Workflow

1. Inspect `git status`, staged changes, unstaged changes, and recent commit history before proposing commit or review text.
2. Check whether there are staged changes, unstaged-only changes, or a staged-and-unstaged mismatch before drafting final commit text.
3. Decide whether the change set is one logical unit or whether mixed-purpose changes should be split.
4. Map the actual intent to a Conventional Commit type and optional scope.
5. Draft an imperative subject line and a short body that explains why the change exists.
6. Detect whether the repository host is GitHub or GitLab when exact template mechanics matter, or stay host-neutral when the host is unknown.
7. Preserve an existing repository template when present, or use a stable fallback review body when none exists.
8. Validate that the final commit and hosted-review text match the actual diff rather than aspirations or future work.

## Decision Branches

- No staged changes:
  - If the user asked for a commit message, stop short of a final commit draft and say the repository has no staged changes yet.
  - If only unstaged changes exist, describe the likely commit direction as provisional until the staged diff exists.
- Staged and unstaged changes differ:
  - Draft the commit message from `git diff --cached`, not from the full working tree.
  - Call out that unstaged changes are excluded from the proposed commit and may belong in a follow-up commit.
- One commit vs split commits:
  - Use one commit only when the staged diff serves one clear purpose.
  - Split when the diff mixes feature work, unrelated cleanup, test-only changes with separate intent, or distinct rollback units.
- Conventional Commit type and scope:
  - Choose the type from the user-visible or maintenance effect, not from file extensions alone.
  - Add a scope only when it clarifies the affected surface such as `auth`, `api`, or `docs`; omit it when it adds noise.
- Host known vs unknown:
  - When the repository host is known and exact template preservation matters, use the matching host reference for mechanics.
  - When the host is unknown or template paths are irrelevant, keep the review body host-neutral in `SKILL.md`.
- Template present vs no template:
  - Preserve the existing headings, order, and required sections when a repository template exists.
  - Use the fallback structure in this file only when no repository template exists.
  - If a host-specific template exists but the exact selected template cannot be confirmed, report the template as unconfirmed instead of silently falling back.
- Validation claims:
  - Report only checks that already ran.
  - If validation is pending or unknown, say that explicitly instead of implying success.

## User Interaction Protocol

| Situation | Autonomous decision | Must ask user |
| --- | --- | --- |
| Choosing commit type from diff content | Yes | No |
| Choosing scope from file paths | Yes (if clear); No (if ambiguous) | Yes (if ambiguous) |
| Split vs. one-commit decision | Propose + flag | Yes (final call is user's) |
| Subject wording | Draft | Yes (user approves final wording) |
| Which named template to use | Detect from context | Yes (if ambiguous or multiple exist) |
| Whether to proceed with commit | No | Always ask before executing |
| Validation claims | Report actual state | No |

Phrasing templates for questions to the user:

- Split proposal: "The staged diff contains [X] concerns: [list]. Recommend splitting into [N] commits: [outline]. Proceed as one commit or split?"
- Template ambiguity: "Found [N] named templates: [list]. Which template should the PR/MR body follow?"
- Staged mismatch: "`git diff --cached` shows [summary] but `git diff` shows additional [summary]. Draft commit from staged changes only?"

Rule: NEVER execute `git commit` autonomously; always present the draft and wait for user confirmation.

## Inspection Protocol

Inspect the repository state before choosing wording:

```bash
git status
git diff --cached
git diff
git log -5 --oneline
```

### Output Interpretation

`git status` output signals:

| Output pattern | Meaning | Next action |
| --- | --- | --- |
| `nothing to commit, working tree clean` | No changes at all | Stop; nothing to publish |
| `Changes not staged for commit:` only | Unstaged changes exist | Recommend staging first; any draft is provisional |
| `Changes to be committed:` only | Clean staging area | Proceed with commit drafting |
| Both sections present | Staged/unstaged mismatch | Draft from `--cached` only; call out unstaged portion |
| `Untracked files:` present | New files not yet tracked | Include in staging recommendation if relevant |

`git diff --cached` output signals:

| Output pattern | Meaning | Next action |
| --- | --- | --- |
| Empty (no output) | No staged changes | Do not present final commit as ready-to-run |
| Shows file list with stats | Staged changes exist | Use as sole source for commit message |
| Contains `Binary files differ` | Binary files changed | Note in summary; do not describe binary content |

`git diff` output signals:

| Output pattern | Meaning | Next action |
| --- | --- | --- |
| Same files as `--cached` | Working tree matches staging | Normal single-commit path |
| Different or additional files | Unstaged drift exists | Exclude from commit draft; flag to user |

`git log -5 --oneline` output signals:

| Pattern | Meaning | Usage |
| --- | --- | --- |
| Consistent type/scope pattern (e.g., `feat(auth): ...`) | Repository has established convention | Match the established style |
| Mixed conventions or no pattern | No established style | Apply Conventional Commits cleanly; do not invent repo-specific patterns |
| Empty (no commits yet) | Initial repository | Skip history check; base type/scope on files alone |

Use when: you need to anchor commit or hosted-review text to the real repository state before writing anything.

## Host Detection

Detect the repository host before branching into host-specific references:

```bash
git remote -v
ls -d .github 2>/dev/null && echo "HAS_GITHUB_DIR"
ls -d .gitlab 2>/dev/null && echo "HAS_GITLAB_DIR"
```

### Detection Rules

| Signal | Host determination | Action |
| --- | --- | --- |
| Remote URL contains `github.com` | GitHub | Open `./references/github-pull-request-templates.md` for template mechanics |
| Remote URL contains `gitlab.com` or self-hosted GitLab domain | GitLab | Open `./references/gitlab-merge-request-templates.md` for template mechanics |
| `.github/` directory exists but no GitHub remote | Likely GitHub | Treat as GitHub for template purposes; note remote not confirmed |
| `.gitlab/` directory exists but no GitLab remote | Likely GitLab | Treat as GitLab for template purposes; note remote not confirmed |
| Neither signal detected | Unknown | Stay host-neutral; use fallback review body from this file; do not open either reference |

Example detection outcomes:

```text
# GitHub repository
origin  https://github.com/org/repo.git (fetch)
-> Host: GitHub

# Self-hosted GitLab
origin  https://gitlab.example.com/group/project.git (fetch)
-> Host: GitLab (self-hosted)

# Local-only repository, but .github/ directory exists
(no remotes)
HAS_GITHUB_DIR
-> Host: likely GitHub (template presence is strong signal)

# No signals at all
(no remotes, no .github/, no .gitlab/)
-> Host: unknown — stay neutral
```

Rule: when host is unknown, use the fallback review body from SKILL.md and do not open either reference.

## Commit Drafting Core

If `git diff --cached` is empty, do not present a final commit message as ready-to-run. Say that no staged diff exists yet, and keep any wording provisional until the intended changes are staged.

If `git diff --cached` and `git diff` show different concerns, write the commit message from the staged diff only and note that the unstaged portion is out of scope for this commit.

Single logical change ready for one commit:

```bash
git status
git diff --cached
git commit -m "fix(auth): reject expired refresh tokens" -m "- Prevent expired refresh tokens from creating new sessions.
- Keep token rejection behavior aligned with the API contract."
```

### Default Shape

```text
<type>[optional scope]: <description>   # description is REQUIRED

- Explain the user-visible or maintenance reason for the change.
- Note the main constraint, compatibility point, or safety boundary.
```

A scope MUST be a noun describing a section of the codebase, contained within parentheses (e.g., `fix(parser):`).

The type MUST be a noun. Use `feat`, `fix`, `docs`, `style`, `test`, `refactor`, `perf`, `build`, `ci`, `chore`, or `revert`, and prefer `feat` or `fix` whenever published behavior changes. Use `revert` only with the dedicated revert format documented below; it is not interchangeable with the other types. If the diff mixes unrelated feature work, broad cleanup, or separate test concerns, split the commit before drafting final text.

Quick type choices:

| If the real change primarily... | Prefer... | Example |
| --- | --- | --- |
| adds or expands user-visible behavior | `feat` | `feat(search): add prefix matching for tags` |
| corrects incorrect behavior | `fix` | `fix(auth): reject expired refresh tokens` |
| changes documentation only | `docs` | `docs(api): clarify token rotation flow` |
| changes formatting, whitespace, or style without changing logic | `style` | `style(auth): normalize indentation in login handler` |
| adds or corrects tests without changing runtime behavior | `test` | `test(auth): cover expired token rejection` |
| restructures code without changing behavior | `refactor` | `refactor(cache): extract key builder` |
| improves performance characteristics | `perf` | `perf(query): reduce duplicate index scans` |
| changes tooling, packaging, or automation | `build`, `ci`, or `chore` | `ci(actions): cache pnpm store` |
| reverts a prior commit (use the dedicated revert format below) | `revert` | `revert: reject expired refresh tokens` |

### Scope Derivation

A scope MUST be a noun describing a section of the codebase, contained within parentheses (e.g., `fix(parser):`).

Add a scope only when it clarifies the affected surface. Derive from the changed file paths, not from guesses.

| Changed paths match... | Suggested scope | Example |
| --- | --- | --- |
| `src/**/auth/**`, `src/auth/**`, `**/auth*` | `auth` | `fix(auth): reject expired tokens` |
| `src/**/api/**`, `controllers/**`, `routes/**`, `handlers/**` | `api` | `feat(api): add user endpoint` |
| `docs/**`, `*.md` (non-skill docs), `README*` | `docs` | `docs(api): clarify token flow` |
| `**/*Test*`, `**/test/**`, `__tests__/**`, `**/*_test.*`, `**/*.spec.*` | test (or module scope) | `test(auth): cover rejection` |
| `**/package.json`, `**/pom.xml`, `**/build.gradle*`, `**/Cargo.toml` | `build` | `chore(build): bump dependency` |
| `.github/workflows/**`, `.gitlab-ci.yml`, `Jenkinsfile*`, `**/Makefile` | `ci` | `ci(actions): cache store` |
| `**/config/**`, `*.env.example`, `*.conf` | `config` | `chore(config): add rate limit` |

Multi-directory rules:

- Files span two related subsystems: use the broader scope or pick the primary module.
- Files span more than 2 unrelated subsystems: omit scope entirely.
- Single-file change in root directory: omit scope.
- Refactor touching many files across modules: omit scope unless all files share one clear module boundary.

Broken-vs-correct scope choices:

```text
# Broken — scope adds noise without clarification
chore(src): update dependencies

# Correct — scope clarifies the affected surface
chore(build): bump lodash from 4.x to 5.x

# Broken — over-specified for cross-module refactor
refactor(core, api, auth, db): extract shared validation

# Correct — omits scope when too many modules touched
refactor: extract shared validation into common package
```

### Formatting Rules

Subject line rules:

- Maximum 72 characters (best practice matching git convention; not a spec requirement); SHOULD NOT exceed 72.
- Imperative mood ("add" not "added", "fix" not "fixed").
- No trailing period.
- Lowercase type and lowercase scope are RECOMMENDED for consistency (the spec is case-insensitive except `BREAKING CHANGE`).
- Format: `<type>(<scope>): <summary>` or `<type>: <summary>`.

Body rules:

- Blank line between subject and body.
- Body is free-form; MAY consist of paragraphs or bullet points (`- `).
- Body lines wrapped at 72 characters (soft limit matching git convention).
- Body MUST explain WHY, not WHAT (WHAT is in the diff).
- One blank line between body paragraphs.

Complete well-formed example:

```text
feat(search): add prefix matching for tags

- Allow tag queries to match on prefix when exact match returns
  no results.
- Preserve case-insensitive comparison per the search contract.

Refs: PROJ-123
```

Formatting anti-patterns:

```text
# Broken — trailing period, past tense, no blank line before body
fix(Auth): Rejected expired tokens.
Token rejection was updated.

# Correct — imperative, no period, blank line before body
fix(auth): reject expired refresh tokens

- Prevent expired refresh tokens from creating new sessions.
```

### Footers

Standard footer format: `<token><separator><value>`. Footers go after a blank line following the body. Multiple footers are separated by blank lines.

Footer format rules:

- Each footer: `<token><separator><value>`.
- Separator is either `: ` (colon + space) or ` #` (space + hash).
- Token MUST use `-` instead of spaces (e.g., `Acked-by`, `Reviewed-by`).
- Exception: `BREAKING CHANGE` MAY contain a space.
- Footer values MAY span multiple lines; parsing terminates at the next valid token/separator pair.

| Footer token | When to use | Example |
| --- | --- | --- |
| `Refs` | Issue/tracker reference | `Refs: PROJ-123` or `Refs #123` |
| `Closes` | Issue this commit resolves | `Closes: #42` |
| `BREAKING CHANGE:` or `BREAKING-CHANGE:` | API/behavior breaking change | `BREAKING CHANGE: removes legacy endpoint` |
| `Co-authored-by` | Pair programming or co-author credit | `Co-authored-by: Name <email>` |
| `Reviewed-by` | Code review credit | `Reviewed-by: Alice <alice@example.com>` |
| `Acked-by` | Acknowledgement credit | `Acked-by: Bob <bob@example.com>` |

BREAKING CHANGE detail: write a full paragraph explaining the migration path, not just a label.

Revert format:

```text
revert: <description of why the revert happened>

Refs: <sha1>, <sha2>
```

For multiple reverted commits, list all SHAs in the footer.

Breaking changes MAY appear on any type, not just `feat`:

- `fix(auth)!: change default token expiry semantics`
- `chore(deps)!: upgrade to v5 with removed APIs`
- `docs!: restructure all URL paths in documentation`

Breaking change variant with explicit footer (recommended for complex migrations):

```text
feat(api)!: remove legacy session refresh endpoint

- Replace the legacy refresh endpoint with the token-rotation flow.
- Keep server behavior aligned with the new authentication contract.

BREAKING CHANGE: Clients must switch from `/v1/session/refresh`
to `/v2/tokens/refresh`. The legacy endpoint returns 404 after
this release.
```

Breaking change variant with `!` only (no footer needed; the description IS the breaking change notice):

```text
feat!: drop support for Node 6
```

### Split Criteria

Split instead of forcing one commit when any of these signals fire:

Signal 1: Subject would contain "and"

```text
# Diff touches auth/login.js (bug fix) and utils/format.js (cosmetic cleanup)
# Forced single-commit subject:
fix(auth): reject expired tokens and format date strings
#                                    ^^^^ mixed purpose -> SPLIT

# Correct split:
fix(auth): reject expired tokens
style: normalize date string formatting in utils
```

Signal 2: Independent revert boundary

```text
# Diff adds a feature flag AND removes dead code
# If you revert the commit, you lose the feature flag AND restore dead code
# These have independent rollback semantics -> SPLIT

# Correct split:
feat(flags): add dark-mode toggle
refactor: remove unused legacy theme helpers
```

Signal 3: Different validation story

```text
# Diff fixes a bug (needs regression test) AND refactors naming (needs lint only)
# Validation requirements differ per concern -> SPLIT

# Correct split:
fix(db): handle null foreign key in user query
refactor(models): rename userId to owner_id across ORM layer
```

Split decision procedure:

1. Count distinct purpose areas in the diff.
2. Check if each area has independent rollback semantics.
3. Check if validation differs per area.
4. If any signal fires, propose split with per-commit outline.

Split proposal format to present to the user:

```text
Recommend splitting into N commits:

1. <type>(<scope>): <subject>
   - Files: <list>
   - Why: <reason>

2. <type>(<scope>): <subject>
   - Files: <list>
   - Why: <reason>

Proceed as one commit or split?
```

## Diff-to-Prose Mapping

Translate raw diff content into each section of the hosted review body.

### Summary Section

Extract WHAT changed from the diff. Rules:

- 1-3 bullets maximum.
- Start each bullet with a verb (adds, fixes, removes, updates, refactors).
- Name the affected module or surface, not individual files.
- Group related file changes into one bullet.

```text
# Raw diff: auth/login.js (+15/-3), auth/session.js (+8/-2), auth/middleware.js (+5/-0)

# Good Summary:
- Fix token validation in auth module to reject expired sessions
- Add session cleanup after failed authentication attempts

# Bad Summary (copies diff):
- Modified auth/login.js, auth/session.js, auth/middleware.js
```

### Why Section

Extract WHY from the nature of the change. Rules:

- 1-2 bullets maximum.
- State the problem being solved or requirement being met.
- If unclear from diff alone, write: "Reason not evident from diff; confirm with author."

### Validation Section

Map verification methods to what actually changed:

| What changed | Suggest validation | Rule |
| --- | --- | --- |
| Runtime code | Unit tests, integration tests, manual smoke test | List ONLY checks that were run or can be run now |
| Configuration | Config validation, smoke test, dry-run | Do not list checks that don't apply |
| Documentation | Link check, preview render, accuracy review | Be specific about what was checked |
| CI/CD config | Pipeline dry-run, lint, action syntax check | Name the specific pipeline or workflow |
| Tests only | Test suite pass, coverage check | Note if coverage threshold met |

Rule: list ONLY checks that were actually performed or can be performed now. Never fabricate validation entries.

### Risks Section

Identify risks from the diff content:

| Risk signal in diff | Risk text | When to write `None` |
| --- | --- | --- |
| API signature change, removed parameter | Backward compatibility: callers must update | No breaking changes, no deployment ordering concern |
| Database migration, schema change | Migration required; deploy order matters | No schema or data changes |
| Dependency version bump | Transitive dependency conflicts possible | No dependency changes |
| Performance-critical path modified | Regression risk under load | Changes are purely additive or cosmetic |
| Auth/security touched | Review access control impact | No auth/security surface changed |

If no identifiable risk exists, write `None`.

## Ready-to-Adapt Templates

Hosted review body shape to adapt when a repository template exists but the exact body must still be filled with real change details:

```text
## Summary
- <fill with the actual change>

## Why
- <fill with the actual motivation>

## Validation
- <fill with tests, checks, or manual verification actually performed>
```

Fallback hosted review body when no repository template exists:

```text
## Summary
- <1-3 bullets describing the real diff>

## Why
- <reason the change exists>

## Validation
- <tests, lint, typecheck, or manual checks that actually ran>

## Risks
- <follow-up risks, rollout notes, or `None`>
```

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| exact GitHub pull request template paths, named template handling, or GitHub-specific preservation rules | [github-pull-request-templates.md](./references/github-pull-request-templates.md) |
| exact GitLab merge request template paths, `Default.md`, variables, or quick-action preservation rules | [gitlab-merge-request-templates.md](./references/gitlab-merge-request-templates.md) |

## Worked Example

Complete scenario from dirty working tree to final output.

Starting state: a repository with mixed staged and unstaged changes in the auth module.

Step 1: Inspect repository state

```bash
$ git status
On branch feature/auth-token-cleanup
Changes to be committed:
  (use "git restore --staged <file>..." to unstage)
        modified:   src/auth/login.ts
        modified:   src/auth/session.ts
Changes not staged for commit:
  (use "git add <file>..." to update what will be committed)
  (use "git restore <file>..." to discard changes in working directory)
        modified:   src/auth/middleware.ts

$ git diff --cached
diff --git a/src/auth/login.ts b/src/auth/login.ts
index abc1234..def5678 100644
--- a/src/auth/login.ts
+++ b/src/auth/login.ts
@@ -45,7 +45,10 @@ export async function login(credentials: Credentials): Promise<Session> {
   const user = await findUserByEmail(credentials.email);
+  if (!user) {
+    throw new AuthenticationError('invalid credentials');
+  }
   const token = generateToken(user);
   const session = await createSession(user.id, token);
   return { user, token, session };
 }

diff --git a/src/auth/session.ts b/src/auth/session.ts
index def5678..ghi9012 100644
--- a/src/auth/session.ts
+++ b/src/auth/session.ts
@@ -12,6 +12,9 @@ export async function createSession(userId: string, token: string): Promise<Session> {
   const expiresAt = new Date(Date.now() + SESSION_TTL);
+  if (isExpired(token)) {
+    throw new SessionError('token expired');
+  }
   return db.sessions.insert({ userId, token, expiresAt });
 }

$ git diff
diff --git a/src/auth/middleware.ts b/src/auth/middleware.ts
--- a/src/auth/middleware.ts
+++ b/src/auth/middleware.ts
@@ -8,7 +8,7 @@ export function authMiddleware(req: Request, res: Response, next: NextFunction): void {
-  const session = req.headers['x-session-id'];
+  const session = req.headers['x-session-id'] ?? req.cookies?.session_id;
   if (!session) {
     return res.status(401).json({ error: 'unauthenticated' });
   }
   next();
 }

$ git log -5 --oneline
a1b2c3d (HEAD -> feature/auth-token-cleanup) feat(auth): add OAuth2 provider support
e4f5g6h fix(auth): handle missing user avatar gracefully
i7j8k9l feat(api): add user profile endpoint
m0n1o2p chore(ci): upgrade Node.js to 20 in CI workflow
q3r4s5t docs(readme): update installation instructions
```

Step 2: Decide publication approach

- Staged changes exist: `login.ts` and `session.ts` (both auth module, same purpose: input validation).
- Unstaged change: `middleware.ts` (cookie fallback, different concern).
- Decision: one logical unit for staged changes; call out unstaged middleware change as follow-up.

Step 3: Choose type and scope

- Type: `fix` (corrects incorrect behavior: missing validation allowed invalid/expired input).
- Scope: `auth` (all changed files under `src/auth/`).

Step 4: Draft commit message

```text
fix(auth): validate credentials and token before session creation

- Reject login early when email does not match any user.
- Throw explicit error for expired tokens during session creation.
```

Step 5: Detect host

```bash
$ git remote -v
origin  https://github.com/example/webapp.git (fetch)
-> Host: GitHub
```

Step 6: Prepare review body (using fallback template)

```text
## Summary
- Fix auth module to validate user existence and token freshness before creating sessions

## Why
- Unvalidated login allowed null-user token generation; expired tokens created orphaned sessions

## Validation
- Added unit tests for null-email rejection in login.ts
- Added unit tests for expired-token rejection in session.ts
- Manual: verified 401 response on expired token replay

## Risks
- Clients sending expired tokens receive 401 instead of 500; verify error handling upstream
```

Step 7: Validate result

- Subject length: 63 characters (within 72 limit).
- Type `fix` is valid; scope `auth` matches file paths.
- Commit message explains why, not what.
- Body anchored to `git diff --cached` only.
- Unstaged middleware change flagged as out of scope.

Step 8: Output contract fulfillment

1. **Publication decision:** one commit (staged changes form one logical unit).
2. **Proposed commit:** `fix(auth): validate credentials and token before session creation` (ready-to-run).
3. **Review body:** fallback template filled above.
4. **Host/template assumption:** GitHub; template missing (fallback used).
5. **Validation statement:** unit tests added for both changed files; manual verification performed; unstaged middleware change excluded.

## Edge Cases

| Edge case | Detection | Response |
| --- | --- | --- |
| Empty repository (no commits) | `git log` returns empty | Skip history check; base type/scope on files alone; note this is initial commit |
| First commit on new branch | `git log --oneline` shows 0 entries for branch | Normal flow; note branch is new |
| Detached HEAD | `git symbolic-ref HEAD` fails | Warn user; recommend creating or checking out a branch first; do not draft commit for detached HEAD |
| Binary files in diff | `git diff --cached` shows `Binary files differ` | Note binary files in summary by name; do not attempt to describe binary content |
| Large diff (>200 lines) | `git diff --cached \| wc -l` > 200 | Summarize by file rather than listing every hunk; consider split recommendation |
| Submodules present | `.gitmodules` exists or `git submodule status` shows entries | Note submodules in validation; do not include submodule state in commit message unless explicitly changed |
| Only untracked files | `git status` shows untracked but no staged/unstaged modifications | Recommend staging relevant files first; do not draft commit from untracked-only state |
| Merge conflict markers | `grep -r '^<<<<<<< ' .` finds matches | Stop; tell user to resolve conflicts before drafting commit |
| Stash present | `git stash list` is non-empty | Note stash existence but do not pop or drop; proceed with working tree as-is |
| Signed commits required | `git config --get commit.gpgsign` returns `true` | Note GPG signing will be applied; do not attempt to sign or bypass |
| `.gitignore` issues | Changed files appear in `git status` that should be ignored | Flag suspicious tracked files; recommend checking `.gitignore` before committing |

## Output Contract

Return:

1. The publication decision: one commit, split required, or blocked by missing staged changes
2. The proposed Conventional Commit message, or a clearly labeled provisional draft when staging is incomplete
3. The hosted review body text, preserving a confirmed template or using the fallback structure from this file
4. The host and template assumption used: GitHub, GitLab, or unknown; template preserved, template missing, or template unconfirmed
5. The validation statement with only checks that actually ran, plus any explicit blockers or follow-up risks

## Validation Protocol

Validate the result with these executable checks:

```bash
# 1. Staged changes exist
echo "=== Staged changes ==="
git diff --cached --stat
# Must show at least one file; if empty, result is blocked

# 2. Unstaged drift awareness
echo "=== Unstaged changes (excluded from commit) ==="
git diff --stat
# Compare to staged stat; flag mismatches to user

# 3. Subject length check
echo "=== Subject length (must be <= 72) ==="
echo -n "<proposed subject>" | wc -c

# 4. Type validity
echo "=== Type check ==="
echo "<proposed type>" | grep -E '^(feat|fix|docs|style|test|refactor|perf|build|ci|chore|revert)$'
# Must exit 0

# 5. Recent history consistency
echo "=== Recent history (style consistency check) ==="
git log -5 --oneline

# 6. Template preservation (when host is known)
# Re-run discovery command from Host Detection or host-specific reference
# Confirm sections match the discovered template
```

Pass/fail criteria:

| Check | Pass condition | Fail action |
| --- | --- | --- |
| Staged changes exist | `--cached --stat` shows files | Block: tell user to stage files |
| Subject length | <= 72 characters | Rewrite subject to fit |
| Type validity | Matches allowed types list | Change to valid type |
| History consistency | Proposed style does not conflict with established repo pattern | Adjust to match repo or justify deviation |
| Template preservation | Filled sections are subset of template sections | Re-fill using correct template structure |
| Validation honesty | Only actually-run checks listed | Remove any planned-but-not-run checks |

## Invariants

- MUST inspect real repository state before drafting commit or hosted-review text.
- MUST avoid grouping unrelated changes into one commit.
- MUST use a valid Conventional Commit type.
- MUST keep the subject line imperative and concise (<= 72 chars, no trailing period).
- MUST align the chosen type and scope with the actual diff.
- MUST preserve repository template structure when a hosted template exists.
- MUST NOT mark validation or checklist items as done unless they are actually done.
- MUST NOT recommend committing secrets, credentials, or unrelated generated noise by default.
- SHOULD open a host-specific reference only when exact GitHub or GitLab template mechanics are the blocker.
- SHOULD propose a split when the staged diff shows multiple independent concerns.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| mixing unrelated work into one commit | review and rollback become harder | split the changes into separate logical commits |
| writing subjects like `update files` | the commit history loses meaning | write an imperative summary tied to the actual change |
| copying the diff into hosted review text | reviewers lose the higher-level story | summarize the change, motivation, and validation instead |
| moving the common path into host-specific references | the main skill stops being self-sufficient | keep commit drafting and fallback review guidance in `SKILL.md` |
| checking boxes for work that has not been done | the review narrative becomes misleading | mark only the items that are actually true |
| drafting commit from unstaged diff | the committed content will not match the message | always draft from `git diff --cached` |
| omitting scope when files cluster in one module | commit loses navigational signal | add scope when changed files share a clear module boundary |
| writing body that restates file names | body adds no value beyond the diff | explain WHY the change exists, not WHICH files changed |

## Scope Boundaries

- Activate this skill for:
  - commit readiness decisions
  - Conventional Commit drafting
  - commit split guidance for mixed change sets
  - hosted review body preparation with GitHub/GitLab branching only when needed
- Do not use this skill as the primary source for:
  - Git history rewriting or interactive rebase strategy
  - merge-conflict resolution
  - low-level Git fundamentals for beginners
  - general GitHub or GitLab issue, CI, or project-management workflows
