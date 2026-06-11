---
name: spring-shell
description: >-
  Build Spring Shell command-line applications with validated commands, completion, availability rules, terminal prompts, and shell-focused tests. Triggers on command registration with options and validation, tab completion configuration, runtime command availability control, or interactive REPL workflow construction.
---

# Spring Shell

## Purpose

Keep command registration, parsing, validation, availability, completion, terminal interaction, and shell tests focused on the CLI boundary while application services hold workflow logic.

Keep dependency examples on the released Spring Shell line that matches the published Maven artifact metadata for `org.springframework.shell:spring-shell-starter`.

Spring Shell 4.0 is based on Spring Framework 7 and requires Spring Boot 4+.

## Boundaries

Use `spring-shell` for command registration, command grouping, option syntax, validation, completion, availability checks, shell-specific prompting, terminal-facing output, and shell-focused testing.

- Use Spring Boot `CommandLineRunner` or `ApplicationRunner` for one-shot startup jobs that do not expose an interactive command surface.
- Keep durable batch execution, restartable jobs, and large-scale scheduled processing outside this skill's scope.
- Keep business workflows outside the command class. The shell layer should parse input, enforce availability, and delegate to application services.
- Keep guided flows, TUI views, and custom prompt styling outside the ordinary path. Open the references only when the command-and-option model is not enough.

## Surface map

| Surface | Start here when | Open a reference when |
| --- | --- | --- |
| Ordinary commands | one command with options, help text, and deterministic output is enough | stay in `SKILL.md` |
| Availability and completion | a command needs state checks or bounded values | stay in `SKILL.md` |
| Headless or scripted execution | the shell must run without the interactive REPL loop | stay in `SKILL.md` |
| Guided flows or selection widgets | plain options become error-prone or too hard to discover | open [references/interactive-flows-and-terminal-ui.md](references/interactive-flows-and-terminal-ui.md) |
| Prompt or output risk signaling | environment, login state, or operator risk must stay visible | open [references/prompt-and-styling.md](references/prompt-and-styling.md) |

## Common path

The ordinary Spring Shell job is:

1. Define the CLI task boundary and keep domain logic in application services.
2. Register a small command group with explicit names, help text, and option defaults.
3. Add validation, completion, and availability rules close to the command signature.
4. Use `CommandContext` input and output APIs when a command needs interactive reads or terminal-aware writes.
5. Keep success output, help output, and exit behavior stable enough for automation and tests.
6. Add shell-focused tests that prove parsing, validation, help, and command dispatch.

### Branch selector

- Stay in `SKILL.md` for the ordinary command path: annotation-based commands, command grouping, built-in help behavior, option defaults, validation, completion, availability, `CommandContext` reads and writes, stable output, exit-status decisions, headless execution, and shell-focused tests.
- Open [references/interactive-flows-and-terminal-ui.md](references/interactive-flows-and-terminal-ui.md) when the task needs guided multi-step flows, selection widgets, confirmations, or richer terminal UI behavior.
- Open [references/prompt-and-styling.md](references/prompt-and-styling.md) when the prompt or output styling must reflect environment, login state, or operator risk.

## Dependency baseline

Use the starter for normal Spring Boot integration. Add the test artifact whenever command behavior matters enough to lock with executable shell tests.

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.shell</groupId>
        <artifactId>spring-shell-starter</artifactId>
        <version>4.0.3</version>
    </dependency>
    <dependency>
        <groupId>org.springframework.shell</groupId>
        <artifactId>spring-shell-test</artifactId>
        <version>4.0.3</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### Module layout

| Artifact | Role |
| --- | --- |
| `spring-shell-starter` | Spring Boot starter with JLine-based interactive REPL |
| `spring-shell-core` | Command registration, parsing, and execution APIs. Does not depend on Spring Boot or JLine |
| `spring-shell-jline` | JLine console integration. Required for tab completion, command history, and rich text formatting |
| `spring-shell-test` | Shell testing utilities for Spring Boot tests |

### Feature-to-artifact map

| Need | Starter or artifact |
| --- | --- |
| Ordinary `@Command` registration and REPL runtime | `spring-shell-starter` |
| Shell-focused tests | `spring-shell-test` |
| Completion and availability rules | `spring-shell-starter` |
| Guided flows or TUI components | `spring-shell-starter`, then open [references/interactive-flows-and-terminal-ui.md](references/interactive-flows-and-terminal-ui.md) |
| Prompt customization and styled operator output | `spring-shell-starter`, then open [references/prompt-and-styling.md](references/prompt-and-styling.md) |

### Non-Boot usage

When not using Spring Boot, add `spring-shell-core` directly and enable command discovery with `@EnableCommand`:

