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
