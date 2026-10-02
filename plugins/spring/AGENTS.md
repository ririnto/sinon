---
metadata:
  reference:
    OpenAI Codex Skills:
      url: https://developers.openai.com/codex/skills.md
    Rethinking Skills and Prompts:
      url: https://developers.openai.com/blog/rethinking-skills-and-prompts-for-gpt-6-astra.md
---

# Repository Guidelines

Open the affected skill before changing examples or version-specific guidance.

## Release Sources

Verify release-sensitive claims against official Spring documentation and published artifact metadata.
Keep release facts in the owning skill.
Record the exact source used for a changed compatibility claim.

## Coding Style and Testing

State the target release when a claim is pinned.
Otherwise use version-line wording.
Keep BOM-managed dependency examples versionless.
Use JUnit 6 as the default test line and keep JUnit 5 only for documented Boot 3.5 or Spring statemachine paths.
Keep Java method declarations, calls, and related assertions compact.
