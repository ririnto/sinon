# Architecture Enforcement

Open this reference when translating the target repository's documented layer model, source roots, and checks into deterministic architecture enforcement.

## Config-first rule

The target repo's `docs/harness-engineering/harness-engineering.json` is this plugin's six-field machine-readable config convention, and root `ARCHITECTURE.md` is the human architecture source. The optional layer sequence below follows the OpenAI article's default business-domain model as an example only: `types -> config -> repo`, Providers feeding `service -> runtime -> ui`, and app wiring, UI, and utilities outside the domain boundary.

```yaml
layer_model:
  name: default-domain-layers
  roots:
    - src/domains
  layers:
    - types
    - config
    - repo
    - providers
    - service
    - runtime
    - ui
  allowed_edges:
    types: []
    config: [types]
    repo: [config, types]
    providers: [repo, config, types]
    service: [providers, repo, config, types]
    runtime: [service, providers, repo, config, types]
    ui: [runtime, service, providers, types]
```

If the repo uses `domain -> application -> infrastructure`, hexagonal ports, modules, or another model, encode that model instead of forcing the default.

## Structural check shape

```python
from pathlib import Path


"""
Validate configured domain layer directories.

:param architecture: Parsed architecture-doc model block.
:return: List of human-readable violations.
"""
def validate_layer_directories(architecture):
    violations = []
    layer_model = architecture.get("layer_model", {})
    layers = set(layer_model.get("layers", []))
    for root in layer_model.get("roots", []):
        root_path = Path(root)
        if not root_path.exists():
            violations.append(f"Configured layer root is missing: {root}")
            continue
        for domain_path in root_path.iterdir():
            if not domain_path.is_dir():
                continue
            actual = {path.name for path in domain_path.iterdir() if path.is_dir()}
            missing = layers - actual
            if missing:
                violations.append(f"{domain_path} is missing configured layers: {sorted(missing)}")
    return violations
```

## Import check shape

```python
"""
Check an import edge against configured allowed edges.

:param source_layer: Layer containing the importing file.
:param target_layer: Layer containing the imported file.
:param allowed_edges: Mapping from layer to allowed target layers.
:return: Error message or None.
"""
def check_edge(source_layer, target_layer, allowed_edges):
    if target_layer not in allowed_edges.get(source_layer, []):
        return f"Layer violation: {source_layer} imports {target_layer}; allowed targets are {allowed_edges.get(source_layer, [])}"
    return None
```

## Taste invariants

Place target-specific taste rules under `checks` or a referenced docs file. Good candidates are file size, generated-file boundaries, structured logging, boundary parsing, naming conventions, and internal dependency rules. Do not call a preference mandatory unless a script, CI job, hook, shell-wrapper check, or documented review policy can verify it.

## Completion checks

- Documented source roots and layer roots exist or are reported as explicit pending scaffold actions.
- Every declared layer has a clear allowed-edge policy.
- Architecture docs describe the same layer model used by structural checks.
- CI or hooks run the validator or target-specific architecture check.
