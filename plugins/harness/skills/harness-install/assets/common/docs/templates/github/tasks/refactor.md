# Refactor

Propose behavior-preserving internal code or architecture changes.

Suggested title prefix: `refactor: ` — Label: `refactor`

## Target

Name the code, module, boundary, or architecture area. (Required)

## Motivation

Explain why the refactor is needed. (Required)

## Proposed Change

Describe the internal structure change. (Required)

## Scope

Choose one: (Required)

- Broad: multiple modules or layers
- Medium: one module with several files
- Narrow: one file or package
- Local: implementation details only

## Behavior Preservation

State how existing behavior will be proven unchanged. (Required)

- [ ] Existing tests pass
- [ ] Public API behavior checked
- [ ] Manual QA completed when user-visible

## Interfaces and Handoffs

Name public APIs, schemas, types, contracts, or docs that must remain aligned. (Required)

- Public API to preserve:
- Internal types changed:
- Compatibility to verify:

## Acceptance Criteria

- [ ] ... (Required)

## Validation Plan

Name the exact test, command, comparison, or manual QA action. (Required)

## Risks

Note regression, compatibility, migration, or rollout risks. (Required)

## Rollback Plan

Describe how to return to the previous structure if needed. (Optional)
