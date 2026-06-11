# Spring REST Docs preprocessors

Open this reference when preprocessors must do more than the ordinary pretty-print and URI rewriting path.

## Decision points

| Situation | Use |
| --- | --- |
| Pretty-print request or response payloads | `preprocessRequest(prettyPrint())`, `preprocessResponse(prettyPrint())` |
| Rewrite URIs for published docs | `modifyUris().scheme("https").host("api.example.com").removePort()` |
| Mask volatile headers | `removeHeaders("Authorization")` |
| Mask volatile query parameters | `removeParameters("timestamp")` |
| Replace content matching a pattern | `replacePattern(Pattern.compile("\\d{4}-\\d{2}-\\d{2}"), "YYYY-MM-DD")` |
| Mask hypermedia link hrefs | `maskLinks()` |
| Add or set headers | `modifyHeaders().header("X-Custom", "value")` |
| Apply the same preprocessor to every test | configure `operationPreprocessors()` in the `@BeforeEach` setup |

## Per-test preprocessing

```java
.andDo(document("orders-get", preprocessRequest(prettyPrint(), modifyUris().scheme("https").host("api.example.com").removePort()), preprocessResponse(prettyPrint())))
```

## Global preprocessing in setup

```java
@BeforeEach
void setUp(WebApplicationContext context, RestDocumentationContextProvider restDocumentation) {
    this.mvc = MockMvcBuilders.webAppContextSetup(context)
        .apply(documentationConfiguration(restDocumentation).operationPreprocessors()
            .withRequestDefaults(removeHeaders("Authorization"), prettyPrint())
            .withResponseDefaults(prettyPrint()))
        .build();
}
```

Use global preprocessing when the same normalization applies to every test.

## Gotchas

- Do not hide stable contract data behind an overly broad preprocessing rule.
- For body-only content modification, implement `ContentModifier` and use it with the built-in `ContentModifyingOperationPreprocessor`.
