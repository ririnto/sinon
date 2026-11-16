# Java test templates

## Plain JUnit Jupiter test

```java
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProfileServiceTest {
    @Test
    void returnsCachedProfileWhenPresent() {
        assertEquals("user-1", service.loadProfile("user-1").id());
    }
}
```

## Parameterized test

`@CsvSource` for tabular inputs:

```java
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscountCalculatorTest {
    @ParameterizedTest
    @CsvSource({
        "100, 10, 90",
        "200, 25, 150",
        "50,  0,  50"
    })
    void appliesDiscountCorrectly(int price, int percent, int expected) {
        assertEquals(expected, calculator.apply(price, percent));
    }
}
```

`@MethodSource` for complex objects:

```java
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TierClassifierTest {
    static Stream<Arguments> tiers() {
        return Stream.of(
            Arguments.of(0, "free"),
            Arguments.of(999, "free"),
            Arguments.of(1000, "standard"),
            Arguments.of(9999, "standard")
        );
    }

    @ParameterizedTest
    @MethodSource("tiers")
    void classifiesCorrectly(int points, String expectedTier) {
        assertEquals(expectedTier, classifier.classify(points));
    }
}
```

## Mockito boundary

```java
import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {
    @Mock
    private RemoteClient client;

    @InjectMocks
    private ClientService service;

    @Test
    void retriesAfterTransientFailure() throws IOException {
        when(client.call())
            .thenThrow(new IOException("temporary"))
            .thenReturn("ok");
        assertEquals("ok", service.run());
        verify(client, times(2)).call();
    }
}
```

Argument capture:

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    @Mock
    private MessageSender sender;

    @Captor
    private ArgumentCaptor<Message> messageCaptor;

    @Test
    void sendsFormattedMessage() {
        service.notify("alice", "welcome");
        verify(sender).send(messageCaptor.capture());
        assertEquals("welcome", messageCaptor.getValue().body());
    }
}
```

For additional Mockito features, see [`testing-core.md`](./testing-core.md).

## Awaitility eventual assertion

```java
import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventPublisherTest {
    @Test
    void publishesEventually() {
        service.triggerAsyncWork();
        await().atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> assertTrue(repository.contains("done")));
    }
}
```

## Nested JUnit Jupiter contexts

```java
import java.math.BigDecimal;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CartServiceTest {
    @Nested
    class WhenCartIsEmpty {
        @Test
        void returnsZeroTotal() {
            assertEquals(BigDecimal.ZERO, service.total());
        }
    }
}
```

## Declarative timeout

```java
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class FeedRefreshTest {
    @Test
    @Timeout(value = 500, unit = TimeUnit.MILLISECONDS)
    void refreshFinishesWithinBudget() {
        service.refresh();
    }
}
```

## Temporary directory

```java
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FileExporterTest {
    @Test
    void writesOutputFile(@TempDir Path tempDir) {
        Path output = tempDir.resolve("report.csv");
        exporter.exportTo(output);
        assertTrue(output.toFile().exists());
    }
}
```

## Build-tool wiring

Maven Surefire (unit tests, `*Test.java`):

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-surefire-plugin</artifactId>
</plugin>
```

Maven Failsafe (integration tests, `*IT.java`):

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-failsafe-plugin</artifactId>
</plugin>
```

Gradle Groovy DSL:

```groovy
test {
    useJUnitPlatform()
}
```

Gradle Kotlin DSL:

```kotlin
tasks.test {
    useJUnitPlatform()
}
```
