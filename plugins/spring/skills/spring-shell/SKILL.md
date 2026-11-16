---
metadata:
  reference:
    Spring Shell:
      version: 4.0.3
      url:
        - https://docs.spring.io/spring-shell/reference/
        - https://repo.maven.apache.org/maven2/org/springframework/shell/spring-shell-starter/maven-metadata.xml
        - https://repo.maven.apache.org/maven2/org/springframework/shell/spring-shell-starter/4.0.3/spring-shell-starter-4.0.3.pom
name: spring-shell
description: >-
  Use Spring Shell for interactive command parsing, validation, completion, terminal output, and tests.
---

# Spring Shell

## Purpose

Keep command registration, parsing, validation, availability, completion, terminal interaction, and shell tests focused on the CLI boundary while application services hold workflow logic.

The `4.0.3` versions below illustrate one Spring Shell release pairing.
For new dependencies, check `org.springframework.shell:spring-shell-starter` and `org.springframework.shell:spring-shell-test` on Maven Central for the latest stable compatible release.
Keep an existing BOM or version-catalog pin unless the task authorizes changing it.

Spring Shell 4.0 is based on Spring Framework 7 and requires Spring Boot 4+.

## Boundaries

Use `spring-shell` for command registration, command grouping, option syntax, validation, completion, availability checks, shell-specific prompting, terminal-facing output, and shell-focused testing.

- Use Spring Boot `CommandLineRunner` or `ApplicationRunner` for one-shot startup jobs that do not expose an interactive shell API.
- Keep durable batch execution, restartable jobs, and large-scale scheduled processing outside this skill's scope.
- Keep business workflows outside the command class.
  - The shell layer should parse input, enforce availability, and delegate to application services.
- Keep guided flows, TUI views, and custom prompt styling outside the ordinary path.
  - Open the interactive-flow and styling references when the command-and-option model is not enough.

## Surface map

- For command registration, availability, or completion, read [command examples](references/commands-and-annotation-examples.md).
- For headless or scripted execution, read [setup and execution](references/setup-and-execution.md).
- When options do not suffice for guided flows or selection widgets, read [interactive flows and terminal UI](references/interactive-flows-and-terminal-ui.md).
- For environment, login, or operator risk signaling, read [prompt and styling](references/prompt-and-styling.md).

## Task scope

Preserve the existing command names, option syntax, output, and exit behavior unless the task requires changes.
Use the sections and references for the affected command, execution mode, or terminal interaction.

## Dependencies and execution

Use `spring-shell-starter` for normal Boot integration and `spring-shell-test` for command tests.
Without Boot, use `spring-shell-core` and `@EnableCommand` for annotated command discovery; `spring-shell-jline` adds completion, history, and rich text.
Keep interactive mode enabled for a REPL; disable it only for script execution or harnesses that bypass the REPL.
Spring Shell 4.0.3+ runs command-line commands non-interactively automatically.
Set `spring.shell.debug.enabled=true` for command-error stack traces.

For dependency declarations, non-Boot setup, runner selection, configuration properties, or executable commands, read [setup and execution](references/setup-and-execution.md).

## Built-in commands and help

Spring Shell exposes built-in commands: `help`, `clear`, `exit`, `quit`, `script`, and `version`.

The `stacktrace` and `completion` built-in commands were removed in 4.0.
Use debug mode (`spring.shell.debug.enabled=true`) instead of `stacktrace`.
Shell-specific completion setup is left to the user's preferred shell.

- Keep built-ins enabled unless the product has a deliberate operator-experience reason to hide them.
- Keep custom command names distinct from built-ins so help output and command routing stay unambiguous.
- Treat help text as part of the command contract.
  - Keep command names, options, defaults, and examples aligned with what `help` prints.
- Use script mode when support teams need reproducible CLI runs for onboarding, automation, or incident response.

## Implementation guidance

- Define the affected command's name, group, options, defaults, help text, and terminal output.
- Default to annotation-based command registration unless native compilation or dynamic registration requires the programmatic path.
- Keep command methods thin and delegate real work to an injected service.
- Use `@Option` defaults, required flags, and Bean Validation to reject invalid input at the command boundary.
  Add completion and `Availability` checks when bounded values or runtime prerequisites require them.
- Use `CommandContext` for interactive reads and terminal-aware writes instead of `System.in` or `System.out`.
- Make success output deterministic and define non-zero exit behavior when operators or automation depend on it.

## Command examples

For command groups, option and argument annotations, programmatic registration, terminal interaction, availability, completion, exit status, or shell tests, read [commands and annotation examples](references/commands-and-annotation-examples.md).

## Output contract

Report the changed command contract, relevant terminal behavior, and verification results.
Identify unverified interactive or startup behavior and any blocker.
Follow the task's required response format.

## Output shapes

### Stable success output

```text
added sku=SKU-1 quantity=2
```

### Availability failure output

```text
Command 'cluster status' exists but is not currently available because run 'cluster connect' first
```

### Help-oriented command layout

```text
catalog item add --sku <string> [--quantity <int>]
```

### Non-zero exit behavior shape

```text
exit code: 2
```

## Verification

Use existing tests for the changed command contract and regression risks.
Select the checks that apply:

- Verify a valid command dispatches to the correct service and returns deterministic terminal output.
- Verify required options, default values, and invalid values produce the expected shell error.
- Verify availability rules hide or reject commands until prerequisites are satisfied.
- Verify completion suggestions only return values valid for the current environment or state.
- Verify help output stays aligned with the registered command names, groups, and options.
- Verify non-zero exit behavior only where the shell contract explicitly defines it.
- Add a `@ShellTest` path when parsing, dispatch, and terminal output are the real contract under test.
- Add a `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, useMainMethod = SpringBootTest.UseMainMethod.ALWAYS)` path when full application wiring matters beyond `@ShellTest`.

### Example command verification shape

```sh
printf 'item add --sku SKU-1\nexit\n' | java -jar build/libs/app.jar
```

Expected output shape:

```text
added sku=SKU-1 quantity=1
```

## Production checklist

- Keep interactive shell commands idempotent or explicitly confirm destructive actions before execution.
- Avoid leaking secrets into terminal output, history files, or script logs.
- Keep success and error output machine-greppable when operators will wrap the shell in automation.
- Document which commands require an active connection, login, or environment selection before release.
- Prefer bounded completion providers and controlled help text over ad hoc parsing or reflective command generation.

## Known limitations

- Declarative annotation-based command registration is not supported for GraalVM native compilation in 4.0.x.
  - Use the programmatic `Command` bean approach for native images.
