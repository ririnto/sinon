---
description: >-
---


## Template Discovery


```bash
python - <<'PY'
from pathlib import Path

paths = [
    Path('pull_request_template.md'),
    Path('pull_request_template.txt'),
    Path('docs/pull_request_template.md'),
    Path('docs/pull_request_template.txt'),
    Path('.hosted-service/pull_request_template.md'),
    Path('.hosted-service/pull_request_template.txt'),
    Path('.hosted-service/PULL_REQUEST_TEMPLATE.md'),
    Path('.hosted-service/PULL_REQUEST_TEMPLATE.txt'),
]
for template_dir in [
    Path('.hosted-service/PULL_REQUEST_TEMPLATE'),
    Path('PULL_REQUEST_TEMPLATE'),
    Path('docs/PULL_REQUEST_TEMPLATE'),
]:
    if template_dir.exists():
        paths.extend(template_dir.glob('*.md'))
        paths.extend(template_dir.glob('*.txt'))

for path in paths:
    if path.exists():
        print(path)
PY
```

Rules:

- Template filenames are case-insensitive. `PULL_REQUEST_TEMPLATE.md`,
  `Pull_Request_Template.md`, and `pull_request_template.md` resolve to the
  same location.
- Templates MUST exist on the repository's default branch to be active.
  A template present only on a non-default branch is invisible to hosted service's
  PR creation flow.
- `.md` and `.txt` extensions are both supported.

### API-Based Fallback Discovery


```bash
# Requires host-cli CLI; skip if unavailable or offline
  || echo "NO_API_TEMPLATE"
```

Rules:

- If offline or no `host-cli` CLI available, rely solely on filesystem discovery.
- Do not fail or guess when neither filesystem nor API finds a template.
- If API returns a template that does not exist in the filesystem, use the API-discovered template structure.
- Report the discovery source (filesystem / API / both) in the output contract host assumption field.

## Preservation Rules

- Look for `pull_request_template.md` at the repository root.
- Also check `docs/pull_request_template.md`.
- Also check `.hosted-service/pull_request_template.md` and `.hosted-service/PULL_REQUEST_TEMPLATE.md`.
- Also check `.hosted-service/PULL_REQUEST_TEMPLATE/` for named templates.
- Preserve the existing section order, headings, checklists, and placeholder intent.
- Mark checkboxes only when the statement is actually true.
- Do not delete required headings just because one section is brief.
- When a repository uses named templates, keep the selected template body aligned with that template rather than merging content from multiple templates.

### Partial Template Handling

When a template has many sections but the change only meaningfully fills a subset:

1. Fill the relevant sections with real change content derived from the diff.
2. Leave remaining sections with their original placeholder text intact; do NOT delete them.
3. Add a trailing note outside the template structure: `[Other sections preserved from repository template]`

Broken-vs-correct:

```markdown
<!-- Broken — deleted "empty-looking" sections -->
## Description
Fix auth validation bug.

<!-- Correct — preserved all template sections -->
## Description
Fix auth validation bug.

## Type of Change
- [x] Bug fix (non-breaking change which fixes an issue)

## Checklist
- [x] My code follows the project's code style
- [ ] I have updated the documentation
- [ ] I have added tests that prove my fix is effective

[Other sections preserved from repository template: Reviewers, Testing]
```

## Template Resolution Rules


Apply the discovery order from Template Discovery and the preservation rules from Preservation Rules above, then follow these behavioral rules:

- If a single non-directory template is the applicable repository template, preserve that file's structure.
- If named templates exist, preserve only the explicitly requested or otherwise confirmed named template.
- Do not invent a named-template choice from branch names or generic heuristics.
- Do not inject content from multiple templates into one review body.

## Named-Template Handling

If `.hosted-service/PULL_REQUEST_TEMPLATE/` contains multiple `.md` files, hosted service presents a chooser UI. The change integration handoff skill must:

- Detect which named template the PR will use from an explicitly requested or otherwise confirmed template choice.
- Apply only the selected template's sections and placeholders.
- Skip content intended for other named templates even if those sections appear helpful.
- If the selected template cannot be confirmed, say that exact template preservation is unconfirmed instead of claiming a specific named template.

Example directory:

```text
.hosted-service/
└── PULL_REQUEST_TEMPLATE/
    ├── bug-fix-template.md
    └── feature-template.md
```

If the workflow targets `bug-fix-template.md`, use only the bug-fix template structure. Do not pull in checklist items from `feature-template.md`.

### Template Query Parameter


```text
https://github.com/{owner}/{repo}/compare/{base}...{head}?quick_pull=1&template={filename}
```

The `template` value is the filename (with extension) of a template file stored inside any `PULL_REQUEST_TEMPLATE/` subdirectory (root, docs/, or .hosted-service/). This parameter does NOT work with single-file templates at the repository root or hidden .hosted-service directory.

