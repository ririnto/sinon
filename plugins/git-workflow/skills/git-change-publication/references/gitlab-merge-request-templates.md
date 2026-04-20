---
description: >-
---


## Template Discovery


```bash
python - <<'PY'
from pathlib import Path

template_dir = Path('.hosted-service/merge_request_templates')
if template_dir.exists():
    for path in sorted(template_dir.glob('*.md')):
        print(path)
PY
```

### API-Based Discovery


```bash
# Requires host-cli CLI or curl with token; skip if offline
curl --header "PRIVATE-TOKEN: $hosted-service_TOKEN" \
  "$hosted-service_API_URL/projects/$PROJECT_ID/templates/mergerequests" 2>/dev/null \
  || echo "NO_API_ACCESS"
```

Discovery precedence order:

1. Project settings (Settings > change descriptions > Default description template) — highest priority
2. Parent group's `Default.md` (case-insensitive, via `.hosted-service/merge_request_templates/` in group template project)
3. Project-level `.hosted-service/merge_request_templates/` (filesystem; case-insensitive for `Default.md`)
5. Instance-level admin template (only discoverable via API; lowest priority)

Rules:

- Templates MUST be on the default branch to be active.
- `Default.md` is case-insensitive (hosted-service recognizes `default.md`, `DEFAULT.MD`, etc.).
- If offline, only filesystem discovery (step 3) is available.
- Report template as "filesystem-only discovery; group/instance-level templates not verified" when offline.

## Default vs. Named Templates


| Scenario | What hosted service does |
| --- | --- |
| User selects no template explicitly | Uses `Default.md` (case-insensitive) when it exists; otherwise uses project settings template, group template, instance-level template, or blank body |
| User selects a named template (e.g., `feature.md`) | Uses only that file; `Default.md` is ignored |
| No `.hosted-service/merge_request_templates/` directory | Falls back to project settings template, group template, instance-level template (via API), or SKILL.md fallback body |

This means `Default.md` is the baseline only when it will actually be applied. A named template does not inherit from `Default.md`.

## Project Settings Template


- **Web UI:** Settings > change descriptions > Default description template
- **REST API:** `merge_requests_template` attribute via Projects API

This template has the highest precedence (above group-level and project-level `Default.md` files) when an author creates an MR without selecting a specific template.

## Default.md Fallback Boundary

Use the SKILL.md fallback body when:

- `.hosted-service/merge_request_templates/` does not exist or contains no `.md` files, AND
- API discovery returns no instance-level template

Do not fall back when `Default.md` exists and the author has not selected a different template.
If the MR author explicitly selects a named template that you cannot locate, report the template as unconfirmed instead of silently switching to the generic fallback.

## Instance-Level vs. Project-Level Precedence


| Scenario | Which template applies |
| --- | --- |
| Project settings template set + any other template exists | Project settings template wins |
| No project settings + group has `Default.md` + project has `Default.md` | Group `Default.md` wins (inherits from parent) |
| Project has `Default.md` + no group template + no instance template | Project `Default.md` applies |
| No project templates + group template exists | Group-level template applies |
| No project or group templates + instance template exists | Instance-level template applies |
| Project has named template selected + any other template exists | Named template wins (explicit selection overrides all) |

The precedence chain: **project settings** > **group Default.md** > **project Default.md** > **instance-level admin template**.

`Default.md` filename is case-insensitive across all levels.

Detect instance-level template (API-only):

```bash
curl --header "PRIVATE-TOKEN: $hosted-service_TOKEN" \
  "$hosted-service_API_URL/templates/mergerequests" 2>/dev/null \
  || echo "NO_INSTANCE_TEMPLATE"
```

Note: This endpoint requires maintainer or owner permissions. Report inability to verify if credentials are insufficient.

## Preservation Rules

- Look under `.hosted-service/merge_request_templates/` for available templates.
- Treat `Default.md` as the default local template shape when it exists.
- Preserve the existing section order, headings, checklists, and placeholder intent.
- Mark checkboxes only when the statement is actually true.
- Do not delete required headings just because one section is brief.

### Variable Preservation

hosted service substitutes these variables at MR creation time (case-insensitive):

| Variable | Description | When to leave as-is |
| --- | --- | --- | --- |
| `%{source_branch}` | The branch to be merged | Template displays branch info in header or footer |
| `%{target_branch}` | The branch the source branch merges into | Template shows target for reviewer context |
| `%{all_commits}` | All commit messages in the MR (up to 100, last 100KiB excluded) | Template references full commit history |
| `%{co_authored_by}` | `Co-authored-by:` trailers from recent commits | Template credits multiple authors |
| `%{first_commit}` | The very first commit message in the MR | Template references the initial commit |
| `%{first_multiline_commit}` | First commit with a multi-line body (not merge commit) | Template needs full first-commit content |
| `%{first_multiline_commit_description}` | Description part of the first multiline commit (after the subject line) | Template references first-commit description |

Note: `%{url}`, `%{title}`, and `%{id}` are not in the official MR template variable list. They may be issue-template legacy variables or version-dependent. Prefer the 7 variables listed above. If a template uses them, preserve them as-is since they may still function in some hosted service versions.

If the template already contains any of these variables, keep them verbatim. Do not replace them with static text. Do not add new variable references unless the template already demonstrates them.

### Quick-Action Preservation


