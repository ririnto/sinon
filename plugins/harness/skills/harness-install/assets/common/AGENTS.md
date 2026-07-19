# Repository Guidelines

This file defines the installed repository safety and precedence contract for any agent runtime.
Normative requirements use BCP 14 keywords as defined by RFC 2119 and RFC 8174.
`WORKFLOW.md` defines target lifecycle policy, orchestration, worker selection, and runtime fallbacks.

## Target Repository Structure

Preserve existing named top-level components.
Each top-level component MUST have one documented responsibility.
Adding, removing, or moving a top-level component MUST update architecture, dependencies, consumers, and relevant documentation.
Do not add legacy parallel surfaces or compatibility shims without an external contract.
Use installed skills only for their named tasks.
Keep durable documents under `docs/` and use `docs/templates/` for new project artifacts.
Do not edit credentials, local environment files, caches, or vendored code unless the user names them.

## Documentation Ownership

`README.md` is the concise project entry point and MUST link to the checks, runbooks, and architecture guidance that users need.
`AGENTS.md` owns repository boundaries, local ownership, security rules, and checks for agents.
`WORKFLOW.md` owns delivery lifecycle, delegation, validation gates, and approval boundaries.
`ARCHITECTURE.md` owns current and planned system structure, dependency direction, data flow, and verification boundaries.
Do not duplicate one document's durable guidance in another document.

## Architecture Updates

Update `ARCHITECTURE.md` in the same logical change when a domain responsibility, dependency direction, public internal boundary, durable-state owner, transaction boundary, recovery authority, external-effect protocol, or verification boundary changes.
Routine implementation, test, formatting, and internal refactoring changes that preserve those boundaries MUST NOT cause architecture-document churn.

## Build, Test, and Development Commands

Use the project’s setup and development commands instead of inventing replacements.
Report changed files, validation evidence, skipped checks, and risks.
Name each unavailable command and explain why it was not run.

## Coding Style and Testing

Use English Markdown headings, language-tagged fences, and ASCII tree markers.
Test executable behavior at the smallest useful scope.
Use unit tests by default.
Use integration tests only when behavior requires a real process, database, network, filesystem boundary, container, or framework runtime.
Use end-to-end tests only for distinct core user journeys that lower-level tests do not already prove.
Review prose guidance directly instead of testing its wording or file layout.
The 60/30/10 split is a suite-budget guide: unit tests should carry most behavioral coverage, integration tests belong at real infrastructure boundaries, and end-to-end tests cover only distinct critical journeys.
The ratio is not line coverage, a per-feature quota, or a requirement to test one behavior at every layer.

## Security and Configuration

Review hooks, scripts, MCP, settings, and generated assets for command, filesystem, network, credential, and publication risks.
For changes that can trigger external behavior, identify the actor, boundary, and affected target.
