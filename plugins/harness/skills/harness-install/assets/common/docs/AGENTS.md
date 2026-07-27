# Documentation Guidelines

This directory holds durable project documentation, templates, references, and generated records for the target repository.

## Project Structure

### Ownership

Put durable project guidance, decisions, plans, references, and generated records under the matching `docs/` category.
Use `docs/templates/` for new document types and preserve each template's machine-consumed structure.
Keep roadmap milestones in `docs/PLANS.md` and bounded execution work in `docs/exec-plans/`.

### Generated Outputs

Keep generated outputs under `docs/generated/`.

## Build, Test, and Development Commands

Inspect generated artifacts and document links in the exact diff.

## Coding Style and Testing

### Review

Review prose for accurate links and semantic line breaks.
Keep external contracts in their source documents, including `docs/references/symphony-spec.md`.
Validate document structure and links rather than testing prose wording or file layout as runtime behavior.

## Security and Configuration

Record the source, inputs, freshness, and regeneration trigger for each generated document.
Do not hand-edit generated output when its source exists.
Do not place private data in durable documentation.
Preserve external contract wording and links unless the owning source document changes.