```text
/label ~backend      /assign @username      /milestone %"1.0"
/target_branch master      /title "Fix: "
```

These are not comments. If the template already uses quick actions, preserve them. Dropping quick actions breaks CI automation, label routing, or reviewer assignment that the template encodes.

Quick action reference:

| Action | Effect | When to preserve |
| --- | --- | --- |
| `/label ~label_name` | Applies label at MR creation | Template encodes required labels |
| `/assign @user` | Assigns reviewer at creation | Template specifies default assignee |
| `/reviewer @user` | Requests review from user | Template sets default reviewer (distinct from assign) |
| `/milestone %"name"` | Sets milestone | Template ties MR to a release milestone |
| `/target_branch name` | Overrides default target branch | Feature branches target non-default branch |
| `/title "text"` | Sets MR title prefix | Template enforces title convention |
| `/cc @user` | Adds CC participant | Template includes stakeholder CC |
| `/due_date` | Sets due date | Template ties MR to a deadline |
| `/unlabel ~label_name` | Removes a label | Template manages label lifecycle |
| `/copy_metadata` | Copies metadata from a linked issue | Template pulls issue metadata |
| `/shrink` | Collapses the MR description | Template optimizes for lengthy descriptions |
| `/todo` | Creates a todo item | Template tracks follow-up tasks |

Note: Quick actions execute only if the user submitting the MR has the permissions to perform the relevant action.

## MR Options Interaction

### Quick Actions at Creation Time

When a template contains `/label`, `/assign`, or `/milestone`, these execute before any reviewer sees the MR. The rendered MR description will not show the quick action lines (hosted service processes and removes them), but their effects persist.

Example: template with quick actions before and after rendering:

```text
# Before MR creation (template source)
## Summary
- <fill with the actual change>

/label ~backend
/assign @reviewer
/milestone %"Sprint 5"

# After MR creation (rendered MR)
## Summary
- Fix auth token validation

# (quick actions consumed; label ~backend applied,
#  @reviewer assigned, milestone set to Sprint 5)
```

### Variable Substitution Timing

Variables are substituted at MR creation time, simultaneously with quick action processing. The order is: variables replaced first, then quick actions executed.

### Combined Example: Variables + Quick Actions + Content

Template source (`Default.md`):

```text
## change description: %{title}

Source: `%{source_branch}` → `%{target_branch}`

## Summary
- <fill>

## Validation
- <fill>

/label ~backend
/assign @team-lead
```

After preparing the change description (title "Fix auth validation", branch `fix/auth-tokens` -> `main`):

```text
## change description: Fix auth validation

Source: `fix/auth-tokens` -> `main`

## Summary
- Reject expired tokens and validate user existence before session creation

## Validation
- Unit tests added for login.ts and session.ts
- Manual verification performed

# (label ~backend applied, @team-lead assigned)
```

## Complete Template Examples

### Example 1: Default.md with Variables and Quick Actions

Project has `.hosted-service/merge_request_templates/Default.md`:

```text
## What does this MR do?
<description>

## What are the relevant tickets?
- Closes #

## Screenshots (if appropriate)
<screenshots>

## Configuration
- Create/config: <details>
- Destroy/rollback: <details>

## Checklist
- [ ] Tests added/updated
- [ ] Changelog entry added
- [ ] Documentation updated

%{source_branch}
%{target_branch}
/label ~development
/assign @maintainer
```

Filled with real change content:

```text
## What does this MR do?
Reject expired tokens and validate user existence before session creation in the auth module. Prevents orphaned sessions and null-user token generation.

## What are the relevant tickets?

## Screenshots (if appropriate)
N/A

## Configuration
- Create: No migration needed
- Rollback: No rollback needed

## Checklist
- [x] Tests added/updated
- [ ] Changelog entry added
- [ ] Documentation updated

fix/auth-tokens
main
/label ~development
/assign @maintainer
```

### Example 2: Named Template Selection

Project has `.hosted-service/merge_request_templates/bug.md` and `feature.md`. Author selects `bug.md`:

**bug.md template:**

```text
## Bug Summary
<what is broken>

## Root Cause
<why it broke>

## Fix
<what changed>

## Verification
- [ ] Reproducer fails after fix
- [ ] Regression test passes
- [ ] Manual QA approved
```

Filled output (only `bug.md` structure used; `feature.md` content ignored):

```text
## Bug Summary
Expired tokens create orphaned sessions because session creation lacks expiry check.

## Root Cause
The `createSession` function in `session.ts` generates a session record without validating token freshness against the expiry timestamp stored during `generateToken`.

## Fix
Added `isExpired(token)` guard in `createSession`; added null-user early return in `login`. Both checks throw explicit errors before session/token side effects occur.

## Verification
- [x] Reproducer script fails after fix (expired token returns 401)
- [x] Regression test: normal login still creates valid session
- [ ] Manual QA pending
```

## hosted service-Specific Pitfall

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| falling back to SKILL.md when `Default.md` exists and no named template was selected | the template is ignored without cause | use `Default.md` as the structural base when it exists |
| ignoring instance-level templates when offline | an instance template may apply at MR creation time, causing unexpected MR body shape | report "instance-level templates not verified" when offline |
| replacing `%{variables}` with static text | the substitution happens at MR creation time; static text duplicates the rendered value | keep variable placeholders verbatim |
