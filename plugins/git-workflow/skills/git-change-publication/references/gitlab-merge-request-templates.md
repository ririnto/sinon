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

## Default vs. Named Templates

GitLab resolves merge request templates in two distinct ways:

| Scenario | What GitLab does |
| --- | --- |
| User selects no template explicitly | Uses `Default.md` when it exists; otherwise uses a blank body |
| User selects a named template (e.g., `feature.md`) | Uses only that file; `Default.md` is ignored |
| No `.gitlab/merge_request_templates/` directory | Falls back to the SKILL.md fallback body |

This means `Default.md` is the baseline only when it will actually be applied. A named template does not inherit from `Default.md`.

## Default.md Fallback Boundary

Use the SKILL.md fallback body when:

- `.gitlab/merge_request_templates/` does not exist or contains no `.md` files

Do not fall back when `Default.md` exists and the author has not selected a different template.
If the MR author explicitly selects a named template that you cannot locate, report the template as unconfirmed instead of silently switching to the generic fallback.

## Preservation Rules

- Look under `.gitlab/merge_request_templates/` for available templates.
- Treat `Default.md` as the default local template shape when it exists.
- Preserve the existing section order, headings, checklists, and placeholder intent.
- Preserve GitLab variables such as `%{source_branch}` or `%{target_branch}` when the template already uses them.
- Preserve GitLab quick actions such as `/label` or `/assign` when the template already uses them.
- Mark checkboxes only when the statement is actually true.
- Do not delete required headings just because one section is brief.

### Variable Preservation

GitLab substitutes these variables at MR creation time:

```text
%{source_branch}   %{target_branch}   %{url}   %{title}   %{id}
```

If the template already contains `%{source_branch}` or `%{target_branch}`, keep them verbatim. Do not replace them with static text. Do not add new variable references unless the template already demonstrates them.

### Quick-Action Preservation

GitLab quick actions are processed when the MR is created, not during template rendering:

```text
/label ~backend      /assign @username      /milestone %"1.0"
/target_branch master      /title "Fix: "
```

These are not comments. If the template already uses quick actions, preserve them. Dropping quick actions breaks CI automation, label routing, or reviewer assignment that the template encodes.

## GitLab-Specific Example

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

## GitLab-Specific Pitfall

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| dropping GitLab variables or quick actions from an existing template | the MR loses automation or context encoded by the template | preserve the template body shape instead of rewriting it |
| treating `Default.md` as a section header library for named templates | GitLab uses only the selected template; it does not merge with `Default.md` | treat each template as a standalone document |
| falling back to SKILL.md when `Default.md` exists and no named template was selected | the template is ignored without cause | use `Default.md` as the structural base when it exists |
