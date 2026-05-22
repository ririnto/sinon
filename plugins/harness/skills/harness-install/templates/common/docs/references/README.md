# References

This directory holds reference material that supports agent and reviewer decisions: framework documentation snapshots, design-system specs, deployment guides, or domain-specific knowledge that does not live in the source code.

Reference files are project-owned. The plugin ships only this README as a placeholder seed; it does NOT bundle third-party prose (LLM context files, vendor docs, external articles) because that would (a) inflate every install, (b) age silently, and (c) raise distribution-license risk for material the plugin does not own.

## When To Add A Reference

Add a reference here when an agent or reviewer needs a stable, offline copy of external context that the target project actually depends on — for example:

- A captured snapshot of a framework's "LLM context" page when the project pins a specific framework version.
- A design-system spec for the component library the project consumes.
- A relevant external article whose ideas the team chose to apply (cite the source URL and capture date; respect the source's license before storing prose verbatim).

## Naming

- Use lowercase kebab-case filenames.
- Suffix snapshot files with `-llms.txt` (or `-llms.md`) when the file is meant to be loaded directly into an agent's context window.
- Begin each reference file with metadata: source URL, capture date in `yyyy-MM-dd`, freshness expectation, and the license under which the snapshot was captured.

## When To Remove

Remove a reference when the upstream source is dead, when the project no longer depends on the referenced framework or library, or when the snapshot has drifted enough that keeping it misleads agents.
