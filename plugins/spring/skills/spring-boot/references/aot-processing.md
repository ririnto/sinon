# Spring Boot AOT processing

Open this reference when the blocker is AOT generation or runtime hints.

```bash
./mvnw -Pnative spring-boot:process-aot
```

```bash
./gradlew processAot
```

If the application relies on reflection, dynamic proxies, or resource scanning beyond what Boot detects automatically, add explicit runtime hints.

## Validation rule

Run the AOT generation task and inspect failures before assuming a native-image problem.
