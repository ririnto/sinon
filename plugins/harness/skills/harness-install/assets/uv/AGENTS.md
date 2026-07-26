# Repository Guidelines

## Project Structure

`ARCHITECTURE.md` owns system boundaries, dependency direction, data flow, and verification boundaries.
The target is an application environment rather than a packaged Python distribution.

## Build, Test, and Development Commands

Run `uv run ruff check . && uv run ruff format --check .` for validation.
Use uv for execution and dependency resolution instead of alternate Python environments or toolchains.

## Security and Configuration

Review dependency resolution and pre-commit execution for network and filesystem effects before changing their configuration.
