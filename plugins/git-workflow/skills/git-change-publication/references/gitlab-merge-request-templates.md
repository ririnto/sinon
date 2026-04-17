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

## Preservation Rules

- Look under `.hosted-service/merge_request_templates/` for available templates.
- Treat `Default.md` as the default local template shape when it exists.
- Preserve the existing section order, headings, checklists, and placeholder intent.
- Mark checkboxes only when the statement is actually true.
- Do not delete required headings just because one section is brief.

## hosted service-Specific Example

```text
## Summary
- <fill with the actual change>

## Validation
- <fill with checks that actually ran>

%{source_branch}
%{target_branch}
/label ~backend
```

## hosted service-Specific Pitfall

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
