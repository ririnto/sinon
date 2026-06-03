# Standard Rule Interface Specification

Define uniform interface contract for harness validation rule implementations across Gradle, Maven, uv, and Bun runtimes. Use this specification when aligning rule class signatures, context patterns, and finding shapes across stacks.

## Purpose and Scope

Three runtime stacks (Gradle, uv, Bun) implement rule-based harness validation with code-level rule classes. Maven follows the same pattern; shell does not (single script, no class abstraction). This document codifies interface expectations to:

- Enable consistent rule authoring experience across language boundaries
- Guide refactoring efforts when signature harmonization occurs
- Establish baseline for finding location metadata
- Document RuleContext as the single point of manifest and filesystem access

Status: This specification is normative for future implementations. Adoption timing per stack varies; each stack MUST align its base class and context pattern before introducing new rules of that category.

Shell exception: Single-script validation (no class abstraction) is out of scope; shell rules existing prior to this specification MAY remain unchanged.

## Current State

### Gradle (Kotlin)

```kotlin
abstract class HarnessCheckRule {
    abstract val category: String
    open fun applies(ctx: RuleContext): Boolean = ctx.manifest.isEnabled(category)
    abstract fun validate(ctx: RuleContext): Collection<Finding>
}

abstract class HarnessAstRule : HarnessCheckRule() {
    override fun validate(ctx: RuleContext): Collection<Finding> = emptyList()
    abstract fun renderAstFindings(ctx: RuleContext, findings: Collection<AstFinding>): Collection<Finding>
    abstract fun findAstFindings(file: Path, ctx: RuleContext, astFactory: KtPsiFactory?): Collection<AstFinding>
}

data class Finding(
    val severity: Severity,  // enum
    val category: String,
    val message: String
)

interface RuleContext {
    val root: Path
    val manifest: Manifest
    fun readSafe(path: String): String
    fun walkSafe(base: Path): WalkResult
    fun isAllowedRootContractSymlink(path: Path): Boolean
}

interface Manifest {
    val raw: JsonObject
    fun isEnabled(category: String): Boolean
    fun severityOf(category: String): Severity
    fun stringArray(category: String, key: String): List<String>
    fun stringValue(category: String, key: String): String
    fun categoryObject(category: String): JsonObject?
}

data class AstFinding(
    val rule: String,
    val file: String,
    val line: Int,
    val details: Map<String, String> = emptyMap(),
)

object JsonAccess {
    fun stringFromObject(obj: JsonObject?, key: String): String
    fun stringArrayFromObject(obj: JsonObject?, key: String): List<String>
}

object AstFindingRenderer {
    fun renderEach(findings: List<AstFinding>, manifest: JsonObject): List<Finding>
    fun render(finding: AstFinding, manifest: JsonObject): Finding
}
```

### uv (Python)

```python
class HarnessCheckRule(ABC):
    @abstractmethod
    def applies(self, manifest: JsonObject) -> bool:
        """
        Check if this rule applies to the manifest.
        """

    @abstractmethod
    def validate(self, project_dir: Path, manifest: JsonObject) -> Iterable[Finding]:
        """
        Validate and return findings.
        """

    # Static utility methods: is_relative_to, read_text, is_executable, first_line, relative, allowed_root_contract_target, is_safe_file, is_safe_directory, safe_walk, safe_file_or_walk, is_json_object, is_json_array, json_array, load_manifest, severity_for, stack_sources, parse_python, has_nested_function

class Finding(NamedTuple):
    severity: str  # "ERROR", "WARN", "INFO" (string literal, not enum)
    category: str
    message: str
```

### Bun (TypeScript)

```typescript
export interface HarnessCheckRule {
    readonly category: string;
    applies(manifest: HarnessManifest): boolean;
    validate(projectDir: string, manifest: HarnessManifest): readonly Finding[];
}

export interface Finding {
    severity: "ERROR" | "WARN" | "INFO";  // Union type literal
    category: string;
    message: string;
}

export interface RuleContext {
    pathOf(path: string): string;
    read(path: string): string;
    firstLine(path: string): string;
    isFile(path: string): boolean;
    isDirectory(path: string): boolean;
    isExecutablePath(path: string): boolean;
    isSymlink(path: string): boolean;
    allowedRootContractTarget(path: string): string | null;
    readStringArray(value: unknown): readonly string[];
    readJsonObject(value: unknown): Record<string, unknown>;
    severityOf(manifest: HarnessManifest, category: string): "ERROR" | "WARN" | "INFO";
    stackSources(manifest: HarnessManifest, category: string, stack: string): readonly string[];
    walkDirectory(path: string): readonly [readonly string[], readonly Finding[]];
    collectFilesUnder(path: string): readonly [readonly string[], readonly Finding[]];
}
```

