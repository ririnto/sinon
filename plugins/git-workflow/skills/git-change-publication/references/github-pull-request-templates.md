---
description: >-
---


## Template Discovery


```bash
python - <<'PY'
from pathlib import Path

paths = [
    Path('pull_request_template.md'),
    Path('docs/pull_request_template.md'),
    Path('.hosted-service/pull_request_template.md'),
    Path('.hosted-service/PULL_REQUEST_TEMPLATE.md'),
]
template_dir = Path('.hosted-service/PULL_REQUEST_TEMPLATE')
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
- Also check `.hosted-service/pull_request_template.md` and `.hosted-service/PULL_REQUEST_TEMPLATE.md`.
- Also check `.hosted-service/PULL_REQUEST_TEMPLATE/` for named templates.
- Preserve the existing section order, headings, checklists, and placeholder intent.
- Mark checkboxes only when the statement is actually true.
- Do not delete required headings just because one section is brief.
- When a repository uses named templates, keep the selected template body aligned with that template rather than merging content from multiple templates.

## hosted service-Specific Pitfall

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
