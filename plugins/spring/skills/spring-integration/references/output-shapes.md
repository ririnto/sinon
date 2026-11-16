# Output and configuration shapes

## Flow sketch shape

```text
source -> channel -> endpoint -> channel -> sink
```

## Gateway channel shape

```text
orders.input
```

## Poller shape

```java
Pollers.fixedDelay(Duration.ofSeconds(5))
```
