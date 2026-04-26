# Multiple image comparison

Open this reference when the ordinary vision path in [SKILL.md](../SKILL.md) is not enough and the blocker is comparing or cross-referencing several images in one request.

## Multiple image blocker

**Problem:** the model must compare or cross-reference more than one image.

**Solution:** attach multiple image payloads in a single user message and keep the comparison question explicit.

```java
UserMessage userMessage = UserMessage.builder()
    .text("Compare the two architecture diagrams and list only the new components.")
    .media(new Media(MimeTypeUtils.IMAGE_PNG, new ClassPathResource("/before.png")), new Media(MimeTypeUtils.IMAGE_PNG, new ClassPathResource("/after.png")))
    .build();
```

## Decision points

| Situation | First choice |
| --- | --- |
| the model must compare several images | one user message with explicit comparison text and multiple `Media` payloads |

## Pitfalls

- Do not mix unrelated images into one request unless the prompt explicitly tells the model how to compare them.
- Keep image order meaningful so the prompt can refer to each image deterministically.
