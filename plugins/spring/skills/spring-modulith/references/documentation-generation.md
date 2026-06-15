# Documentation Generation

Open this reference when module arrangement diagrams and canvases must be generated for developer documentation.

## Documentation boundary

Use the `Documenter` API to generate architectural diagrams and module canvases from the application module model.
This runs in tests and writes output to the build folder.

## Required test dependency

```xml
<dependency>
    <groupId>org.springframework.modulith</groupId>
    <artifactId>spring-modulith-docs</artifactId>
    <scope>test</scope>
</dependency>
```

## Common documentation test shape

```java
class DocumentationTests {

    ApplicationModules modules = ApplicationModules.of(Application.class);

    @Test
    void writeDocumentationSnippets() {
        new Documenter(modules)
            .writeModulesAsPlantUml()
            .writeIndividualModulesAsPlantUml()
            .writeModuleCanvases()
            .writeAggregatingDocument();
    }
}
```

## Diagram style

Default is C4.
Switch to UML component diagrams:

```java
new Documenter(modules, DiagramOptions.defaults()
    .withStyle(DiagramStyle.UML))
    .writeModulesAsPlantUml();
```

## Canvas internals

By default, the canvas hides non-exported types.
Reveal internal types:

```java
new Documenter(modules, CanvasOptions.defaults()
    .revealInternals())
    .writeModuleCanvases();
```

## Output location

Default output goes to `spring-modulith-docs` in the build folder (e.g., `target/spring-modulith-docs` for Maven).
The aggregating document is `all-docs.adoc`.

## Generated artifacts

| Method | Output |
| --- | --- |
| `writeModulesAsPlantUml()` | Single C4 diagram showing all modules |
| `writeIndividualModulesAsPlantUml()` | Per-module C4 diagrams with direct dependencies |
| `writeModuleCanvases()` | Tabular canvas per module listing beans, events, properties |
| `writeAggregatingDocument()` | Single Asciidoctor file linking all diagrams and canvases |

## Canvas sections

Each generated canvas includes:

- Base package name
- Spring components grouped by stereotype (services, repositories, listeners)
- Exposed aggregate roots
- Published events with their creating methods
- Events listened to
- Configuration properties (requires `spring-boot-configuration-processor`)

## Decision points

| Situation | Use |
| --- | --- |
| Architecture documentation needs module diagrams | `writeModulesAsPlantUml()` |
| Per-module dependency diagrams | `writeIndividualModulesAsPlantUml()` |
| Developer-facing module overview tables | `writeModuleCanvases()` |
| Single aggregated document for inclusion | `writeAggregatingDocument()` |
| Legacy visual style | `DiagramStyle.UML` |

## Verification rule

Verify the documentation test runs in CI and produces the expected output files so diagrams stay synchronized with the module structure.
