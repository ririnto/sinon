# Plans

Document near-term sequencing of in-flight work — initiatives that are currently being built, paused, or about to start. This is the active counterpart of `docs/exec-plans/`: lightweight items that do not yet warrant a dedicated `yyyy-MM-dd-<slug>.md` plan file but still need to be visible.

## Purpose

Give agents a single page they can scan to learn what is in flight right now, what is paused (and why), and which checkpoint each item is waiting on. Heavyweight, multi-phase plans MUST live under `docs/exec-plans/active/` following the `yyyy-MM-dd-<slug>.md` convention.

`docs/exec-plans/tech-debt-tracker.md` is a separate concern: it tracks consciously *deferred* work that the team chose NOT to do, with retirement criteria. PLANS.md tracks work that is *being done* or *staged to start*; the tech-debt tracker records work that is *intentionally postponed*.

## When To Update

Update when the scope of an in-flight initiative shifts, when a cross-team dependency forms, when a checkpoint passes, or when an item leaves the page (graduates into a tracked exec-plan file, ships, or is deferred into the tech-debt tracker).

## Required Evidence

- Link to the relevant `docs/exec-plans/active/` file when an item graduates into a tracked plan.
- Cite the issue, PR, or message where the sequencing decision was made.
