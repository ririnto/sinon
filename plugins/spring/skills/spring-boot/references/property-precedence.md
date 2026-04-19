# Spring Boot property precedence

Open this reference when conflicting values across property sources are the blocker.

```bash
java -jar app.jar --catalog.region=eu-west-1
```

When values conflict, verify precedence instead of assuming the file is wrong.

## Gotchas

- Do not forget command-line arguments and environment variables can override config files.
