---
description: >-
---


## Template Discovery


```sh
uv run python - <<'PY'
from pathlib import Path

template_dir = Path('.hosted-service/merge_request_templates')
if template_dir.exists():
    for path in sorted(template_dir.glob('*.md')):
        print(path)
PY
```

### API-Based Discovery



```sh
curl --header "PRIVATE-TOKEN: $hosted-service_TOKEN" \
  "$hosted-service_API_URL/projects/$PROJECT_ID/templates/merge_requests" \
  || echo "NO_API_ACCESS"
```

Discovery precedence order (default template resolution, per official docs):

1. Template set in project settings (Settings > change descriptions > Default description template) — Premium/Ultimate only.
2. `Default.md` (case-insensitive) from the parent group's template project.
3. `Default.md` (case-insensitive) from the project repository's `.hosted-service/merge_request_templates/` directory.

Additional inheritance (documented but not fully enumerated):

- Instance-level admins MAY integrate MR description templates through an instance template repository (Premium/Ultimate). The interaction order between instance-level templates and the three-step default chain is not explicitly documented; treat instance-level templates as a fallback that applies only when no project or group default resolves.

Rules:

- Templates MUST be on the default branch to be active.
- `Default.md` is case-insensitive; hosted service recognizes `default.md`, `DEFAULT.MD`, etc.
- If offline, only filesystem discovery (step 3) is available.
- Report the template as "filesystem-only discovery; project-settings, group, and instance-level templates not verified" when offline or when the API is unreachable.

## Default vs. Named Templates


| Scenario | What hosted service does |
| --- | --- |
| User selects no template explicitly | Uses `Default.md` (case-insensitive) when it exists; otherwise uses project settings template, group template, instance-level template, or blank body |
| User selects a named template (e.g., `feature.md`) | Uses only that file; `Default.md` is ignored |
| No `.hosted-service/merge_request_templates/` directory | Falls back to project settings template, group template, instance-level template (via API), or SKILL.md fallback body |

This means `Default.md` is the baseline only when it will actually be applied. A named template does not inherit from `Default.md`.

## Project Settings Template


- `Web UI`: Settings > change descriptions > Default description template
- `REST API`: `merge_requests_template` attribute via Projects API

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
| Author explicitly selects a named template (project, group, or instance) | The selected named template wins; no inheritance from `Default.md`. |
| Project settings default description template is configured | Project settings template wins over any `Default.md`. |
| No project settings + parent group has `Default.md` + project has `Default.md` | Group `Default.md` wins (inherits from parent). |
| Project has `Default.md` + no group template | Project `Default.md` applies. |
| No project or group default template + instance template repository is configured | Instance-level template is the remaining fallback; confirm via API before relying on it. |

Documented default chain: project settings > group `Default.md` > project `Default.md`.

`Default.md` filename is case-insensitive across all levels.

Detect instance-level template (API, maintainer/owner permissions required):

```sh
curl --header "PRIVATE-TOKEN: $hosted-service_TOKEN" \
    "$hosted-service_API_URL/templates/merge_requests" \
    || echo "NO_INSTANCE_TEMPLATE"

curl --header "PRIVATE-TOKEN: $hosted-service_TOKEN" \
    "$hosted-service_API_URL/templates/merge_requests/Default" \
    || echo "NO_INSTANCE_DEFAULT_TEMPLATE"
```

If the API is unreachable or credentials are insufficient, report the inability to verify instead of silently falling back.

## Preservation Rules

- Look under `.hosted-service/merge_request_templates/` for available templates.
- Treat `Default.md` as the default local template shape when it exists.
- Preserve the existing section order, headings, checklists, and placeholder intent.
- Mark checkboxes only when the statement is actually true.
- Do not delete required headings just because one section is brief.

### Variable Preservation

> [!IMPORTANT]
>


| Variable | Description | When to leave as-is |
| --- | --- | --- |
| `%{source_branch}` | The name of the branch being merged | Template displays branch info in header or footer |
| `%{target_branch}` | The branch that the changes are applied to | Template shows target for reviewer context |
| `%{all_commits}` | Messages from all commits in the MR (the MR body is truncated when it would exceed the MR description limit) | Template references full commit history |
| `%{co_authored_by}` | Names and emails of commit authors derived from `Co-authored-by:` trailers | Template credits multiple authors |
| `%{first_commit}` | Full message of the first commit | Template references the initial commit |
| `%{first_multiline_commit}` | Full message of the first commit that is not a merge commit | Template needs full first-commit content |
| `%{first_multiline_commit_description}` | Description part (without the first line/title) of `%{first_multiline_commit}` | Template references first-commit description |

Variables outside this list (for example `%{url}`, `%{title}`, `%{id}`) are not part of the change description template variable set. If an existing template uses them, preserve them verbatim so version-dependent behavior is not silently lost, but do not introduce new variables beyond the seven above.

If the template already contains any of these variables, keep them verbatim. Do not replace them with static text. Do not add new variable references unless the template already demonstrates them. When the MR will be opened with a named template rather than the default, surface this to the user so they know the variables will remain literal.

### Quick-Action Preservation


```text
/label ~backend
/assign @username
/milestone %"1.0"
/target_branch master
/title "Fix: "
```

These are not comments. If the template already uses quick actions, preserve them. Dropping quick actions breaks CI automation, label routing, or reviewer assignment that the template encodes.

Quick action reference:

| Action | Effect | When to preserve |
| --- | --- | --- |
| `/label ~label_name` | Applies label at MR creation | Template encodes required labels |
| `/assign @user` | Assigns an assignee at creation | Template specifies default assignee |
| `/reviewer @user` | Requests review from user | Template sets default reviewer (distinct from assign) |
| `/milestone %"name"` | Sets milestone | Template ties MR to a release milestone |
| `/target_branch name` | Overrides default target branch | Feature branches target non-default branch |
| `/title "text"` | Sets MR title prefix | Template enforces title convention |
| `/due_date` | Sets due date | Template ties MR to a deadline |
| `/unlabel ~label_name` | Removes a label | Template manages label lifecycle |
| `/copy_metadata` | Copies metadata from a linked issue | Template pulls issue metadata |
| `/shrink` | Collapses the MR description | Template optimizes for lengthy descriptions |
| `/todo` | Creates a todo item | Template tracks follow-up tasks |


## MR Options Interaction

### Quick Actions at Creation Time

When a template contains `/label`, `/assign`, or `/milestone`, these execute before any reviewer sees the MR. The rendered MR description will not show the quick action lines (hosted service processes and removes them), but their effects persist.

Example: template source before MR creation:

```text
## Summary
- <fill with the actual change>

/label ~backend
/assign @reviewer
/milestone %"Sprint 5"
```

Rendered MR description after creation:

```text
## Summary
- Fix auth token validation
```

The quick-action lines are consumed; label `~backend` is applied, `@reviewer` is assigned, and milestone `Sprint 5` is set when permissions allow.

### Variable Substitution Timing


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
```

The quick-action lines are consumed; label `~backend` is applied and `@team-lead` is assigned when permissions allow.

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

bug.md template:

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
