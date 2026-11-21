# Kotest Source Metadata Compatibility

The Kotest skill's source mapping moved from `metadata.reference` in the [skill entry point](../plugins/kotlin/skills/kotest/SKILL.md) to the same key in the linked [source ledger](../plugins/kotlin/skills/kotest/references/sources.md).
The audit found no consumer that reads that field from the skill entry point within the scope below.
Claude Code and Codex still discover the skill through their plugin interfaces.

## Search scope

- Searched Sinon's tracked scripts, lint rules, document tooling, and both plugin catalogs for Kotest's skill path, the ledger path, and readers of skill frontmatter.
  The skill frontmatter rule checks the structure of the YAML.
  The specification tools limit their frontmatter queries to specification documents.
- Searched the tracked files of local checkouts under the repository owner's GitHub namespace and scripts in locally available Sinon plugin packages and Claude marketplace checkouts.
  References in neighboring projects were marketplace configuration, installation instructions, a project registry entry, and attribution for a separate plugin.
- Searched the signed-in GitHub code index with `gh search code` for `ririnto/sinon`, `kotlin@sinon`, `plugins/kotlin/skills/kotest`, and distinctive moved keys such as `Kotest timeout documentation` and `Kotest isolation modes`.
  Searched for `metadata.reference` in the owner's repositories and in combination with `kotest`.
  The external matches were marketplace references, a project registry entry, and an unrelated Kotest article.
  GitHub listed no forks of Sinon at the time of the audit.

The search covered local files and indexed GitHub results visible to the account that ran it.
Unindexed and inaccessible repositories, other machines, and undisclosed scripts remain outside that scope.

## Observed behavior

The audit used the parent of merge commit `cbf972549997131512c974a354e35bc1083d8ebd`, the merge itself, and the named `main` snapshot at `1d682518522dd0b0bc63f30bb73811f94f03a7c1`.
The two historical snapshots came from `git archive` of the Kotlin package and the marketplace manifests.
Each host read the Kotlin package from each snapshot.

| Reader or check | Observed result |
| --- | --- |
| Claude Code 2.1.330 with `claude plugin validate plugins/kotlin` and `claude --bare --plugin-dir ./plugins/kotlin plugin details kotlin` | Both commands exited 0 on all three snapshots. The component inventory included `kotest` each time. The validator reported the same missing-icon warning and skills-only package note. |
| Codex CLI 0.171.0 with `codex app-server --stdio` and `plugin/read` | Passing each snapshot's `.agents/plugins/marketplace.json` as `marketplacePath` and `kotlin` as `pluginName` returned `kotlin:kotest` with the same description on all three snapshots. |
| Sinon's [skill frontmatter rule](../rules/skill-frontmatter.ts) via `pnpm exec markdownlint-cli2 plugins/kotlin/skills/kotest/SKILL.md plugins/kotlin/skills/kotest/references/sources.md` | Exit 0 with zero diagnostics on current `main`. The rule validates the skill's YAML mapping. |
| The [specification CLI](../plugins/spec-driven-development/README.md) with `get-frontmatter spec <path-to-SKILL.md> --fields metadata` | Exit 1 with `kind=spec requires SPEC.md (got: SKILL.md)` on all three inputs. The frontmatter query accepts specification document filenames. |
| YAML comparison with the repository's `yaml` package | The moved key is absent from the skill at the merge and on current `main`. The ledger contains all 25 mappings with the same parsed values as the original. |

The host checks establish skill discovery and package validation for the versions tested.
The YAML comparison establishes data preservation.
A reader that queries the old key directly would see it missing.
If such a reader is identified, update it to follow the skill's ledger link and extract `metadata.reference` from the ledger.
Verify the correction by running that reader against both layouts and comparing its emitted source records.
