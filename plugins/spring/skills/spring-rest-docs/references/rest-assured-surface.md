# Spring REST Docs REST Assured surface

Open this reference when the ordinary MockMvc path is not enough and the task needs REST Assured.

```java
this.spec = new RequestSpecBuilder()
    .addFilter(documentationConfiguration(restDocumentation))
    .build();
```

Use this when the project already uses REST Assured for end-to-end HTTP verification.

## Validation rule

Verify the documentation filter is attached to the same REST Assured spec the test already uses.