When both `template` and `body` query parameters are provided, the `template` parameter takes precedence: the specified template fills the body, and the `body` parameter value is ignored. Other parameters (`title`, `labels`, etc.) compose independently with the template-selected body.

### Organization/Account-Level Default Templates

Organizations and personal accounts can define default PR templates in a public `.hosted-service` repository. These defaults apply to any owned repository that lacks its own template, with this precedence order:

1. Repository's own `PULL_REQUEST_TEMPLATE/` folder (highest priority — full override)
2. Repository root single-file template
3. Repository `docs/` single-file template
4. Organization/account `.hosted-service` repository (lowest priority)

If a repository has ANY files in its own `PULL_REQUEST_TEMPLATE/` folder, the organization-level default is NOT used.

### YAML Frontmatter Note

Unlike issue templates (which require `name:` and `about:` keys in YAML frontmatter to appear in the chooser UI), change description templates do NOT require or use YAML frontmatter. PR template files are plain Markdown (or plain text). Any YAML frontmatter present in a PR template file is treated as literal body content and will appear in the PR description.

## Complete Template Examples

### Example 1: Single-File Root Template

Repository has `pull_request_template.md` at root:

```markdown
<!-- ## Description (raw template) -->
## Description
A clear description of what this PR changes and why it is needed.

## Type of Change
Please delete options that are not relevant.
- [ ] Bug fix (non-breaking change which fixes an issue)
- [ ] New feature (non-breaking change which adds functionality)
- [ ] Breaking change (fix or feature that would cause existing functionality to not work as expected)
- [ ] Documentation update

## Checklist
- [ ] My code follows the project's code style
- [ ] I have reviewed the code changes
- [ ] I have updated the documentation accordingly
- [ ] I have added tests that prove my fix is effective or that my feature works
- [ ] New and existing unit tests pass locally with my changes
```

Filled with real change content (auth token validation fix):

```markdown
## Description
Fix auth module to reject invalid credentials and expired tokens before session creation, preventing orphaned sessions and null-user token generation.

## Type of Change
- [x] Bug fix (non-breaking change which fixes an issue)

## Checklist
- [x] My code follows the project's code style
- [x] I have reviewed the code changes
- [ ] I have updated the documentation accordingly
- [x] I have added tests that prove my fix is effective or that my feature works
- [x] New and existing unit tests pass locally with my changes
```

### Example 2: Named Templates

Repository has `.hosted-service/PULL_REQUEST_TEMPLATE/` with two templates:

**bug-fix-template.md:**

```markdown
## Problem
What is the problem this PR solves?

## Solution
How does this PR solve the problem?

## Validation
- [ ] Tests added covering the fix
- [ ] No regression in related flows
```

**feature-template.md:**

```markdown
## Goal
What user-facing capability does this PR deliver?

## Approach
How was this implemented?

## Acceptance Criteria
- [ ] Criteria met
```

When the PR targets `bug-fix-template.md`, correct filled output:

```markdown
## Problem
Unvalidated login allowed null-user token generation; expired tokens created orphaned sessions in the database.

## Solution
Add early rejection for non-existent users in login flow; add explicit expired-token check before session creation.

## Validation
- [x] Tests added covering null-email rejection in login.ts
- [x] Tests added covering expired-token rejection in session.ts
- [x] Manual: verified 401 response on expired token replay
- [x] No regression in normal login flow
```

Note: No content from `feature-template.md` (Goal, Approach, Acceptance Criteria) appears in the filled output.

## Fallback Boundaries

Use the fallback workflow ONLY when template discovery (filesystem + API, if available) finds no template.


## Broken vs Correct Example

**Broken** -- wrong template priority applied:

```markdown
<!-- `.hosted-service/PULL_REQUEST_TEMPLATE/feature.md` exists, but the skill wrote: -->
## Description
<!-- this body came from the fallback, not the named feature template -->
```

**Correct** -- named template respected:

```markdown
<!-- `.hosted-service/PULL_REQUEST_TEMPLATE/feature.md` was detected and used -->
## Problem
<!-- sections match the named template exactly -->
## Solution
<!-- placeholder intent preserved, no foreign checklist items -->
## Validation
- [ ] tests added
- [ ] no regression
```

The broken version injects fallback content into a repository that already has a named template. The correct version detects the named template and uses its exact structure.

## hosted service-Specific Pitfall

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| deleting template sections that look empty | reviewers expect those sections to be present | fill relevant sections; leave others with placeholder text |
| merging content from multiple named templates | each named template serves a different PR category | apply only the confirmed named template |
| using fallback when API discovers a web-ui-only template | the repo has a template that is not on disk | use the API-discovered template structure |
