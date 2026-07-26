# Repository Guidelines

Harness contains agent-readable composition guidance and packaged target resource bundles.

## Bundle Boundary

`skills/harness-install/SKILL.md` owns composition and configuration guidance.
`skills/harness-install/assets/` contains flat common, tool, and environment bundles.

- `common/` contains only resources copied to every target.
- `common/` MUST NOT contain root `AGENTS.md` or `CLAUDE.md`.
- Each tool bundle owns its root `AGENTS.md`, root `CLAUDE.md`, and native configuration.
- `github/` owns `.github/` resources.
- `gitlab/` owns `.gitlab/` resources.

Chosen bundles are copied as complete trees.
Do not add file-level selection, generated command layers, or parallel setup surfaces.

## Change Discipline

Keep packaged target content reproducible and package-local.
Do not mirror target-owned files into the marketplace root.
Adding, removing, or moving a resource must update its bundle ownership and consuming guidance.
Preserve existing target content unless replacement is explicitly approved.

## Asset Safety

Review copied hooks, settings, CI resources, commands, and configuration for filesystem, network, credential, and publication risks.
Do not add generated caches, vendored dependencies, or local build output to a bundle.
Run repository checks, plugin validation, and representative whole-bundle copy smoke checks after bundle changes.
