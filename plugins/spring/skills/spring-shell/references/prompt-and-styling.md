# Spring Shell prompt and styling

Open this reference when the blocker is environment visibility, connection state, or operator-risk signaling beyond the ordinary command path.

Customize the prompt only when environment visibility materially reduces operator error.

## Prompt provider shape

```java
@Bean
PromptProvider promptProvider(ActiveEnvironment environment) {
    return () -> new AttributedString("ops-%s:> ".formatted(environment.name()), AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW));
}
```

Use prompt theming to surface environment or login state, not to decorate the shell unnecessarily.

## Risk-signaling output shape

```java
String delete(CommandContext ctx, @Option(longNames = "cluster", required = true) String cluster) {
    ctx.outputWriter().println(new AttributedString("warning: deleting from %s".formatted(cluster), AttributedStyle.DEFAULT.foreground(AttributedStyle.RED)));
    ctx.outputWriter().flush();
    return "accepted";
}
```

Use styled output for operator warnings, but keep the actual success or failure payload stable enough for logs and wrappers.

## Decision points

| Situation | Use |
| --- | --- |
| Operators must see the active environment at all times | custom `PromptProvider` |
| Color should warn about risky environments | `AttributedStyle` with explicit warning colors |
| Automation needs predictable plain output | keep styling out of machine-facing command output |

## Gotchas

- Do not rely on color alone for safety-critical information.
- Do not make prompt styling so noisy that it reduces command readability.
- Do not mix decorative styling into outputs that scripts parse.

## Validation rule

Verify prompt customization still leaves command names readable and that machine-parsed output remains plain, stable, and independent of terminal color support.
