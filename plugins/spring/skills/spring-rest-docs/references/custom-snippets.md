# Spring REST Docs custom snippets

Open this reference when built-in snippets are not enough.

Add custom snippets only when the built-in field, parameter, header, and request or response snippets are not enough.

- Good fit: domain-specific tables, enum catalogs, repeated error envelope documentation, constraint columns.
- Poor fit: rewriting standard request or response snippets.

## Adding snippet attributes

```java
requestFields(attributes(key("title").value("Fields for user creation")),
    fieldWithPath("name").description("The user's name")
        .attributes(key("constraints").value("Must not be null. Must not be empty")),
    fieldWithPath("email").description("The user's email address"))
```

## Custom Mustache templates

Templates are loaded from the classpath under `org.springframework.restdocs.templates.asciidoctor`. Each template is named after the snippet it produces, for example `request-fields.snippet` at `src/test/resources/org/springframework/restdocs/templates/asciidoctor/request-fields.snippet`.

```mustache
.{{title}}
|===
|Path|Type|Description|Constraints

{{#fields}}
|{{path}}
|{{type}}
|{{description}}
|{{constraints}}

{{/fields}}
|===
```

Use the `tableCellContent` lambda when writing custom templates that render user-provided text containing Asciidoctor table cell characters.

## Validation rule

Verify the custom snippet adds contract value not already covered by built-in snippets.
