# Architecture Enforcement

Open this reference when implementing custom linters, structural tests, or taste invariants beyond the common-path layer model in `SKILL.md`.

## Layer model in detail

See SKILL.md Step 3 for the layer model overview and enforcement approach. This reference provides the allowed-target matrix and implementation patterns.

| Source | Allowed targets |
| --- | --- |
| Types | (none -- leaf layer) |
| Config | Types |
| Repo | Config, Types |
| Service | Repo, Config, Types |
| Runtime | Service, Repo, Config, Types |
| UI | Runtime, Service, Types |

Cross-cutting providers (auth, connectors, telemetry, feature flags) are injected at the Runtime layer and flow upward. No layer may import a provider directly except through the declared interface.

## Custom linter patterns

### Import direction linter

Reject imports that violate the inward-only dependency rule shown in the allowed-target matrix.

```python
"""
Linter: enforce inward-only layer imports within each domain.

Scans each domain directory for import statements that reference
a layer above the importing layer in the dependency stack.
"""
LAYERS = ["types", "config", "repo", "service", "runtime", "ui"]

def check_layer_imports(domain_path, file_path, imports):
    file_layer = detect_layer(file_path)
    for imp in imports:
        target_layer = detect_layer(imp)
        if target_layer and LAYERS.index(target_layer) > LAYERS.index(file_layer):
            yield LintError(
                file=file_path,
                line=imp.line,
                message=(
                    f"Layer violation: {file_layer} imports {target_layer}. "
                    "Dependencies must point only toward earlier layers: "
                    + " → ".join(LAYERS)
                    + f". Move the shared logic to {file_layer}, "
                    + f"depend on an allowed lower layer, or introduce a provider interface for {target_layer}."
                ),
            )
```

Key design: error messages include remediation instructions so the agent can self-correct without human intervention.

### Structured logging linter

Reject ad-hoc print statements and enforce structured logging.

```python
"""
Linter: reject print/log calls that use string formatting.

All logging MUST use structured key-value pairs.
"""
def check_structured_logging(file_path, source):
    for match in re.finditer(r'(print|log\.\w+)\(f["\']', source):
        yield LintError(
            file=file_path,
            line=match.lineno,
            message=(
                "Unstructured logging detected. Use structured key-value "
                "pairs: logger.info('event_name', key=value) instead of "
                "f-string formatting."
            ),
        )
```

### File size linter

Enforce maximum file size to prevent monolithic modules.

```python
"""
Linter: enforce maximum file size.

Files exceeding MAX_LINES MUST be split into focused modules.
"""
MAX_LINES = 300

def check_file_size(file_path, line_count):
    if line_count > MAX_LINES:
        yield LintError(
            file=file_path,
            line=line_count,
            message=(
                f"File has {line_count} lines (max {MAX_LINES}). "
                "Split into focused modules. Group by responsibility, "
                "not by feature."
            ),
        )
```

## Structural test templates

### Domain structure test

Assert every domain follows the fixed layer set.

```python
"""
Structural test: assert each business domain follows the layer model.

Each domain MUST contain directories matching the fixed layer set.
Extra directories are flagged. Missing directories are flagged.
"""
REQUIRED_LAYERS = {"types", "config", "repo", "service", "runtime", "ui"}

def test_domain_structure(domains_path):
    for domain in Path(domains_path).iterdir():
        if not domain.is_dir():
            continue
        actual = {d.name for d in domain.iterdir() if d.is_dir()}
        missing = REQUIRED_LAYERS - actual
        extra = actual - REQUIRED_LAYERS - {"providers"}
        assert not missing, (
            f"Domain '{domain.name}' is missing layers: {missing}. "
            f"Required layers: {REQUIRED_LAYERS}."
        )
        assert not extra, (
            f"Domain '{domain.name}' has unexpected directories: {extra}. "
            "Only the standard layers and 'providers' are allowed."
        )
```

### Provider interface test

Assert cross-cutting concerns enter through a single interface.

```python
"""
Structural test: assert providers use a declared interface.

Each provider MUST export a single interface module. Direct imports
from provider internals are disallowed.
"""
def test_provider_interfaces(providers_path):
    for provider in Path(providers_path).iterdir():
        if not provider.is_dir():
            continue
        interface_file = provider / "interface.py"
        assert interface_file.exists(), (
            f"Provider '{provider.name}' lacks interface.py. "
            "All providers MUST expose a single interface module."
        )
```

## Taste invariants

Encode style preferences as mechanical rules:

| Invariant | Enforcement |
| --- | --- |
| Structured logging | Custom lint rejects string-formatted log calls |
| Schema naming | Lint asserts `*Schema` suffix on validation types |
| File size | Lint rejects files exceeding the line limit |
| Boundary parsing | Structural test asserts validation at domain edges |
| No YOLO data access | Lint flags raw dict/list access without schema validation |

When human reviewers flag a pattern repeatedly, promote it from a comment convention to a lint rule. The goal is to capture taste once and enforce it continuously.

## Common mistakes

- Writing lints with vague error messages. Agents need specific remediation instructions in the error output.
- Enforcing style preferences that do not affect correctness, maintainability, or agent legibility. Focus on what matters.
- Allowing exceptions without encoding them. If a rule has legitimate exceptions, add the exception to the lint logic, not to a comment.
- Postponing linter creation until the codebase grows large. In agent-first development, linters are an early prerequisite for speed.
