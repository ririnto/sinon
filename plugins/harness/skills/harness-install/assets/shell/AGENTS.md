# Repository Guidelines

## Project Structure

`ARCHITECTURE.md` owns system boundaries, dependency direction, data flow, and verification boundaries.

## Build, Test, and Development Commands

Run ShellCheck at warning severity on Git-tracked `*.sh` files with `git ls-files -z -- '*.sh' | xargs -0 -r shellcheck -S warning --`.
Run `git ls-files -z -- '*.sh' | xargs -0 -r shfmt -d --` to check Git-tracked `*.sh` files and `git ls-files -z -- '*.sh' | xargs -0 -r shfmt -w --` to apply approved shell formatting fixes.
Run Markdownlint directly when it is available, and warn and skip when Markdownlint is missing.
Missing shfmt fails visibly.

## Coding Style and Testing

Use portable POSIX `sh` for shell scripts.
Use option terminators when passing Git-tracked paths to analyzers.

## Security and Configuration

Review every shell command for filesystem, process, network, credential, and publication effects before changing it.
Preserve null-delimited handling for names containing whitespace or shell metacharacters.
