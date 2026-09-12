---
name: review
description: Use when reviewing a diff, pull request, merge request, or completed change for correctness, drift, and rule compliance.
---

# Review

Judge the change against evidence and the target repository's own rules, following the shared rules in `docs/rules.md` (located next to this plugin's `skills/` directory).
This skill owns the review procedure.
`docs/rules.md` owns the rules.
The `implement` skill consumes the same rules.

## Procedure

1. Read the root instruction file the active host actually loads, plus the complete diff and the code around every changed line.
   Do not assume a universal instruction filename.
   Discover it per host.
2. Read the stated requirements before judging.
   Review against what was asked, not against unstated taste.
3. Compare each finding against `docs/rules.md` and the repository's own instruction files.
   Name the file and quote the rule a finding enforces.
4. Run the checks that exercise the changed behavior.
   Record commands with exit codes.
   A check that cannot run stays an explicit named gap, never a silent pass.
5. Report a verdict with findings ranked by severity, each anchored to a file and line.
   State the evidence class behind each verdict: source inspection, automated check, live behavior, or independent review.
   Output fields are recommended choices.
   The task or host requirement wins over any fixed schema.

## Reject As Drift

- Weakened or skipped validation commands, or a check turned into a no-op.
- Edits that mask a failing contract instead of fixing it.
- Accidental deletion, silent error swallowing, or invented values for required identifiers.
- Scope expansion beyond the stated task.
- Documentation left stale where the change alters a documented boundary, workflow, or invariant.
- Full revalidation demanded only because a commit hash changed.
  Require the related-scope check instead.

## Decisions

- Prefer reusing the original implementer's context for fixes when the host supports it.
- Distinguish explicit requirements from your interpretation.
  State assumptions plainly.
- A review fix reruns only the checks in the affected scope, preserving valid evidence for unchanged inputs.

## Update Triggers

Update this skill when the review procedure changes.
Update `docs/rules.md` when a rule changes.
