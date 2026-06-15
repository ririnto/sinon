---
name: scripts-guidance
description: |-
  Decide whether a helper belongs in a skill's `scripts/` directory and avoid making scripts hidden prerequisites.
  Open this file when planning helper scripts or auditing a draft script for skill self-sufficiency.
---

# Scripts Guidance

Open this file when you are deciding whether a helper belongs in `scripts/`, or when a draft script risks becoming a hidden prerequisite for the skill.

## When to add a script

Add a script only when the repeated step is deterministic, non-interactive, and clearer as code than as prose.

Good fits:

- validate a schema or document structure
- normalize generated files into a stable format
- package assets into a repeatable output
- collect deterministic reports

Poor fits:

- browser-driven steps
- required login or network setup
- host-specific wrappers
- interactive review loops
- tasks performed only once during authoring

## Script contract

A script in a skill should be:

- non-interactive
- deterministic
- narrowly scoped to one support task
- optional for understanding the ordinary path
- documented by a short pointer in `SKILL.md`

## Documentation pattern

Mention the script in `SKILL.md` like this:

```markdown
- `scripts/validate.py` - Run when a deterministic structure check is safer as code than prose.
```

Do not write the main workflow so that readers must inspect the script source before they can follow the ordinary path.

## Dependency rule

Prefer runtimes and tools that are already common for the target environment.
Do not invent a new required CLI or hosted validator just to support the script.

Python scripts that run directly should start with `#!/usr/bin/env -S uv run`, then `# -*- coding: utf-8 -*-`, then a PEP 723 metadata block before imports.
Use PEP 508 major-compatible dependency ranges for external packages, such as `package>=1.2.3,<2`, and use `dependencies = []` for stdlib-only direct scripts when an explicit declaration is needed.

JavaScript and TypeScript scripts that run directly should start with `#!/usr/bin/env bun`, then `// -*- coding: utf-8 -*-`.
Declare external dependencies with major-compatible import specifiers, such as `import * as cheerio from "cheerio@^1.0.0"`.
Scripts that use only the JavaScript standard library or Bun built-ins need no dependency metadata block.

Shell scripts have no equivalent dependency metadata block.
Document required commands beside the shell invocation or in `SKILL.md`.

## Example decisions

### Good decision

```text
Need: Verify that every example file contains required frontmatter keys.
Choice: Add scripts/validate.py because the check is deterministic and repetitive.
```

### Bad decision

```text
Need: Explain how to choose a good description.
Choice: Add a validator service or web-based scoring tool.
```

That second case belongs in prose and checklists, not in a mandatory script.
