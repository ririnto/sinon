# Commands and annotation examples

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

Commands in a `@CommandGroup` class are invoked as `catalog item add`.
A `group` attribute on `@Command` overrides the class-level group.

### Options and positional arguments

`@Option` defines named parameters.
`@Argument` defines positional parameters.
`@Arguments` defines multi-valued positional parameters with optional arity.

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

Options use POSIX-style syntax: `--long-name=value`, `--long-name value`, `-k=value`, `-k value`.
Options and arguments can appear in any order.

Boolean options require an explicit value in 4.x.
Use `--flag=true` or `--flag false`.

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
        /**
         * Prints the current cluster status to the command output writer.
         */
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
        .execute(context -> downloadService.download());
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

Spring Shell 4.x provides command-level `CompletionProvider` that supports cross-option completion.
Register a bean and reference it by name:

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
        return exception instanceof IllegalArgumentException
            ? new ExitStatus(2, "invalid argument")
            : new ExitStatus(1, "error");
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

Use `@SpringBootTest` when full application wiring matters.
Use `@ShellTest` when testing multiple commands in the same class.

## `@Command` annotation reference

| Attribute | Purpose | Default |
| --- | --- | --- |
| `name` | Command name with subcommand support, including `{"command1", "sub1"}` or `"command1 sub1"` | method name |
| `alias` | Alternative names | `{}` |
| `value` | Shorthand for `description` | `""` |
| `description` | One-line command description | `""` |
| `group` | Help group name | `""` |
| `help` | Extended help text with usage examples | `""` |
| `hidden` | Hide from help output | `false` |
| `availabilityProvider` | Bean name for `AvailabilityProvider` | `""` |
| `completionProvider` | Bean name for `CompletionProvider` | `""` |
| `exitStatusExceptionMapper` | Bean name for `ExitStatusExceptionMapper` | `""` |

All customizations (availability, completion, exception mapping) are set via `@Command` attributes.
The v3 pattern of separate annotations (`@CommandAvailability`, `@OptionValues`) is removed.

## `@Option` annotation reference

| Attribute | Purpose | Default |
| --- | --- | --- |
| `shortName` | Single-character option name (e.g. `'n'` for `-n`) | none |
| `longName` | Multi-character option name (e.g. `"name"` for `--name`) | none |
| `description` | Option description for help | `""` |
| `defaultValue` | Default when option is omitted | `""` |
| `required` | Fail if option is missing | `false` |

Each option can define one `shortName` and one `longName`.
Either may be omitted.
Option labels and additional aliases are removed in 4.x.
Arity on `@Option` is removed.
Use `@Arguments` for multi-valued input.

## `@Argument` and `@Arguments` annotation reference

`@Argument` defines a single positional parameter.
`@Arguments` defines multi-valued positional parameters.

| Annotation | Attribute | Purpose | Default |
| --- | --- | --- | --- |
| `@Argument` | `index` | Position index among arguments (0-based) | required |
| `@Argument` | `description` | Description for help | `""` |
| `@Argument` | `defaultValue` | Default when argument is omitted | `""` |
| `@Arguments` | `arity` | Number of values to consume (rest treated as separate arguments/options) | unbounded |
