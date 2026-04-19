# Spring Shell prompt and styling

Open this reference when the shell prompt or colored output must surface environment, connection state, or operator risk beyond the ordinary command path.

Customize the prompt only when environment visibility materially reduces operator error.

## Prompt provider shape

```java
@Bean
PromptProvider promptProvider(ActiveEnvironment environment) {
    return () -> new AttributedString(
        "ops-%s:> ".formatted(environment.name()),
        AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW)
    );
}
```

Use prompt theming to surface environment or login state, not to decorate the shell unnecessarily.

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
