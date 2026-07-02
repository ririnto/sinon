# References

## Purpose

`docs/references/` is the system of record for external documents that agents need to access offline.
References preserve upstream content by default, with attribution and an optional editor's note for required local formatting or repository-rule deviations.
The validator excludes this directory from placeholder-token checks.
Upstream content may contain `TODO`, `{{...}}`, or other tokens that agents must not alter.

## Shipped References

- `openai-harness-engineering.md`: Harness engineering: leveraging Codex in an agent-first world.
  - Source: OpenAI Engineering blog, 2026-02-11.
  - Source: `https://openai.com/index/harness-engineering/`.
- `symphony-spec.md`: Symphony Service Specification (language-agnostic).
  - Source: `https://github.com/openai/symphony`.

## Adding a New Reference

1. Create a new file `docs/references/<slug>.md` (or `.txt` for plain text).
2. Prepend the file with the NOTE-style attribution block.
   - Start with `<!-- @formatter:off -->`.
   - Then add `> [!NOTE]`.
   - Include source, canonical URL, raw URL, and the verbatim caveat.
3. Reproduce the upstream body verbatim unless a repository rule requires a documented local formatting deviation.
   - Add an editor's note only for required local deviations.
4. Keep references self-contained: leave external links in place but do not depend on them at runtime.
   - References must be readable offline.
5. Update `## Shipped References` above with the new file.

## Template

See `docs/templates/docs/reference-llms.txt` for the canonical attribution skeleton.
Use the template unchanged for the attribution block.
Change the body only when upstream changes or the editor's note documents a local terminology deviation.

## Validation

- The validator excludes this directory from leak checks.
- Manual review by another agent or human is the only quality gate.
- The editor's note must document every deviation from upstream.
