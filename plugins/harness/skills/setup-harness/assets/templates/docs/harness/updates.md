# Harness Updates

Record harness config, validation, and scaffold changes for {project-name} so future agents can distinguish intentional customization from drift.

## Update Log

| Date | Change | Reason | Owner | Validation |
| --- | --- | --- | --- | --- |
| {date} | {config, script, doc, CI, or hook change} | {why the harness changed} | {owner} | `{command or evidence}` |

## Pending Updates

| Update | Blocker | Owner | Target |
| --- | --- | --- | --- |
| {needed harness change} | {missing decision, tool, or cleanup} | {owner} | {date or release} |

## Compatibility Notes

- {local tool version, CI image, shell, or runtime constraint that affects harness checks}
- {target-owned customization that should be preserved on reinstall}

## Regeneration Notes

When refreshing harness files, preserve target-owned edits unless the owner explicitly chooses replacement. Record each copied file, skipped conflict, and validation result here.
