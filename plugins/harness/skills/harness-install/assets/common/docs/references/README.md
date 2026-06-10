# References

## Purpose

`docs/references/` is the system of record for external documents that agents need to access offline. References are verbatim upstream content with attribution and an editor's note documenting repository-local deviations (such as CLAUDE.md vs. AGENTS.md naming, or .agents vs. .claude directory symlinks). The validator excludes this directory from leak and scaffold-token checks precisely because upstream may contain `TODO`, `{{...}}`, or other tokens that agents must not alter.

## Shipped References

- `openai-harness-engineering.md` — Harness engineering: leveraging Codex in an agent-first world (OpenAI Engineering blog, 2026-02-11). Source: `https://openai.com/index/harness-engineering/`.
- `symphony-spec.md` — Symphony Service Specification (language-agnostic). Source: `https://github.com/openai/symphony`.

## Adding a New Reference

1. Create a new file `docs/references/<slug>.md` (or `.txt` for plain text).
2. Prepend the file with the NOTE-style attribution block: `<!-- @formatter:off -->`, then `> [!NOTE]` with Source, Original URL, Raw URL, verbatim caveat, and editor's note on root-contract naming and symlinks.
3. Reproduce the upstream body verbatim. Add an editor's note only for documented local deviations.
4. Keep references self-contained: leave external links in place but do not depend on them at runtime — references must be readable offline.
5. Update `## Shipped References` above with the new file.

## Template

See `docs/templates/docs/reference-llms.txt` for the canonical attribution skeleton. Use the template unchanged for the attribution block; only the verbatim body changes.

## Validation

- The validator excludes this directory from leak checks.
- Manual review by another agent or human is the only quality gate.
- The editor's note must document every deviation from upstream.
