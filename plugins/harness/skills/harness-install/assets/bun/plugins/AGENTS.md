# Bun Oxlint Plugins

This directory implements the custom Oxlint plugins used by the Bun target.

## Project Structure

### Plugin Ownership

`style-plugin.ts` owns parser-based checks for blank lines and non-documentation inline comments inside function bodies.
`tsdoc-plugin.ts` owns parser-based TSDoc checks for exported TypeScript declarations and public class methods.
`oxlint.config.ts` owns the configured plugin rule names and diagnostics.

## Build, Test, and Development Commands

### Verification

Define no plugin-local command layer.
Exercise both valid and violating AST shapes when tests exist, and inspect the resulting diagnostics rather than snapshots of prose.

## Coding Style and Testing

### Local Constraints

Keep parser rules deterministic, bounded to the AST and source text supplied by Oxlint, and explicit about diagnostics.
Preserve typed plugin interfaces and return actionable rule failures without suppression.
Use OXC node narrowing, stable source ranges, and deduplicated diagnostics.

## Security and Configuration

Do not execute repository commands or access credentials from syntax-only plugins.
Keep rule configuration in `oxlint.config.ts` and do not add alternate plugin names or diagnostic policies.
