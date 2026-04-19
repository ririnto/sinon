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

## Default vs. Named Templates


| Scenario | What hosted service does |
| --- | --- |
| User selects no template explicitly | Uses `Default.md` when it exists; otherwise uses a blank body |
| User selects a named template (e.g., `feature.md`) | Uses only that file; `Default.md` is ignored |
| No `.hosted-service/merge_request_templates/` directory | Falls back to the SKILL.md fallback body |

This means `Default.md` is the baseline only when it will actually be applied. A named template does not inherit from `Default.md`.

## Default.md Fallback Boundary

Use the SKILL.md fallback body when:

- `.hosted-service/merge_request_templates/` does not exist or contains no `.md` files

Do not fall back when `Default.md` exists and the author has not selected a different template.
If the MR author explicitly selects a named template that you cannot locate, report the template as unconfirmed instead of silently switching to the generic fallback.

## Preservation Rules

- Look under `.hosted-service/merge_request_templates/` for available templates.
- Treat `Default.md` as the default local template shape when it exists.
- Preserve the existing section order, headings, checklists, and placeholder intent.
- Mark checkboxes only when the statement is actually true.
- Do not delete required headings just because one section is brief.

### Variable Preservation

hosted service substitutes these variables at MR creation time:

```text
%{source_branch}   %{target_branch}   %{url}   %{title}   %{id}
```

If the template already contains `%{source_branch}` or `%{target_branch}`, keep them verbatim. Do not replace them with static text. Do not add new variable references unless the template already demonstrates them.

### Quick-Action Preservation


```text
/label ~backend      /assign @username      /milestone %"1.0"
/target_branch master      /title "Fix: "
```

These are not comments. If the template already uses quick actions, preserve them. Dropping quick actions breaks CI automation, label routing, or reviewer assignment that the template encodes.

## hosted service-Specific Example

**Correct (preserves quick actions and variables):**

```text
## Summary
- <fill with the actual change>

## Validation
- <fill with checks that actually ran>

%{source_branch}
%{target_branch}
/label ~backend
/assign @reviewer
```

**Broken (dropped quick actions and variables):**

```text
## Summary
- <fill with the actual change>

## Validation
- <fill with checks that actually ran>
```

The broken version loses automatic label application and reviewer assignment that the template encodes.

## hosted service-Specific Pitfall

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| falling back to SKILL.md when `Default.md` exists and no named template was selected | the template is ignored without cause | use `Default.md` as the structural base when it exists |