### Differences Summary

| Aspect | Gradle | uv | Bun | Maven |
| --- | --- | --- | --- | --- |
| Base type | abstract class | abstract base class (ABC) | interface | Java interface |
| applies() params | `ctx: RuleContext` | `manifest: JsonObject` | `manifest: HarnessManifest` | `manifest: JsonNode` |
| validate() params | `ctx: RuleContext` | `project_dir: Path, manifest: JsonObject` | `projectDir: string, manifest: HarnessManifest` | `root: Path, manifest: JsonNode` |
| Root type | `Path` (kotlin.io.path) | `Path` (pathlib) | `string` | `Path` (java.nio.file) |
| Finding.severity | enum (Severity) | string literal ("ERROR" \| "WARN" \| "INFO") | union type ("ERROR" \| "WARN" \| "INFO") | string literal ("ERROR" \| "WARN" \| "INFO") |
| Utility exposure | helper objects (JsonAccess, AstSupport, AstFindingRenderer) | static methods on HarnessCheckRule plus rules/utils.py re-exports | RuleContext interface captured by rule factories | HarnessCheckHelper static methods |
| Manifest access | via RuleContext.manifest (Manifest interface) | manifest dict plus HarnessCheckRule helpers | manifest argument plus RuleContext helpers | JsonNode plus HarnessCheckHelper |
| Filesystem access | RuleContext.readSafe(), walkSafe(), isAllowedRootContractSymlink() | HarnessCheckRule safe helpers | RuleContext filesystem helpers | HarnessCheckHelper safe helpers |

## Target Specification (Gradle Implementation Reference)

### Rule Interface Contract

All stack rule implementations expose three core methods: `applies`, `validate`, and `format`. Gradle (Kotlin) currently implements the target `RuleContext` signature fully; uv (Python), Bun (TypeScript), and Maven (Java) currently retain stack-native signatures while using centralized registries and shared helpers. The `format` method is OPTIONAL for rules without an automatic fix; the base interface MUST provide a no-op default that returns an empty collection.

#### Gradle (Kotlin) — Current Implementation

```kotlin
// Base rule class
abstract class HarnessCheckRule {
    abstract val category: String
    open fun applies(ctx: RuleContext): Boolean = ctx.manifest.isEnabled(category)
    abstract fun validate(ctx: RuleContext): Collection<Finding>
    open fun format(ctx: RuleContext): Collection<Path> = emptyList()
}

// AST-based rules (optional extension)
abstract class HarnessAstRule : HarnessCheckRule() {
    override fun validate(ctx: RuleContext): Collection<Finding> = emptyList()
    abstract fun renderAstFindings(ctx: RuleContext, findings: Collection<AstFinding>): Collection<Finding>
    abstract fun findAstFindings(file: Path, ctx: RuleContext, astFactory: KtPsiFactory?): Collection<AstFinding>
}
```

```python
# Python (target)
@abstractmethod
def applies(self, ctx: RuleContext) -> bool:
    """Check if rule applies; return True if applicable."""

@abstractmethod
def validate(self, ctx: RuleContext) -> Iterable[Finding]:
    """Run validation; return collection of findings."""
```

```typescript
// TypeScript (target)
applies(ctx: RuleContext): boolean;
validate(ctx: RuleContext): readonly Finding[];
```

Rationale: Consolidating manifest and filesystem context into a single `ctx` parameter reduces signature variance and centralizes policy for safe file I/O and manifest structure access. Gradle implementation provides the reference model.

### Target Parameter Model: RuleContext and Manifest

The target end-state is to pass context and manifest through dedicated interface abstractions:

- Kotlin: `RuleContext` interface with `val root: Path`, `val manifest: Manifest`; uses `kotlin.io.path.Path` (prefer over java.nio.file.Path per memory guidelines)
- Python: `RuleContext` class with `root: Path` (pathlib), `manifest: Manifest`
- TypeScript: `RuleContext` interface with `root: string` (absolute path), `manifest: HarnessManifest`

