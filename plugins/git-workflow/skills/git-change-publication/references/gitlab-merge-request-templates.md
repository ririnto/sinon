---
title: GitLab Merge Request Templates
description: >-
  Reference for exact GitLab merge request template detection and preservation behavior.
---

Use this reference when the repository is hosted on GitLab and the blocker is exact merge request template detection, `Default.md` handling, variable preservation, or quick-action preservation rather than the shared commit and fallback review workflow already covered in `SKILL.md`.

## Template Discovery

Check whether the repository already ships a GitLab template before drafting a fallback body.

```bash
python - <<'PY'
from pathlib import Path

template_dir = Path('.gitlab/merge_request_templates')
if template_dir.exists():
    for path in sorted(template_dir.glob('*.md')):
        print(path)
PY
```

## Preservation Rules

- Look under `.gitlab/merge_request_templates/` for available templates.
- Treat `Default.md` as the default local template shape when it exists.
- Preserve the existing section order, headings, checklists, and placeholder intent.
- Preserve GitLab variables such as `%{source_branch}` or `%{target_branch}` when the template already uses them.
- Preserve GitLab quick actions such as `/label` or `/assign` when the template already uses them.
- Mark checkboxes only when the statement is actually true.
- Do not delete required headings just because one section is brief.

## GitLab-Specific Example

```text
## Summary
- <fill with the actual change>

## Validation
- <fill with checks that actually ran>

%{source_branch}
%{target_branch}
/label ~backend
```

## GitLab-Specific Pitfall

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| dropping GitLab variables or quick actions from an existing template | the MR loses automation or context encoded by the template | preserve the template body shape instead of rewriting it |
