# Maven reactor sample

This project builds a catalog module and a checkout module that consumes the catalog.
The root POM manages dependency and plugin versions for both modules.
Java 25, Node.js 24.22.3 in the 24 series, and pnpm 12.21.0 are required.

Run `pnpm install --frozen-lockfile` to install the baseline checks and Git hooks.

Run `./mvnw verify` from the project root to check formatting, Checkstyle, compilation, and tests for the entire reactor.
Run `./mvnw -pl checkout -am test` to build and test checkout with its reactor dependencies.
Run `pnpm run pre-commit` for Markdown and Maven lint checks, or `pnpm run pre-push` for Markdown and the complete reactor verification.
