# Local vector store for development

Open this reference when the ordinary path in [SKILL.md](../SKILL.md) is not enough and the blocker is a reproducible local vector store that matches the production retrieval path.

## Local vector-store blocker

**Problem:** retrieval work needs a reproducible vector store in development.

**Solution:** provision the store explicitly and keep the store choice aligned with the deployment path.

```yaml
services:
  pgvector:
    image: pgvector/pgvector:pg16
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: app
      POSTGRES_USER: app
      POSTGRES_PASSWORD: app
```

Treat in-memory stores as development-only. When production uses PostgreSQL plus pgvector, prefer a local pgvector instance over a different store that changes retrieval behavior.

## Decision points

| Situation | First choice |
| --- | --- |
| need local retrieval that matches PostgreSQL production | pgvector |

## Pitfalls

- Do not rely on in-memory vector stores to validate production retrieval behavior.
- Keep local schema and extension setup explicit so developers do not accidentally test against divergent storage behavior.
