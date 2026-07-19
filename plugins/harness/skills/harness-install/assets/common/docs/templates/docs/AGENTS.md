# Repository Guidelines

This file applies to {{scope_description}}. A closer `AGENTS.md` overrides it for its subtree. Read {{primary_context}} before changing this area.

## Project Structure

{{project_structure}}

## Build, Test, and Development Commands

Run {{setup_command}} when setup is required. Run {{build_command}} for builds, {{development_command}} for local development, and {{validation_command}} before handoff.

## Coding Style and Testing

Follow {{style_source}}.
Use unit tests by default.
Use integration tests only when behavior requires a real process, database, network, filesystem boundary, container, or framework runtime.
Use end-to-end tests only for distinct core user journeys that lower-level tests do not already prove.
Review prose guidance instead of testing its wording or file layout.
Use {{testing_surface}} for executable behavior in this scope.

## Commit and Publication

Stage only {{ownership_scope}}. {{commit_and_review_rule}}

## Security and Configuration

{{security_or_configuration_rule}}
