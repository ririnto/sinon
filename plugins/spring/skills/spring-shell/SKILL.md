---
name: "spring-shell"
description: "Use this skill when building Spring Shell command-line applications, interactive REPL workflows, validated commands, completion, command availability rules, terminal prompts, and shell-focused tests."
metadata:
  title: "Spring Shell"
  official_project_url: "https://spring.io/projects/spring-shell"
  reference_doc_urls:
    - "https://docs.spring.io/spring-shell/reference/index.html"
  version: "4.0.1"
---

Use this skill when building operational CLIs, administrative shells, interactive command workflows, or shell-focused tests with Spring Shell command registration, option parsing, completion, availability, and terminal-aware input or output.

## Boundaries

Use `spring-shell` for command registration, command grouping, option syntax, validation, completion, availability checks, shell-specific prompting, terminal-facing output, and shell-focused testing.

- Use Spring Boot `CommandLineRunner` or `ApplicationRunner` for one-shot startup jobs that do not expose an interactive command surface.
- Keep durable batch execution, restartable jobs, and large-scale scheduled processing outside this skill's scope.
- Keep business workflows outside the command class. The shell layer should parse input, enforce availability, and delegate to application services.
- Keep guided flows, TUI views, and custom prompt styling outside the ordinary path. Open the references only when the command-and-option model is not enough.

## Common path

The ordinary Spring Shell job is:

1. Define the CLI task boundary and keep domain logic in application services.
2. Register a small command group with explicit names, help text, and option defaults.
3. Add validation, completion, and availability rules close to the command signature.
4. Use `CommandContext` input and output APIs when a command needs interactive reads or terminal-aware writes.
5. Keep success output, help output, and exit behavior stable enough for automation and tests.
6. Add shell-focused tests that prove parsing, validation, help, and command dispatch.

### Branch selector

- Stay in `SKILL.md` for the ordinary command path: annotation-based commands, command grouping, built-in help behavior, option defaults, validation, completion, availability, `CommandContext` reads and writes, stable output, exit-status decisions, and shell-focused tests.
- Open [references/interactive-flows-and-terminal-ui.md](references/interactive-flows-and-terminal-ui.md) when the task needs guided multi-step flows, selection widgets, confirmations, or richer TUI behavior.
- Open [references/prompt-and-styling.md](references/prompt-and-styling.md) when the shell prompt or output styling must reflect environment, login state, or operator risk.

## Dependency baseline

Use the starter for normal Spring Boot integration. Add the test module whenever command behavior matters enough to lock with executable shell tests.

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.shell</groupId>
        <artifactId>spring-shell-starter</artifactId>
        <version>4.0.1</version>
    </dependency>
    <dependency>
        <groupId>org.springframework.shell</groupId>
        <artifactId>spring-shell-test</artifactId>
        <version>4.0.1</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

## First safe configuration

```yaml
spring:
  shell:
    interactive:
      enabled: true
    script:
      enabled: true
```

Disable interactive mode only for automated script execution or test harnesses that intentionally bypass the REPL loop.

## Built-in commands and help

Spring Shell can expose built-in commands such as `help`, `clear`, `quit`, `script`, and `version`.

- Keep built-ins enabled unless the product has a deliberate operator-experience reason to hide them.
- Keep custom command names distinct from built-ins so help output and command routing stay unambiguous.
- Treat help text as part of the command contract. Keep command names, options, defaults, and examples aligned with what `help` prints.
- Use script mode when support teams need reproducible CLI runs for onboarding, automation, or incident response.

## Coding procedure

1. Start from the user-facing command contract: command name, group, required options, defaults, and terminal output.
2. Default to annotation-based command registration for ordinary shells. Use programmatic registration only when commands must be assembled dynamically at runtime.
3. Keep command methods thin. Parse input in the shell layer and delegate real work to an injected service.
4. Use `@Option` defaults, arity, and required flags to make invalid input fail early.
5. Add Bean Validation, completion, and `Availability` checks as close as possible to the command boundary.
6. Use `CommandContext` for interactive reads and terminal-aware writes instead of `System.in` or `System.out`.
7. Make success output deterministic and define non-zero exit behavior explicitly when operators or automation depend on it.
8. Add shell tests that prove command execution, help output, invalid input handling, and availability behavior.
9. Add a Spring Boot end-to-end test when startup wiring, profiles, or full application context integration matter.

