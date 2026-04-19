# Local model runtime

Open this reference when the common-path Spring AI seam in [SKILL.md](../SKILL.md) is already clear and the blocker is a local model runtime for development or offline testing.

## Use this file for one blocker family

Use this file only when the remaining work is a local model runtime, not vector-store provisioning or full environment bootstrap.

## Local model runtime blocker

**Problem:** the team needs a local model runtime for development or offline testing.

**Solution:** use an officially documented local provider path such as Ollama and keep the runtime URL explicit.

```yaml
spring:
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        options:
          model: mistral
```

If the same local runtime should expose both chat and embeddings, keep those model choices explicit instead of assuming one default fits both.

## Decision points

| Situation | First choice |
| --- | --- |
| Need private local chat or embeddings | Ollama |
| Need cloud-specific integration behavior | move back to the provider-specific configuration seam instead of hiding it in local infra |

## Pitfalls

- Do not assume local and production model behavior match exactly. Validate prompts and tool behavior again against the production provider.
- Do not let local infra pick model names implicitly. Keep chat, embedding, and optional image or audio models explicit.
- Open [local-vector-store-dev.md](local-vector-store-dev.md) when the blocker is a reproducible local vector store.
- Open [containerized-dev-environment.md](containerized-dev-environment.md) when the blocker is a full repeatable containerized development stack.
