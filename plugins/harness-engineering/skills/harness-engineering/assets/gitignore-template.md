# Target Gitignore Entries

Add these entries to the target repository root `.gitignore` when installing harness scripts or generated local tooling caches.

```gitignore
__pycache__/
*.py[cod]
*.pyo
*.pyd
.mypy_cache/
.pytest_cache/
.ruff_cache/
.tox/
.nox/
.venv/
venv/
env/
ENV/
.uv-cache/
docs/generated/.tmp/
scripts/harness/.tmp/
```

Keep generated documentation under `docs/generated/` tracked only when it has provenance, source paths, and a regeneration command in `docs/harness-engineering/harness-engineering.json`.