All path references SHOULD be resolved to absolute paths before crossing rule boundaries. The `Manifest` interface abstracts manifest queries (`isEnabled()`, `severityOf()`, `stringArray()`, `stringValue()`, `categoryObject()`, `raw`). Current uv, Bun, and Maven implementations enforce the same policy through stack-local helpers rather than a common `RuleContext` signature.

### Severity and Finding Shape

#### Severity enum/union (mandatory)

```kotlin
enum class Severity { ERROR, WARN, INFO }
```

```python
# Use constants, not bare strings
class Severity(str, Enum):
    ERROR = "ERROR"
    WARN = "WARN"
    INFO = "INFO"
```

```typescript
type Severity = "ERROR" | "WARN" | "INFO";
```

#### Finding MUST include optional location metadata

```kotlin
data class Finding(
    val severity: Severity,
    val category: String,
    val message: String,
    val location: Location? = null,  // NEW
)

data class Location(
    val file: Path,
    val line: Int? = null,
    val column: Int? = null,
)
```

```python
class Finding(NamedTuple):
    severity: Severity
    category: str
    message: str
    location: Location | None = None  # NEW

class Location(NamedTuple):
    file: Path
    line: int | None = None
    column: int | None = None
```

```typescript
export interface Finding {
    severity: Severity;
    category: string;
    message: string;
    location?: Location;  // NEW
}

export interface Location {
    file: string;
    line?: number;
    column?: number;
}
```

### RuleContext Pattern (Gradle Implementation)

All stacks MUST provide a `RuleContext` interface that encapsulates:

#### Project State

- `root: Path` — absolute project root path
- `manifest: Manifest` — parsed harness manifest (interface abstraction)

#### Manifest Interface (Gradle: DefaultManifest)

All implementations MUST provide:

- `isEnabled(category: String): Boolean` — check if category is enabled in manifest
- `severityOf(category: String): Severity` — fetch severity, default to ERROR
- `stringArray(category: String, key: String): List<String>` — extract string array from category parameters
- `stringValue(category: String, key: String): String` — extract string value from category parameters
- `categoryObject(category: String): JsonObject?` — fetch category's JSON object
- `raw: JsonObject` — direct access to raw manifest (for advanced queries)

#### Safe File I/O (Gradle: DefaultRuleContext)

Rules access filesystem through `RuleContext` methods:

- `readSafe(path: String): String` — read file text; resolve allowed root-contract symlinks; return empty on OSError
- `walkSafe(base: Path): WalkResult` — walk tree; exclude symlinks; return both paths and walk-violation findings
- `isAllowedRootContractSymlink(path: Path): Boolean` — check if a symlink is allowed (e.g., AGENTS.md ↔ CLAUDE.md)

#### Helper Objects (Gradle Implementation)

- `JsonAccess` (object): `stringFromObject()`, `stringArrayFromObject()` — safely extract JSON values
- `AstSupport` (object): `parse()`, `relativeFilePath()`, `lineOf()`, `hasDescendantOfType()` — PSI utilities
- `AstFindingRenderer` (object): `renderEach()`, `render()` — convert `AstFinding` to final `Finding`

#### Language-Specific Parsing

Target AST rules SHOULD receive parser support from runtime-level helpers. Gradle rules use `KtPsiFactory` through `HarnessAstRule.findAstFindings()`. Current uv and Bun rules use stack-local AST/token helpers while they continue aligning with the Gradle model.

## Rule Classification (Directory Organization)

Rules are grouped by validation concern:

| Group | Purpose | Examples |
| --- | --- | --- |
| fs | File and directory structure checks | file-presence, directory-presence, symlink-safety, empty-directory-placeholders |
| text | Text content and markup validation | doc-headings, doc-content, hook-shebang, hook-executable, agent-frontmatter, skill-frontmatter |
| ast | Code structure and syntax validation | public-declaration-doc-comment, leaf-function-blank-lines, early-return, silent-catch, hook-command |

### Target directory layout

```text
rules/
  fs/
    FilePresenceRule.kt  (Gradle)
    file_presence.py  (uv)
    file-presence.ts  (Bun)
    DirectoryPresenceRule.kt
    …
  text/
    DocHeadingsRule.kt  (Gradle, uses CommonMark AST)
    doc_headings.py  (uv)
    doc-headings.ts  (Bun)
    SkillFrontmatterRule.kt  (Gradle, uses YamlFrontMatterVisitor)
    AgentFrontmatterRule.kt
    …
  ast/  (Gradle: Kotlin-specific AST via PSI; uv/Bun: language-specific)
    PublicDeclarationDocCommentRule.kt  (Gradle)
    public_declaration_doc_comment.py  (uv)
    public-declaration-doc-comment.ts  (Bun)
    LeafFunctionBlankLinesRule.kt
    NonNullAssertionRule.kt  (Gradle, checks `!!` operator)
    UncheckedCastSuppressionRule.kt  (Gradle, checks `@Suppress("UNCHECKED_CAST")`)
    …
```