```java
@EnableCommand(SpringShellApplication.class)
public class SpringShellApplication {
    public static void main(String[] args) throws Exception {
        ApplicationContext context = new AnnotationConfigApplicationContext(SpringShellApplication.class);
        ShellRunner runner = context.getBean(ShellRunner.class);
        runner.run(args);
    }
}
```

Without `@EnableCommand`, annotated commands will not be discovered.

## First safe configuration

### First safe commands

```sh
./mvnw test -Dtest=CatalogCommandsTests
```

```sh
./gradlew test --tests CatalogCommandsTests
```

```sh
printf 'help
exit
' | java -jar build/libs/app.jar
```

```sh
java -jar build/libs/app.jar --spring.shell.interactive.enabled=false catalog item add --sku SKU-1
```

```yaml
spring:
  shell:
    interactive:
      enabled: true
    script:
      enabled: true
```

Keep interactive mode enabled for the ordinary REPL path. Disable it only for automated script execution or harnesses that intentionally bypass the REPL loop.

### Interaction mode

Spring Shell 4.x provides three `ShellRunner` implementations:

| Runner | Mode | Requires |
| --- | --- | --- |
| `JLineShellRunner` | Interactive | `spring-shell-starter` or `spring-shell-jline` |
| `SystemShellRunner` | Interactive | `spring-shell-core` only (no tab completion or history) |
| `NonInteractiveShellRunner` | Non-interactive | `spring-shell-core` only |

The Spring Boot starter uses `JLineShellRunner` by default. Set `spring.shell.interactive.enabled=false` to switch to `NonInteractiveShellRunner`.

When a command is passed on the command line, Spring Shell 4.0.3+ automatically runs in non-interactive mode even without the property.

### Debug mode

Set `spring.shell.debug.enabled=true` to print stack traces on command errors. This replaces the v3 `stacktrace` built-in command.

### Configuration properties

| Property | Default | Effect |
| --- | --- | --- |
| `spring.shell.interactive.enabled` | `true` | Enable interactive REPL; set `false` for single-command execution |
| `spring.shell.script.enabled` | `true` | Enable the `script` built-in command |
| `spring.shell.debug.enabled` | `false` | Print stack traces on command errors |

## Built-in commands and help

Spring Shell exposes built-in commands: `help`, `clear`, `exit`, `quit`, `script`, and `version`.

The `stacktrace` and `completion` built-in commands were removed in 4.0. Use debug mode (`spring.shell.debug.enabled=true`) instead of `stacktrace`. Shell-specific completion setup is left to the user's preferred shell.

- Keep built-ins enabled unless the product has a deliberate operator-experience reason to hide them.
- Keep custom command names distinct from built-ins so help output and command routing stay unambiguous.
- Treat help text as part of the command contract. Keep command names, options, defaults, and examples aligned with what `help` prints.
- Use script mode when support teams need reproducible CLI runs for onboarding, automation, or incident response.

## Coding procedure

1. Start from the user-facing command contract: command name, group, required options, defaults, and terminal output.
2. Default to annotation-based command registration for ordinary shells. Use programmatic registration only when commands must be assembled dynamically at runtime.
3. Keep command methods thin. Parse input in the shell layer and delegate real work to an injected service.
4. Use `@Option` defaults and required flags to make invalid input fail early.
5. Add Bean Validation, completion, and `Availability` checks as close as possible to the command boundary.
6. Use `CommandContext` for interactive reads and terminal-aware writes instead of `System.in` or `System.out`.
7. Make success output deterministic and define non-zero exit behavior explicitly when operators or automation depend on it.
8. Add shell tests that prove command execution, help output, invalid input handling, and availability behavior.
9. Add a Spring Boot end-to-end test when startup wiring, profiles, or full application context integration matter.

## Implementation examples

### End-to-end shell path

```java
@SpringBootApplication
class CatalogShellApplication {
}

@Service
class CatalogService {
    String add(String sku, int quantity) {
        return "added sku=%s quantity=%d".formatted(sku, quantity);
    }
}

@Component
class CatalogCommands {
    private final CatalogService catalogService;
    CatalogCommands(CatalogService catalogService) {
        this.catalogService = catalogService;
    }
    @Command(name = "catalog item add", description = "Add an item", group = "catalog")
    String add(@Option(longName = "sku", required = true) String sku,
               @Option(longName = "quantity", defaultValue = "1") int quantity) {
        return catalogService.add(sku, quantity);
    }
}
```

### Command group with `@CommandGroup`

Use `@CommandGroup` to define a shared prefix and group name at the class level:

```java
@CommandGroup(prefix = "catalog", name = "Catalog Commands")
@Component
class CatalogCommands {
    private final CatalogService catalog;
    CatalogCommands(CatalogService catalog) {
        this.catalog = catalog;
    }
    @Command(name = "item add", description = "Add an item",
             help = "Adds an item to the catalog with an explicit SKU and optional quantity.")
    String add(@Option(longName = "sku", required = true) String sku,
               @Option(longName = "quantity", defaultValue = "1") int quantity) {
        catalog.add(sku, quantity);
        return "added sku=%s quantity=%d".formatted(sku, quantity);
    }
}
```

