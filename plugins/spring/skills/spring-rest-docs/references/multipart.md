# Spring REST Docs multipart

Open this reference when the contract includes multipart request parts.

```java
requestParts(partWithName("metadata").description("JSON metadata part"), partWithName("file").description("Uploaded document"))
```

```java
requestPartFields("metadata", fieldWithPath("title").description("Document title"))
```

Use multipart snippets only when request parts are part of the contract.

## Validation rule

Verify part names and structured part payloads match the real multipart request shape.
