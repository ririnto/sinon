# Provider selection and model capability fit

Open this reference when the common-path model-family choice in [SKILL.md](../SKILL.md) is not enough and the blocker is provider-specific incompatibility, configuration edge behavior, or portability risk.

## Provider-specific blockers

Use this file only after `SKILL.md` has already narrowed the workload to a model surface and the remaining question is provider-specific behavior.

- OpenAI or Azure OpenAI: when response-format support, deployment naming, or mature tool-calling examples are the deciding edge.
- Anthropic: when instruction following is strong but embeddings, moderation, or image features may come from a different provider.
- Gemini or Vertex AI: when long-context or multimodal handling dominates and the deployment already lives in GCP.
- Mistral: when EU-hosting preference, cost profile, moderation, or model-family availability is the real constraint.
- Ollama: when local execution is required and the real blocker is model capability variation rather than Spring AI API usage.
- Bedrock: when one AWS-hosted integration must span multiple provider families behind a single cloud boundary.

## Model-type selector blocker

When more than one provider starter is present for the same model type, keep the active provider explicit instead of relying on accidental auto-configuration.

```properties
spring.ai.model.chat=openai
spring.ai.model.embedding=openai
spring.ai.model.image=openai
spring.ai.model.audio.transcription=openai
spring.ai.model.audio.speech=openai
spring.ai.model.moderation=openai
```

Use only the selectors needed for the surfaces the application actually exposes.

## Capability matrix

| Provider | Chat | Embeddings | Vision | Image generation | Audio | Moderation | Local |
| --- | --- | --- | --- | --- | --- | --- | --- |
| OpenAI | Yes | Yes | Yes | Yes | Yes | Yes | No |
| Azure OpenAI | Yes | Yes | Yes | Yes | Provider-dependent | No common default | No |
| Anthropic | Yes | No native default in many teams | Yes | No | No | No | No |
| Gemini | Yes | Yes | Yes | No common default | Audio and video-capable families | No common default | No |
| Mistral | Yes | Yes | Some families | No common default | No common default | Yes | No |
| Ollama | Depends on model | Depends on model | Depends on model | No common default | No common default | No common default | Yes |
| Bedrock | Yes | Yes | Provider-dependent | Provider-dependent | Provider-dependent | Provider-dependent | No |

## Provider-specific configuration boundaries

### OpenAI

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o
```

### Anthropic

```yaml
spring:
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}
      chat:
        options:
          model: claude-3-5-sonnet-20241022
```

### Google Vertex AI Gemini

```yaml
spring:
  ai:
    vertex:
      ai:
        gemini:
          project-id: ${GCP_PROJECT_ID}
          location: ${GCP_LOCATION:us-central1}
```

### Mistral AI

```yaml
spring:
  ai:
    mistralai:
      api-key: ${MISTRAL_API_KEY}
      chat:
        options:
          model: mistral-large-latest
```

### Ollama

```yaml
spring:
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        options:
          model: mistral
```

## Portability traps

- Prefer the portable Spring AI baseline first: `ChatClient`, `.entity(...)`, Spring AI model interfaces, and `VectorStore`.
- Do not assume tool-calling, structured-output, moderation, or multimodal behavior is identical across providers.
- Azure OpenAI requires deployment names that differ from standard OpenAI model IDs.
- Anthropic chat support and embedding support are separate concerns in many deployments.
- Ollama capability depends on the loaded model, not just the runtime.
- If portability matters more than provider-specific features, stay with Spring AI's ordinary abstractions instead of provider-native extensions.
