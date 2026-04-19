# Image generation

Open this reference when the ordinary vision path in [SKILL.md](../SKILL.md) is not enough and the blocker is producing a generated image artifact instead of text.

## Image generation blocker

**Problem:** the feature must return a generated image rather than plain text.

**Solution:** use an `ImageModel` seam and keep image-model options explicit at the call site or configuration edge.

```java
@Service
class ReleaseBannerService {
    private final ImageModel imageModel;

    ReleaseBannerService(ImageModel imageModel) {
        this.imageModel = imageModel;
    }

    String generateBannerUrl(String releaseName) {
        ImageResponse response = imageModel.call(new ImagePrompt(
            "Create a release banner for " + releaseName,
            OpenAiImageOptions.builder().withModel("dall-e-3").withWidth(1024).withHeight(1024).build()
        ));
        return response.getResult().getOutput().getUrl();
    }
}
```

## Decision points

| Situation | First choice |
| --- | --- |
| generated marketing or UI assets | dedicated `ImageModel` seam |

## Pitfalls

- Do not treat image generation as ordinary chat output.
- Keep output format, size, and provider-specific options explicit at the application edge.
