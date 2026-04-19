# Containerized development environment

Open this reference when the ordinary path in [SKILL.md](../SKILL.md) is not enough and the blocker is a repeatable local environment spanning model runtime and retrieval infrastructure.

## Containerized dev-services blocker

**Problem:** the team needs a repeatable local environment for models and retrieval infrastructure.

**Solution:** keep the local runtime list short and explicit so every developer sees the same endpoints.

```yaml
services:
  ollama:
    image: ollama/ollama:latest
    ports:
      - "11434:11434"
  pgvector:
    image: pgvector/pgvector:pg16
    ports:
      - "5432:5432"
```

## Decision points

| Situation | First choice |
| --- | --- |
| need repeatable dev bootstrap | Docker Compose with explicit model and vector services |

## Pitfalls

- Do not mix unrelated local stacks in one reference file unless they solve the same provisioning blocker.
- Keep the container list short enough that developers can understand which service owns which capability.
