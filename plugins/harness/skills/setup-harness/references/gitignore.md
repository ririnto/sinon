# Gitignore Reference

Open this reference when deciding which harness runtime artifacts should stay untracked in a target repository.

# Target Gitignore Entries

Add these entries to the target repository root `.gitignore` when installing harness scripts or generated local tooling caches. Treat IDE metadata and local caches as non-copyable harness artifacts.

```gitignore
__pycache__/
*.py[cod]
*.pyo
*.pyd
.DS_Store
.idea/
.settings/
.classpath
.project
.factorypath
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

Keep generated documentation under `docs/generated/` tracked only when it has provenance, source paths, and a regeneration command in `docs/harness/config.json`.