Commands in a `@CommandGroup` class are invoked as `catalog item add`. A `group` attribute on `@Command` overrides the class-level group.

### Options and positional arguments

`@Option` defines named parameters. `@Argument` defines positional parameters. `@Arguments` defines multi-valued positional parameters with optional arity.

```java
@Command(name = "catalog item add", description = "Add an item", group = "catalog")
String add(@Option(longName = "sku", required = true) String sku,
           @Option(longName = "quantity", defaultValue = "1") int quantity) {
    return catalogService.add(sku, quantity);
}

@Command(name = "hi", description = "Say hi", group = "greetings")
String sayHi(@Argument(index = 0, description = "the name of the person to greet",
                       defaultValue = "world") String name,
             @Option(shortName = 's', longName = "suffix", defaultValue = "!") String suffix) {
    return "Hi " + name + suffix;
}

@Command(name = "real-part", description = "Calculate the real part of a complex product", group = "math")
double realPart(@Arguments(arity = 2) double[] realParts,
                @Arguments(arity = 2) double[] imaginaryParts) {
    return realParts[0] * realParts[1] - imaginaryParts[0] * imaginaryParts[1];
}
```

Options use POSIX-style syntax: `--long-name=value`, `--long-name value`, `-k=value`, `-k value`. Options and arguments can appear in any order.

Boolean options require an explicit value in 4.x. Use `--flag=true` or `--flag false`.

### Programmatic command registration

Register `Command` beans for dynamic or runtime-assembled commands:

```java
@Bean
Command myCommand() {
    return Command.builder().name("mycommand").execute(context -> {
        context.outputWriter().println("This is my command!");
    });
}
```

Use `AbstractCommand` for a base class with built-in help-option handling:

```java
@Bean
Command statusCommand(ClusterService clusterService) {
    return new AbstractCommand("cluster status", "Show current cluster status") {
        @Override
        public ExitStatus doExecute(CommandContext commandContext) {
            commandContext.outputWriter().println(clusterService.currentStatus());
            return ExitStatus.OK;
        }
    };
}
```

Use the `Command.Builder` API for fine-grained control over grouping, availability, completion, and exception mapping:

```java
@Bean
Command downloadCommand() {
    return Command.builder()
        .name("download")
        .group("cluster")
        .availabilityProvider(() -> connected
            ? Availability.available()
            : Availability.unavailable("run 'cluster connect' first"))
        .execute(ctx -> { /* ... */ });
}
```

### Command context input and output

```java
@Command(name = "catalog item note", description = "Attach a free-form note", group = "catalog")
String note(CommandContext ctx, @Option(longName = "sku", required = true) String sku) {
    String note = ctx.inputReader().readInput("note: ");
    ctx.outputWriter().println("saving note for " + sku);
    ctx.outputWriter().flush();
    return "saved";
}
```

Use `ctx.inputReader().readPassword("password: ")` instead of `readInput(...)` when the operator enters a secret.

### Availability rule for environment-sensitive commands

```java
@Component
class ClusterCommands {
    private final ClusterSession clusterSession;
    ClusterCommands(ClusterSession clusterSession) {
        this.clusterSession = clusterSession;
    }
    @Command(name = "cluster status", description = "Show current cluster status", group = "cluster",
             availabilityProvider = "statusAvailability")
    String status() {
        return clusterSession.currentStatus();
    }
    @Bean
    AvailabilityProvider statusAvailability() {
        return () -> clusterSession.isConnected()
            ? Availability.available()
            : Availability.unavailable("run 'cluster connect' first");
    }
}
```

The `availabilityProvider` attribute on `@Command` replaces the v3 `@CommandAvailability` annotation.

### Completion for bounded option values

Spring Shell 4.x provides command-level `CompletionProvider` that supports cross-option completion. Register a bean and reference it by name:

```java
@Command(name = "cluster connect", description = "Connect to a target environment", group = "cluster",
         completionProvider = "environmentCompletionProvider")
String connect(@Option(shortName = 'e', longName = "env") String environment) {
    return "connected %s".formatted(environment);
}

@Bean
CompletionProvider environmentCompletionProvider() {
    return completionContext -> List.of("local", "staging", "prod").stream()
        .map(CompletionProposal::new)
        .toList();
}
```

Built-in completion providers:

| Provider | Suggests |
| --- | --- |
| `EnumCompletionProvider` | Values from an enum type |
| `FileNameCompletionProvider` | File and directory names |
| `CompositeCompletionProvider` | Merges multiple providers |

