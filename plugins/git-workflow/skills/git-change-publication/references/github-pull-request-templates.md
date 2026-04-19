---
title: GitHub Pull Request Templates
description: >-
  Reference for exact GitHub pull request template detection and preservation behavior.
---

Use this reference when the repository is hosted on GitHub and the blocker is exact pull request template detection, named template handling, or GitHub-specific preservation rules rather than the shared commit and fallback review workflow already covered in `SKILL.md`.

## Template Discovery

Check whether the repository already ships a GitHub template before drafting a fallback body.

```bash
python - <<'PY'
from pathlib import Path

paths = [
    Path('pull_request_template.md'),
    Path('docs/pull_request_template.md'),
    Path('.github/pull_request_template.md'),
    Path('.github/PULL_REQUEST_TEMPLATE.md'),
]
template_dir = Path('.github/PULL_REQUEST_TEMPLATE')
if template_dir.exists():
    paths.extend(template_dir.glob('*.md'))

for path in paths:
    if path.exists():
        print(path)
PY
```

## Preservation Rules

- Look for `pull_request_template.md` at the repository root.
- Also check `docs/pull_request_template.md`.
- Also check `.github/pull_request_template.md` and `.github/PULL_REQUEST_TEMPLATE.md`.
- Also check `.github/PULL_REQUEST_TEMPLATE/` for named templates.
- Preserve the existing section order, headings, checklists, and placeholder intent.
- Mark checkboxes only when the statement is actually true.
- Do not delete required headings just because one section is brief.
- When a repository uses named templates, keep the selected template body aligned with that template rather than merging content from multiple templates.

## GitHub-Specific Pitfall

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| ignoring an existing GitHub pull request template | repository review expectations get broken | preserve the template headings, order, and checklists |

## Template Resolution Rules

GitHub can expose several supported template locations, but named templates under `.github/PULL_REQUEST_TEMPLATE/` require an explicitly requested or otherwise confirmed template choice.

- Check repository-root `pull_request_template.md`.
- Check `docs/pull_request_template.md`.
- Check `.github/pull_request_template.md` and `.github/PULL_REQUEST_TEMPLATE.md`.
- Check `.github/PULL_REQUEST_TEMPLATE/` for named templates.

- If a single non-directory template is the applicable repository template, preserve that file's structure.
- If named templates exist, preserve only the explicitly requested or otherwise confirmed named template.
- Do not invent a named-template choice from branch names or generic heuristics.
- Do not inject content from multiple templates into one review body.

## Named-Template Handling

If `.github/PULL_REQUEST_TEMPLATE/` contains multiple `.md` files, GitHub presents a chooser UI. The change publication skill must:

- Detect which named template the PR will use from an explicitly requested or otherwise confirmed template choice.
- Apply only the selected template's sections and placeholders.
- Skip content intended for other named templates even if those sections appear helpful.
- If the selected template cannot be confirmed, say that exact template preservation is unconfirmed instead of claiming a specific named template.

Example directory:

```text
.github/
└── PULL_REQUEST_TEMPLATE/
    ├── bug-fix-template.md
    └── feature-template.md
```

If the workflow targets `bug-fix-template.md`, use only the bug-fix template structure. Do not pull in checklist items from `feature-template.md`.

## Fallback Boundaries

Use the fallback workflow ONLY when:

- No repository-root `pull_request_template.md` exists, AND
- No `.github/PULL_REQUEST_TEMPLATE/` directory exists, AND
- No root-level `.github/pull_request_template.md` or `.github/PULL_REQUEST_TEMPLATE.md` exists, AND
- No `docs/pull_request_template.md` exists

When any GitHub-managed template is found, skip the fallback entirely. The fallback body must not replace an existing template; it may only be used when no template exists at all. If a named-template directory exists but the exact selected template cannot be confirmed, report template preservation as unconfirmed instead of falling back.

## Broken vs Correct Example

**Broken** — wrong template priority applied:

```markdown
<!-- `.github/PULL_REQUEST_TEMPLATE/feature.md` exists, but the skill wrote: -->
## Description
<!-- this body came from the fallback, not the named feature template -->
```

**Correct** — named template respected:

```markdown
<!-- `.github/PULL_REQUEST_TEMPLATE/feature.md` was detected and used -->
## Problem
<!-- sections match the named template exactly -->
## Solution
<!-- placeholder intent preserved, no foreign checklist items -->
## Validation
- [ ] tests added
- [ ] no regression
```

The broken version injects fallback content into a repository that already has a named template. The correct version detects the named template and uses its exact structure.
