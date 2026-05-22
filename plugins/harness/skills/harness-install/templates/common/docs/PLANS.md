# Plans

Document the project's overall development plan: the long-horizon roadmap, the milestones that compose it, and the rationale behind sequencing.

## Purpose

Give agents and humans a single page to scan for the *whole* development plan — strategic direction, milestone-level scope, target outcomes per milestone, and the dependencies between milestones. Heavyweight, multi-phase execution plans for individual milestones MUST live under `docs/exec-plans/active/` following the `yyyy-MM-dd-<slug>.md` convention.

This document is the project-wide plan, not a list of currently in-flight tasks. `docs/exec-plans/active/` carries the concrete phase-by-phase execution. `docs/exec-plans/tech-debt-tracker.md` records work the team consciously deferred with retirement criteria. `WORKFLOW.md` documents *how* work moves through the repository, while `PLANS.md` documents *what* the project intends to build and in what order.

## When To Update

Update when the overall roadmap changes: a milestone is added, removed, reordered, or redefined; a target outcome shifts; a cross-milestone dependency appears or resolves; or the strategic direction of the project moves.

## Required Evidence

- Cite the product spec, design document, or decision record that motivates each milestone.
- Link to the matching `docs/exec-plans/active/` file when a milestone enters concrete execution.
- Link to `docs/exec-plans/completed/` files when a milestone has shipped.