Enum options are completed automatically when the option type is an enum.

### Exit-status mapping

`ExitStatusExceptionMapper` maps exceptions to `ExitStatus` records:

```java
@Bean
ExitStatusExceptionMapper exitStatusExceptionMapper() {
    return exception -> {
        if (exception instanceof IllegalArgumentException) {
            return new ExitStatus(2, "invalid argument");
        }
        return new ExitStatus(1, "error");
    };
}
```

Specify per-command with `@Command(exitStatusExceptionMapper = "myMapper")` or on `Command.Builder`.

### Shell test shape

```java
@ShellTest
@ContextConfiguration(classes = CatalogShellApplication.class)
class CatalogCommandsTests {
    @Test
    void addCommandReturnsDeterministicOutput(@Autowired ShellTestClient client) throws Exception {
        ShellScreen shellScreen = client.sendCommand("catalog item add --sku SKU-1 --quantity 2");
        ShellAssertions.assertThat(shellScreen).containsText("added sku=SKU-1 quantity=2");
    }
    @Test
    void unknownCommandThrows(@Autowired ShellTestClient client) {
        Assertions.assertThatThrownBy(() -> client.sendCommand("foo"))
            .isInstanceOf(CommandNotFoundException.class);
    }
}
```

Simulate interactive input with `ShellInputProvider`:

```java
@Test
void commandWithUserInput(@Autowired ShellTestClient client) throws Exception {
    ShellInputProvider inputProvider = ShellInputProvider.providerFor("ask").withInput("hi").build();
    ShellScreen screen = client.sendCommand(inputProvider);
    ShellAssertions.assertThat(screen).containsText("You said: hi");
}
```

### Spring Boot end-to-end test shape

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
    useMainMethod = SpringBootTest.UseMainMethod.ALWAYS,
    classes = { CatalogShellApplication.class },
    properties = { "spring.shell.interactive.enabled=false" },
    args = "catalog item add --sku SKU-1")
@ExtendWith(OutputCaptureExtension.class)
class CatalogEndToEndTests {
    @Test
    void commandOutput(CapturedOutput output) {
        assertThat(output).contains("added sku=SKU-1");
    }
}
```

Use `@SpringBootTest` when full application wiring matters. Use `@ShellTest` when testing multiple commands in the same class.

## `@Command` annotation reference

| Attribute | Purpose | Default |
| --- | --- | --- |
| `name` | Command name; supports subcommands as `{"command1", "sub1"}` or `"command1 sub1"` | method name |
| `alias` | Alternative names | `{}` |
| `value` | Shorthand for `description` | `""` |
| `description` | One-line command description | `""` |
| `group` | Help group name | `""` |
| `help` | Extended help text with usage examples | `""` |
| `hidden` | Hide from help output | `false` |
| `availabilityProvider` | Bean name for `AvailabilityProvider` | `""` |
| `completionProvider` | Bean name for `CompletionProvider` | `""` |
| `exitStatusExceptionMapper` | Bean name for `ExitStatusExceptionMapper` | `""` |

All customizations (availability, completion, exception mapping) are set via `@Command` attributes. The v3 pattern of separate annotations (`@CommandAvailability`, `@OptionValues`) is removed.

## `@Option` annotation reference

| Attribute | Purpose | Default |
| --- | --- | --- |
| `shortName` | Single-character option name (e.g. `'n'` for `-n`) | none |
| `longName` | Multi-character option name (e.g. `"name"` for `--name`) | none |
| `description` | Option description for help | `""` |
| `defaultValue` | Default when option is omitted | `""` |
| `required` | Fail if option is missing | `false` |

Each option can have a single short name or long name, not both aliases. Option labels and multi-alias options are removed in 4.x. Arity on `@Option` is removed; use `@Arguments` for multi-valued input.

## `@Argument` and `@Arguments` annotation reference

`@Argument` defines a single positional parameter. `@Arguments` defines multi-valued positional parameters.

| Annotation | Attribute | Purpose | Default |
| --- | --- | --- | --- |
| `@Argument` | `index` | Position index among arguments (0-based) | required |
| `@Argument` | `description` | Description for help | `""` |
| `@Argument` | `defaultValue` | Default when argument is omitted | `""` |
| `@Arguments` | `arity` | Number of values to consume (rest treated as separate arguments/options) | unbounded |

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

- Declarative annotation-based command registration is not supported for GraalVM native compilation in 4.0.x. Use the programmatic `Command` bean approach for native images.

## References

- Open [references/interactive-flows-and-terminal-ui.md](references/interactive-flows-and-terminal-ui.md) when the task needs guided flows, confirmations, selectors, or richer terminal UI components beyond ordinary command registration.
- Open [references/prompt-and-styling.md](references/prompt-and-styling.md) when the prompt or output styling must surface environment or risk information.
