# Plans

## Purpose

`PLANS.md` is the project-wide development plan.
It covers the long-horizon roadmap, milestone-level scope, target outcomes per milestone, and sequencing rationale.
It documents *what* the project intends to build and in what order.
`CONTRIBUTING.md` documents how work moves through the repository.
This is not a list of currently in-flight tasks.
Those are maintained in `docs/exec-plans/` with execution status metadata.
It is not a tech-debt tracker.
That lives at `docs/exec-plans/tech-debt-tracker.md`.

## Roadmap

Replace the example milestones below with your own.
Keep the columns and status values.

| Milestone | Target outcome | Status | Owner | Notes |
| --- | --- | --- | --- | --- |
| M1: Discovery and product spec | Validated product spec under `docs/product-specs/` | shipped | project-owner | |
| M2: Architecture and contracts ready | `ARCHITECTURE.md` + validator green | shipped | project-owner | |
| M3: Internal beta with daily-driver agents | End-to-end agent workflow with hooks + CI green | in flight | project-owner | |
| M4: External alpha | Opt-in alpha users, observability and feedback loop | planned | project-owner | |
| M5: General availability | SLOs met for one full month + retention dashboards green | planned | project-owner | |

## Sequencing Rationale

- M1 precedes everything because the repository needs a product spec to validate against.
  - See `docs/product-specs/`.
- M2 precedes M3 because the validator must be green before any agent-driven implementation lands.
  - See `docs/design-docs/`.
- M4 precedes M5 because external alpha feedback is required to confirm the SLOs targeted at GA.
  - See `docs/RELIABILITY.md`.

## Dependencies

- M3 depends on M2 because the validator gates every implementation change.
- M4 depends on observability landing in M3 because alpha users will surface failures that need traces.
- M5 depends on SLO definitions in `docs/RELIABILITY.md` being green for the GA window.

## When To Update

- When a milestone is added, removed, reordered, or redefined.
- When target outcomes shift.
- When cross-milestone dependencies appear or resolve.

## Required Evidence

- Cite the product spec, design document, or decision record that motivates each milestone.
- Link to the matching execution-plan entry under `docs/exec-plans/` when a milestone enters concrete execution.
- Link to completed execution-plan entries in `docs/exec-plans/` when a milestone ships.