### Gradle Implementation Notes

- text/ rules use CommonMark AST parser (org.commonmark:commonmark 0.28.0) for Markdown validation
- text/ rules use YamlFrontMatterVisitor for YAML frontmatter extraction
- ast/ rules extend `HarnessAstRule` and use `KtPsiFactory` for Kotlin AST traversal

## Manifest Key → Rule Class Mapping Matrix

Harness validation rules are indexed by manifest category keys (camelCase). This matrix maps each key to its runtime implementation(s). Gradle (Kotlin) package names use `com.ririnto.sinon.harness.rules.fs.*`, `.rules.text.*`, and `.rules.ast.*`. Maven (Java) mirrors that grouping under `com.ririnto.sinon.harness.rules.fs`, `.rules.text`, and `.rules.ast`, with shared interfaces in `com.ririnto.sinon.harness.rules`. uv currently keeps rules flat under `runtime/rules/`.

| Manifest Key | Gradle (Kotlin) | uv (Python) | Bun (TypeScript) | Maven (Java) | Group |
| --- | --- | --- | --- | --- | --- |
| agentFrontmatter | text/AgentFrontmatterRule.kt | runtime/rules/agent_frontmatter.py | rules/agent-frontmatter.ts | text/AgentFrontmatterRule.java | text |
| classMemberOrdering | | | | ast/ClassMemberOrderingRule.java | ast |
| ciHookCommandParity | text/CiHookCommandParityRule.kt | runtime/rules/ci_hook_command_parity.py | rules/ci-hook-command-parity.ts | text/CiHookCommandParityRule.java | text |
| companionObjectPosition | ast/CompanionObjectPositionRule.kt | | | | ast |
| directoryPresence | fs/DirectoryPresenceRule.kt | runtime/rules/directory_presence.py | rules/directory-presence.ts | fs/DirectoryPresenceRule.java | fs |
| docContent | text/DocContentRule.kt | runtime/rules/doc_content.py | rules/doc-content.ts | text/DocContentRule.java | text |
| docHeadings | text/DocHeadingsRule.kt (CommonMark AST) | runtime/rules/doc_headings.py | rules/doc-headings.ts | text/DocHeadingsRule.java | text |
| emptyDirectoryPlaceholders | fs/EmptyDirectoryPlaceholdersRule.kt | runtime/rules/empty_directory_placeholders.py | rules/empty-directory-placeholders.ts | fs/EmptyDirectoryPlaceholdersRule.java | fs |
| envShebangUsage | text/EnvShebangUsageRule.kt | runtime/rules/env_shebang_usage.py | rules/env-shebang-usage.ts | text/EnvShebangUsageRule.java | text |
| filePresence | fs/FilePresenceRule.kt | runtime/rules/file_presence.py | rules/file-presence.ts | fs/FilePresenceRule.java | fs |
| hookCommand | text/HookCommandRule.kt | runtime/rules/hook_command.py | rules/hook-command.ts | text/HookCommandRule.java | text/ast |
| hookExecutable | text/HookExecutableRule.kt | runtime/rules/hook_executable.py | rules/hook-executable.ts | text/HookExecutableRule.java | text |
| hookGeneratedMarker | text/HookGeneratedMarkerRule.kt | runtime/rules/hook_generated_marker.py | rules/hook-generated-marker.ts | text/HookGeneratedMarkerRule.java | text |
| hookShebang | text/HookShebangRule.kt | runtime/rules/hook_shebang.py | rules/hook-shebang.ts | text/HookShebangRule.java | text |
| hookStage | text/HookStageRule.kt | runtime/rules/hook_stage.py | rules/hook-stage.ts | text/HookStageRule.java | text |
| ifStatementBraces | ast/IfStatementBracesRule.kt | | rules/if-statement-braces.ts | ast/IfStatementBracesRule.java | ast |
| implicitLambdaIt | ast/ImplicitLambdaItRule.kt | | rules/implicit-lambda-it.ts | | ast |
| importOverFqn | ast/ImportOverFqnRule.kt | | | ast/ImportOverFqnRule.java | text/ast |
| kotlinTopLevelDeclarationCount | ast/KotlinTopLevelDeclarationCountRule.kt | | | | ast |
| leafFunctionBlankLines | ast/LeafFunctionBlankLinesRule.kt (check-only) | `ruff format` | `oxfmt` | ast/LeafFunctionBlankLinesRule.java | text/ast |
 | mutableCollection | ast/MutableCollectionRule.kt | runtime/rules/ast/mutable_collection.py | rules/mutable-collection.ts | ast/MutableCollectionRule.java | ast |
