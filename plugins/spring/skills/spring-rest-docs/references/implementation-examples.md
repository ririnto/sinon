# Rest Docs implementation examples

## MockMvc documentation test

```java
@WebMvcTest(OrderController.class)
@AutoConfigureRestDocs
class OrderDocumentationTests {
    @Autowired
    MockMvc mvc;

    @Test
    void createOrder() throws Exception {
        MockHttpServletRequestBuilder createOrderRequest = post("/orders").contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"ABC\",\"quantity\":2}");
        mvc.perform(createOrderRequest)
            .andExpect(status().isCreated())
            .andDo(document("orders-create", preprocessRequest(prettyPrint()), preprocessResponse(prettyPrint()), requestFields(fieldWithPath("sku").description("Stock keeping unit"), fieldWithPath("quantity").description("Quantity requested")), responseFields(fieldWithPath("id").description("Created order id"), fieldWithPath("status").description("Order status"))));
    }
}
```

## Query parameter and header documentation

```java
.andDo(document("orders-list", queryParameters(parameterWithName("status").description("Order status filter")), requestHeaders(headerWithName("X-Correlation-Id").description("Request correlation id")), responseFields(fieldWithPath("[].id").description("Order id"), fieldWithPath("[].status").description("Order status"))));
```

## Path parameter documentation

```java
.andDo(document("orders-get", pathParameters(parameterWithName("orderId").description("Order identifier")), responseFields(fieldWithPath("id").description("Order id"), fieldWithPath("status").description("Order status"))));
```

## URI preprocessing shape

```java
.andDo(document("orders-get", preprocessRequest(modifyUris().scheme("https").host("api.example.com").removePort()), preprocessResponse(prettyPrint())));
```

## Asciidoctor include shape

```adoc
= Orders API
:snippets: build/generated-snippets

== Create order

operation::orders-create[snippets='http-request,http-response,request-fields,response-fields']
```
