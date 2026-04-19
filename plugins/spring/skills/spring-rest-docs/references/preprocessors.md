# Spring REST Docs preprocessors

Open this reference when preprocessors must do more than the ordinary pretty-print and URI rewriting path.

```java
preprocessRequest(removeHeaders("Authorization"), removeParameters("timestamp"))
```

Use preprocessors when scrubbing or normalization must happen before snippets are written.

## Gotchas

- Do not hide stable contract data behind an overly broad preprocessing rule.