## Implementation examples

### Command group with explicit help and options

```java
@Command(group = "catalog")
class CatalogCommands {
    private final CatalogService catalog;

    CatalogCommands(CatalogService catalog) {
        this.catalog = catalog;
    }

    @Command(
        command = "item add",
        description = "Add an item",
        help = "Adds an item to the catalog with an explicit SKU and optional quantity."
    )
    String add(
            @Option(longNames = "sku", required = true) String sku,
            @Option(longNames = "quantity", defaultValue = "1") int quantity) {
        catalog.add(sku, quantity);
        return "added sku=%s quantity=%d".formatted(sku, quantity);
    }
}
```

### Command context input and output

```java
@Command(command = "item note", description = "Attach a free-form note")
String note(CommandContext ctx, @Option(longNames = "sku", required = true) String sku) {
    String note = ctx.inputReader().readInput("note: ");
    ctx.outputWriter().println("saving note for " + sku);
    ctx.outputWriter().flush();
    return "saved";
}
```

Use `readPassword("prompt: ")` instead of `readInput(...)` when the operator enters a secret.

### Availability rule for environment-sensitive commands

```java
@Command(group = "cluster")
class ClusterCommands {
    private final ClusterSession clusterSession;

    ClusterCommands(ClusterSession clusterSession) {
        this.clusterSession = clusterSession;
    }

    @Command(command = "cluster status", description = "Show current cluster status")
    String status() {
        return clusterSession.currentStatus();
    }

    public Availability statusAvailability() {
        return clusterSession.isConnected()
            ? Availability.available()
            : Availability.unavailable("run 'cluster connect' first");
    }
}
```

### Completion for bounded option values

```java
@Bean
ValueProvider environmentValueProvider() {
    return completionContext -> Arrays.asList("local", "staging", "prod").stream()
        .filter(candidate -> candidate.startsWith(completionContext.currentWordUpToCursor()))
        .map(CompletionProposal::new)
        .toList();
}

@Command(command = "cluster connect", description = "Connect to a target environment")
String connect(@Option(longNames = "env", valueProvider = "environmentValueProvider") String environment) {
    return "connected %s".formatted(environment);
}
```

### Exit-status mapping shape

```java
@Bean
ExitStatusExceptionMapper exitStatusExceptionMapper() {
    return exception -> exception instanceof IllegalArgumentException ? 2 : 1;
}
```

Use explicit exit-status mapping only when scripts or operators depend on a stable non-zero contract.

## Output contract

Return:

1. The registered command shape, including names, groups, help text, options, defaults, and availability behavior
2. The terminal output contract for success, validation failure, unavailable-command paths, and any explicit non-zero exit behavior
3. The completion or prompt behavior when the command depends on bounded values or operator context
4. The shell-focused test shape proving parsing, help, dispatch, and deterministic output
5. Any blocker that requires guided flows, prompt styling, or richer terminal UI beyond the ordinary command path

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

## Testing checklist

- Verify a valid command dispatches to the correct service and returns deterministic terminal output.
- Verify required options, default values, and invalid values produce the expected shell error.
- Verify availability rules hide or reject commands until prerequisites are satisfied.
- Verify completion suggestions only return values valid for the current environment or state.
- Verify help output stays aligned with the registered command names, groups, and options.
- Verify non-zero exit behavior only where the shell contract explicitly defines it.
- Add a `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, useMainMethod = SpringBootTest.UseMainMethod.ALWAYS)` path when full application wiring matters beyond `@ShellTest`.

### Example command verification shape

```bash
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

## References

- Open [references/interactive-flows-and-terminal-ui.md](references/interactive-flows-and-terminal-ui.md) when the task needs guided flows, confirmations, selectors, or richer terminal UI components beyond ordinary command registration.
- Open [references/prompt-and-styling.md](references/prompt-and-styling.md) when the prompt or output styling must surface environment or risk information.
