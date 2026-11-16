# Setup and execution

## Dependency baseline

Use the starter for normal Spring Boot integration.
Add the test artifact whenever command behavior matters enough to lock with executable shell tests.

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
| `spring-shell-core` | Command registration, parsing, and execution APIs without depending on Spring Boot or JLine |
| `spring-shell-jline` | JLine console integration required for tab completion, command history, and rich text formatting |
| `spring-shell-test` | Shell testing utilities for Spring Boot tests |

### Feature-to-artifact map

| Need | Starter or artifact |
| --- | --- |
| Ordinary `@Command` registration and REPL runtime | `spring-shell-starter` |
| Shell-focused tests | `spring-shell-test` |
| Completion and availability rules | `spring-shell-starter` |
| Guided flows or TUI components | `spring-shell-starter`, then open [references/interactive-flows-and-terminal-ui.md](interactive-flows-and-terminal-ui.md) |
| Prompt customization and styled operator output | `spring-shell-starter`, then open [references/prompt-and-styling.md](prompt-and-styling.md) |

### Non-Boot usage

When not using Spring Boot, add `spring-shell-core` directly and enable command discovery with `@EnableCommand` on the configuration class, targeting the class that contains the `@Command` methods:

```java
@Component
class ExampleCommands {
    @Command(name = "example", description = "Print a greeting")
    String example() {
        return "Hello";
    }
}

@Configuration
@EnableCommand(ExampleCommands.class)
/**
 * Entry point that boots the Spring Shell runtime from an annotation context.
 */
public class SpringShellApplication {
    /**
     * Runs the shell until the interactive session exits.
     */
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

Keep interactive mode enabled for the ordinary REPL path.
Disable it only for automated script execution or harnesses that intentionally bypass the REPL loop.

### Interaction mode

Spring Shell 4.x provides three `ShellRunner` implementations:

| Runner | Mode | Requires |
| --- | --- | --- |
| `JLineShellRunner` | Interactive | `spring-shell-starter` or `spring-shell-jline` |
| `SystemShellRunner` | Interactive | `spring-shell-core` only (no tab completion or history) |
| `NonInteractiveShellRunner` | Non-interactive | `spring-shell-core` only |

The Spring Boot starter uses `JLineShellRunner` by default.
Set `spring.shell.interactive.enabled=false` to switch to `NonInteractiveShellRunner`.

When a command is passed on the command line, Spring Shell 4.0.3+ automatically runs in non-interactive mode even without the property.

### Debug mode

Set `spring.shell.debug.enabled=true` to print stack traces on command errors.
This replaces the v3 `stacktrace` built-in command.

### Configuration properties

| Property | Default | Effect |
| --- | --- | --- |
| `spring.shell.interactive.enabled` | `true` | Enable the interactive REPL, or set `false` for single-command execution |
| `spring.shell.script.enabled` | `true` | Enable the `script` built-in command |
| `spring.shell.debug.enabled` | `false` | Print stack traces on command errors |
