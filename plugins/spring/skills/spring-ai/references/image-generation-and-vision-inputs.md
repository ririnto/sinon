# Vision input handling

Open this reference when the common-path text workflow in [SKILL.md](../SKILL.md) is not enough and the blocker is single-image, vision-style user input.

## Use this file for one blocker family

Use this file only after the application already has a clear Spring AI seam and the remaining decision is about image-capable model behavior.

- Vision input: the model must inspect screenshots, PDFs rendered as images, diagrams, or photos.
- Mixed text and image prompts: the user message must carry both text instructions and image data.

## Vision input blocker

**Problem:** the prompt needs non-text input and the application does not yet know how to attach image data safely.

**Solution:** keep text in the `UserMessage` text field and pass image content through `media(...)` on the user side only.

```java
String response = ChatClient.create(chatModel).prompt()
    .user(user -> user
        .text("Explain the deployment risk shown in this screenshot.")
        .media(MimeTypeUtils.IMAGE_PNG, new ClassPathResource("/release-risk.png")))
    .call()
    .content();
```

- Keep the media payload on `UserMessage`, not on system messages.
- Resize very large images before sending them so token usage stays visible and controlled.
- When the model must reason over both the text and the image, say that explicitly in the prompt.

## Decision points

| Situation | First choice |
| --- | --- |
| Screenshot or diagram understanding | vision-capable chat model with `media(...)` |
| Mixed text plus image reasoning | one user message with explicit text and media |
| Document image extraction becomes retrieval or OCR pipeline work | move back to the retrieval or document-processing seam instead of forcing a huge multimodal prompt |

## Pitfalls

- Do not assume every chat model accepts image input. Verify the specific model family.
- Do not bury large binary payloads without tracking token or size cost.
- Open [multiple-image-comparison.md](multiple-image-comparison.md) when the blocker is cross-referencing several images.
- Open [image-generation.md](image-generation.md) when the blocker is producing a generated image artifact.
