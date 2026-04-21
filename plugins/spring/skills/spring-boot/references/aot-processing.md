# Spring Boot AOT processing

Open this reference when the blocker is AOT generation or runtime hints.

## Run AOT processing

```bash
./mvnw -Pnative spring-boot:process-aot
```

```bash
./gradlew processAot
```

## Add explicit runtime hints

```java
package com.example;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(MyHints.class)
class NativeHintsConfiguration {
}

class MyHints implements RuntimeHintsRegistrar {
    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.reflection().registerType(MyClass.class, hint -> hint.withMembers(MemberCategory.INVOKE_DECLARED_METHODS));
    }
}
```

## Gotchas

- If the application relies on reflection, dynamic proxies, or resource scanning beyond what Boot detects automatically, add explicit runtime hints.
- Run the AOT generation task and inspect failures before assuming a native-image problem.

## Validation rule

Verify the generated sources and resources exist after the AOT task before debugging the downstream native-image build.