| nonNullAssertion | ast/NonNullAssertionRule.kt (checks `!!`) | | | | ast |
| publicDeclarationDocComment | ast/PublicDeclarationDocCommentRule.kt | runtime/rules/public_declaration_doc_comment.py | rules/public-declaration-doc-comment.ts | ast/PublicDeclarationDocCommentRule.java | text/ast |
| leadingUnderscore | ast/LeadingUnderscoreRule.kt | runtime/rules/leading_underscore.py | rules/leading-underscore.ts | ast/LeadingUnderscoreRule.java | ast |
| multilineDocStyle | ast/MultilineDocStyleRule.kt | runtime/rules/multiline_doc_style.py | rules/multiline-doc-style.ts | ast/MultilineDocStyleRule.java | ast |
| scaffoldLeaks | fs/ScaffoldLeaksRule.kt | runtime/rules/scaffold_leaks.py | rules/scaffold-leaks.ts | text/ScaffoldLeaksRule.java | fs/text |
| skillFrontmatter | text/SkillFrontmatterRule.kt (YamlFrontMatterVisitor) | runtime/rules/skill_frontmatter.py | rules/skill-frontmatter.ts | text/SkillFrontmatterRule.java | text |
| symlinkSafety | fs/SymlinkSafetyRule.kt | runtime/rules/symlink_safety.py | rules/symlink-safety.ts | fs/SymlinkSafetyRule.java | fs |
| templateGroups | fs/TemplateGroupsRule.kt | runtime/rules/template_groups.py | rules/template-groups.ts | text/TemplateGroupsRule.java | fs/text |
| terminalBranchWhen | ast/TerminalBranchWhenRule.kt | | | | ast |
| tripleQuoteInlineComment | | runtime/rules/triple_quote_inline_comment.py (tokenize) | | | text/token |
| uncheckedCastSuppression | ast/UncheckedCastSuppressionRule.kt (checks `@Suppress("UNCHECKED_CAST")`) | | | | ast |
| uncheckedTasks | fs/UncheckedTasksRule.kt | runtime/rules/unchecked_tasks.py | rules/unchecked-tasks.ts | text/UncheckedTasksRule.java | fs/text |
| unstructuredLogging | text/UnstructuredLoggingRule.kt | runtime/rules/unstructured_logging.py | rules/unstructured-logging.ts | ast/UnstructuredLoggingRule.java | text/ast |
| wildcardImport | ast/WildcardImportRule.kt | runtime/rules/wildcard_import.py | rules/wildcard-import.ts | ast/WildcardImportRule.java | text/ast |

### Legend

- Cell blank = category not yet implemented in that stack
- Group column indicates primary category; rules spanning multiple groups show all applicable
- Gradle classes in `com.ririnto.sinon.harness.rules.*` package hierarchy

## Gradle Implementation Details (Reference for Other Stacks)

### Package Structure

```text
com.ririnto.sinon.harness.
├── core/
│   ├── RuleContext.kt (interface)
│   ├── Manifest.kt (interface)
│   ├── DefaultRuleContext.kt (implementation)
│   ├── DefaultManifest.kt (implementation)
│   ├── Severity.kt (enum)
│   ├── JsonAccess.kt (object: utilities)
│   └── HarnessCheck.kt (enum: registry)
├── rules/
│   ├── HarnessCheckRule.kt (abstract base)
│   ├── HarnessAstRule.kt (AST-specific base)
│   ├── fs/
│   │   ├── FilePresenceRule.kt
│   │   ├── DirectoryPresenceRule.kt
│   │   └── …
│   ├── text/
│   │   ├── DocHeadingsRule.kt (uses CommonMark AST)
│   │   ├── SkillFrontmatterRule.kt (uses YamlFrontMatterVisitor)
│   │   ├── AgentFrontmatterRule.kt (uses YamlFrontMatterVisitor)
│   │   └── …
│   └── ast/
│       ├── NonNullAssertionRule.kt
│       ├── UncheckedCastSuppressionRule.kt
│       ├── WildcardImportRule.kt
│       └── …
├── ast/
│   ├── AstFinding.kt (data class)
│   ├── AstSupport.kt (object: PSI utilities)
│   ├── AstFindingRenderer.kt (object: rendering)
│   └── HarnessAstResults.kt
└── plugin/
    ├── HarnessValidationPlugin.kt
    ├── HarnessValidationTask.kt (Gradle task)
    └── HarnessAstWorkAction.kt (Worker API action)
```

