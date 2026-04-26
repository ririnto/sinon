# Spring REST Docs custom snippets

Open this reference when built-in snippets are not enough.

Add custom snippets only when the built-in field, parameter, header, and request or response snippets are not enough.

- Good fit: domain-specific tables, enum catalogs, or repeated error envelope documentation.
- Poor fit: rewriting standard request and response snippet behavior for style reasons alone.

## Validation rule

Verify the custom snippet adds contract value not already covered by built-in snippets.

## Minimal custom snippet example

Use a custom snippet when the endpoint contract includes a domain-specific table that the built-in snippets do not model well.

```java
final class ErrorCodeSnippet extends TemplatedSnippet {
    private final List<ErrorCodeRow> rows;

    ErrorCodeSnippet(List<ErrorCodeRow> rows) {
        super("error-codes");
        this.rows = rows;
    }

    @Override
    protected Map<String, Object> createModel(Operation operation) {
        return Map.of("rows", rows);
    }

    record ErrorCodeRow(String code, String description) {
    }
}
```

```java
@WebMvcTest(OrderController.class)
@AutoConfigureRestDocs
class OrderDocumentationTests {
    @Autowired
    MockMvc mvc;

    @Test
    void createOrderDocumentsErrorCodes() throws Exception {
        mvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"ABC\",\"quantity\":2}"))
            .andExpect(status().isCreated())
            .andDo(document("orders-create", responseFields(fieldWithPath("id").description("Created order id")), new ErrorCodeSnippet(List.of(new ErrorCodeSnippet.ErrorCodeRow("OUT_OF_STOCK", "Inventory is insufficient for the request")))));
    }
}
```

```adoc
.Error codes
|===
|Code |Description

{{#rows}}
|{{code}} |{{description}}
{{/rows}}

|===
```

Place the template at `src/test/resources/org/springframework/restdocs/templates/error-codes.snippet` so the generated `error-codes.adoc` snippet is available to the same `document("orders-create", ...)` call.
