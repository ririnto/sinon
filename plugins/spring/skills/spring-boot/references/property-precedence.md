# Spring Boot property precedence

Open this reference when conflicting values across property sources are the blocker.

```sh
java -jar app.jar --catalog.region=eu-west-1
```

For these sources, the higher entry takes precedence over the lower one:

1. Command-line arguments.
2. `SPRING_APPLICATION_JSON`.
3. Java system properties.
4. OS environment variables.
5. Config data such as `application.yaml`.

When values conflict, verify precedence instead of assuming the file is wrong.