### Gradle Worker API Pattern

Kotlin AST analysis runs in an isolated classloader using Gradle Worker API:

```kotlin
workerExecutor
    .classLoaderIsolation { classpath.from(kotlinCompiler) }
    .apply {
        submit(HarnessAstWorkAction::class.java) {
            srcFilePaths.set(listOf(…))
            rootDir.set(…)
            manifestText.set(…)
            outputFile.set(…)
        }
    }
    .await()
```

This pattern prevents Kotlin compiler classpath conflicts by running analysis in a separate JVM classloader.

### CommonMark Integration

Text rules using Markdown validation use CommonMark AST:

```kotlin
val parser = Parser.builder().extensions(listOf(
    YamlFrontMatterExtension.create()
)).build()
val document = parser.parse(content)
document.accept(object : AbstractVisitor() {
    override fun visit(heading: Heading) { … }
})
```

Dependencies: `org.commonmark:commonmark:0.28.0`, `org.commonmark:commonmark-ext-yaml-front-matter:0.28.0`

### kotlin.io.path Usage

Path operations use Kotlin stdlib extensions:

```kotlin
file.readText()  // No imports needed
file.invariantSeparatorsPathString  // Normalized path string
file.isDirectory()
file.isRegularFile()
file.walk()  // Directory traversal
file.relativeTo(root)
```

## Migration Path (for uv, Bun)

When aligning a stack to the Gradle model, follow this order:

1. Define RuleContext and Manifest interfaces — all filesystem and manifest access flows through context
2. Migrate applies() and validate() signatures — consolidate parameters into `ctx: RuleContext`
3. Create helper objects/utilities — expose helpers as context methods or separate utility classes (e.g., JsonAccess)
4. Ensure Severity is type-safe — adopt enums or union types; eliminate bare string literals
5. Organize rules into fs/text/ast directories — group by validation concern (matching Gradle layout)
6. Add rule-to-manifest registry — maintain a centralized rule enum (like Gradle's HarnessCheck)
7. Run full validation suite — ensure behavior unchanged after refactoring

## Notes

- Coordinated signature changes: Rule signature changes are acceptable when adoption is coordinated across a stack release cycle. Gradle has completed this migration as of Phase 2.
- Parser ownership: Each stack MUST prevent rules from instantiating parsers directly. Parsers (KtPsiFactory, CommonMark, YAML) MUST be injected or cached at runtime level, never within rule constructors.
- Path absoluteness: All paths crossing rule boundaries MUST be absolute. Relative paths are internal optimization only (e.g., display in messages via `file.relativeTo(root)`).
- Manifest safety: RuleContext/Manifest helpers (stringArray, stringValue, severityOf, categoryObject) MUST enforce type safety and return safe defaults on manifest structure mismatch (empty string, empty list, ERROR severity, null).
- Finding severity: All findings MUST use the Severity enum (Gradle), Severity enum (uv target), or union type (Bun target). String literals are prohibited.
- Rule registration: Each stack MUST maintain a centralized rule registry (Gradle: HarnessCheck enum) mapping manifest categories to rule instances, enabling safe lookup and invocation without reflection.
- CommonMark adoption: Text rules validating Markdown (doc headings, frontmatter) SHOULD use CommonMark AST parsing (0.28.0 or compatible) rather than regex for robustness.
- Python token rules: Physical-line Python checks such as `tripleQuoteInlineComment` SHOULD use the stdlib `tokenize` module so strings and comments are classified as tokens rather than text fragments.
- Collection return types: Rule `validate()` methods return `Collection<Finding>` (Gradle) or language equivalents. buildList/buildSet may be used; early guards like `?: return@buildList` or `let { … }` are idiomatic.
