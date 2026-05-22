# 2026-05-23-ast-based-style-checks

- Status: active
- Created: 2026-05-23
- Last Updated: 2026-05-23
- Completed:
- Author: ririnto
- Assignee: ririnto

## Goal

stack-별 코드 스타일 검사를 *AST 기반*으로 도입한다. 정확성 우선(빌드 시간 무관).

신규 add-on (이미 manifest에 등록됨):

- `forbidGreaterThanComparison` (enabled): `>`/`>=` 대신 `<`/`<=` 만 허용.
- `forbidBlankLineInLeafFunction` (enabled): nested function이 없는 *leaf* 함수 본문에 공백 라인 금지.
- `forbidEarlyReturn`, `forbidSilentCatch`, `forbidMutableCollection`, `forbidUnstructuredLogging`, `forbidWildcardImport`, `requireImportOverFqn` (WARN), `requireDocCommentOnPublicDeclaration` (WARN), `forbidEmptyCatchBlock` — 모두 disabled로 등록. 구현 후 enable.

기존 정규식 기반 검사 마이그레이션 대상:

- `forbidImplicitLambdaIt` (Kotlin)
- `requireSingleTopLevelKotlinDeclaration` (Kotlin)

모든 검사는 stack-native AST API 사용. 정규식 금지.

## manifest Schema (확정)

각 stack-specific add-on의 `parameters` shape:

```text
parameters:
  sourceRootsPerStack:
    kotlin:    [path-glob, ...]   # 예: ["buildSrc/src/main/kotlin", "**/src/main/kotlin"]
    java:      [path-glob, ...]
    python:    [path-glob, ...]
    typescript:[path-glob, ...]
  extensionsPerStack:
    kotlin:    ["kt", "kts"]      # dot 없음
    java:      ["java"]
    python:    ["py"]
    typescript:["ts", "tsx"]
```

각 stack validator는 자기 stack key만 읽어 source root glob 확장 → 해당 확장자 파일 enumerate → AST로 파싱 → check 적용 → `Finding` 반환.

## Plan Convention

- Phase는 순차. 같은 phase 내 task는 독립 파일이라 병렬 가능하지만, 한 stack 내 task는 동일 파일을 다루므로 *stack 단위로 한 sub-agent에 통합*.
- 각 stack section은 self-contained하게 sub-agent prompt로 활용 가능.

## Phases

### [x] Phase 1: manifest 신규 add-on 등록 (완료)

- [x] Task 1.1 — manifest.json에 11개 add-on 등록(2 enabled + 8 disabled + 기존 Kotlin 2개)
- [x] Task 1.2 — `sourceRootsPerStack` (glob) + `extensionsPerStack` (dot-less) schema 확정

### [x] Phase 2: Python AST (uv stack) — sub-agent prompt 자료 ↓ (Phase 400~417에 dispatch 완료 보고)

대상 파일: `plugins/harness/skills/harness-install/assets/uv/runtime/harness_check.py`

기존 구조 (참고):

- 모듈 상단: `import ast`는 *아직 import되어 있지 않음*. 새로 추가.
- `class Finding(NamedTuple)` 정의 — 모든 finding 객체.
- `class HarnessCheck(enum.Enum)` — 각 enum member는 `(category_str, validator_callable)` tuple. validator는 `(root: Path, manifest: dict) -> tuple[Finding, ...]` 시그니처.
- 기존 validator 함수는 모두 `_validate_*` 함수로 module-level에 정의되어 enum value에서 참조.

추가할 helper:

- `def stack_sources(manifest: dict, category: str) -> tuple[Path, ...]`:
  - `manifest[category]["parameters"]["sourceRootsPerStack"]["python"]` 읽기
  - glob 확장: `Path(".").glob(root_glob)` 또는 단일 경로면 `Path(root)`.
  - 각 glob 결과 디렉토리 안에서 `rglob("*.py")` (실제로는 manifest의 extensionsPerStack 사용)
  - dot-less normalize: `path.suffix.lstrip('.') in extensions`
  - 정렬된 tuple 반환.

- `def parse_python(path: Path) -> ast.AST | None`:
  - `ast.parse(path.read_text(encoding="utf-8"), filename=str(path))`
  - SyntaxError 시 `Finding("ERROR", category, f"{path}: syntax error: {err}")` 반환하지 않고 *None* + caller에서 `Finding` 생성 — 또는 helper가 직접 Finding tuple 반환. 어느 쪽이든 단일 컨벤션 유지.

추가할 enum member 2개 (이번 phase 범위):

- `FORBID_GREATER_THAN_COMPARISON = ("forbidGreaterThanComparison", _validate_forbid_greater_than_comparison)`
- `FORBID_BLANK_LINE_IN_LEAF_FUNCTION = ("forbidBlankLineInLeafFunction", _validate_forbid_blank_line_in_leaf_function)`

`_validate_forbid_greater_than_comparison(root, manifest)`:

- enabled / severity 확인 (기존 helper `severity_for`, `_enabled_for` 패턴 따라).
- 각 source file에 대해:
  - `tree = parse_python(path)`
  - `for node in ast.walk(tree): if isinstance(node, ast.Compare):` operators 검사
  - `node.ops`의 각 `op`가 `ast.Gt` 또는 `ast.GtE`이면 Finding 추가
  - `lineno` 사용해 message 생성: `f"{relative(path)}:{node.lineno}: forbidden ..."`

`_validate_forbid_blank_line_in_leaf_function(root, manifest)`:

- 각 source file:
  - `tree = parse_python(path)`
  - `for node in ast.walk(tree): if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):` 함수 정의 발견
  - 내부에 nested `FunctionDef`/`AsyncFunctionDef`/`Lambda`가 *없는지* 검사: `any(isinstance(child, (ast.FunctionDef, ast.AsyncFunctionDef, ast.Lambda)) for child in ast.walk(node.body[i]) for i in ...)` — 더 단순하게 `_has_nested_function(node)` helper.
  - leaf이면 함수 body의 줄 범위 (`node.lineno + 1` ~ `node.end_lineno`) 안에서 *raw source*의 빈 줄 찾기:
    - `source_lines = path.read_text().splitlines()`
    - `for line_no in range(node.body[0].lineno, node.end_lineno + 1):` (decorator/signature 제외)
    - `if source_lines[line_no - 1].strip() == "":` Finding 추가.

위반/통과 fixture (sub-agent가 임시 생성, validator 실행 후 위반 잡힘 확인):

```python
# violation: forbidden > comparison
def f(a, b):
    if a > b:
        return a
    return b
```

```python
# violation: leaf function blank line
def g(x):
    y = x + 1

    return y
```

```python
# pass: outer function with nested is exempt
def outer():
    def inner():
        return 1

    return inner
```

검증 명령:

```sh
cd /tmp/harness-target
python3 docs/harness/uv/harness_validate.py
# expect: ERROR findings on violation fixtures, no findings on pass fixtures
```

- [ ] Task 2.1 — uv harness_check.py에 helper + 2 enum value + validator 함수 추가
- [ ] Task 2.2 — 위반/통과 fixture로 검증

### [x] Phase 3: TypeScript AST (bun stack) — sub-agent prompt 자료 ↓ (Phase 400~417에 dispatch 완료 보고)

대상 파일: `plugins/harness/skills/harness-install/assets/bun/runtime/harness-check.ts`

기존 구조 (참고):

- `interface HarnessCheckSpec { category; severityDefault; applies; validate; }` (정확한 시그니처는 파일 상단 14-21줄 참고).
- `export const HARNESS_CHECKS: readonly HarnessCheckSpec[]` 배열에 모든 check 등록 (line 1090 부근).
- `function readStringArray`, `function readJsonObject`, `function severityOf` 등 helper 존재.
- TypeScript compiler API를 위해 `import * as ts from "typescript"`로 사용 — `typescript` 패키지를 `package.json` 또는 `import` 첫 줄에서 import.

설치:

```sh
cd plugins/harness/skills/harness-install/assets/bun/runtime
bun add typescript@^5
# bun runtime에서 typescript의 ts.createSourceFile / ts.forEachChild 사용 가능
```

`package.json`이 없으면 만들고 dependencies에 typescript 등록. 다만 plugin source에 node_modules를 commit하지 않도록 .gitignore 확인.

추가 helper:

```ts
function stackSources(manifest: Manifest, category: string): readonly string[] {
  const params = manifest[category].parameters as Record<string, unknown>;
  const roots = (params.sourceRootsPerStack as Record<string, readonly string[]>).typescript;
  const exts = (params.extensionsPerStack as Record<string, readonly string[]>).typescript;
  const files: string[] = [];
  for (const root of roots) {
    // bun-native glob:
    const glob = new Bun.Glob(`${root}/**/*`);
    for (const entry of glob.scanSync(".")) {
      const ext = entry.split(".").pop() ?? "";
      if (exts.includes(ext)) {
        files.push(entry);
      }
    }
  }
  return files;
}
```

추가할 spec 2개 (`HARNESS_CHECKS` 배열 끝에 push):

```ts
{
  category: "forbidGreaterThanComparison",
  severityDefault: "ERROR",
  applies(manifest) { return manifest[this.category]?.enabled !== false; },
  validate(root, manifest) {
    const findings: Finding[] = [];
    for (const file of stackSources(manifest, this.category)) {
      const src = ts.createSourceFile(file, read(file), ts.ScriptTarget.Latest, true);
      function visit(node: ts.Node) {
        if (ts.isBinaryExpression(node)) {
          const op = node.operatorToken.kind;
          if (op === ts.SyntaxKind.GreaterThanToken || op === ts.SyntaxKind.GreaterThanEqualsToken) {
            const { line } = src.getLineAndCharacterOfPosition(node.getStart());
            findings.push({ severity: severityOf(manifest, "forbidGreaterThanComparison"), category: "forbidGreaterThanComparison", message: `${file}:${line + 1}: forbidden \`>\`/\`>=\`; use \`<\`/\`<=\`` });
          }
        }
        node.forEachChild(visit);
      }
      visit(src);
    }
    return findings;
  },
},
```

`forbidBlankLineInLeafFunction` spec:

- `ts.FunctionDeclaration` / `ts.MethodDeclaration` / `ts.ArrowFunction` / `ts.FunctionExpression` 노드 순회.
- leaf 판단: visit child nodes, return true on `isFunctionLike(child)`. 없으면 leaf.
- body 범위 내 raw text의 빈 줄 검사 (위 Python 패턴과 동일).

fixture 동일 패턴 (TS 문법으로 변환).

검증 명령:

```sh
cd /tmp/harness-target
bun run docs/harness/bun/harness-validate.ts
```

- [ ] Task 3.1 — bun runtime에 typescript 의존성 추가
- [ ] Task 3.2 — harness-check.ts에 helper + 2 spec 추가
- [ ] Task 3.3 — 위반/통과 fixture로 검증

### [x] Phase 4: Java AST (maven stack) — sub-agent prompt 자료 ↓ (Phase 400~417에 dispatch 완료 보고)

대상 파일: `plugins/harness/skills/harness-install/assets/maven/harness-maven-plugin/src/main/java/ai/harness/maven/HarnessCheck.java`

기존 구조:

- `enum HarnessCheck` with constructor `HarnessCheck(String category)` + abstract method `List<Finding> validate(Path root, JsonNode manifest)` + `boolean applies(JsonNode manifest)`.
- 각 enum value가 자기 검증 로직 구현.

추가할 dependency (`harness-maven-plugin/pom.xml`):

```xml
<dependency>
  <groupId>com.github.javaparser</groupId>
  <artifactId>javaparser-core</artifactId>
  <version>3.26.4</version>
</dependency>
```

(JavaParser는 standalone BOM이 없으므로 단일 dependency.)

추가 helper (HarnessCheck 또는 별도 utility class):

```java
static List<Path> stackSources(JsonNode manifest, String category) {
  JsonNode params = manifest.get(category).get("parameters");
  JsonNode rootsNode = params.get("sourceRootsPerStack").get("java");
  JsonNode extsNode = params.get("extensionsPerStack").get("java");
  Set<String> extensions = new HashSet<>();
  extsNode.forEach(e -> extensions.add(e.asText()));
  List<Path> files = new ArrayList<>();
  FileSystem fs = FileSystems.getDefault();
  for (JsonNode rootNode : rootsNode) {
    PathMatcher matcher = fs.getPathMatcher("glob:" + rootNode.asText() + "/**/*");
    Files.walk(Path.of(".")).filter(p -> matcher.matches(p) && extensions.contains(extensionOf(p))).forEach(files::add);
  }
  return files;
}
static String extensionOf(Path p) { String s = p.getFileName().toString(); int idx = s.lastIndexOf('.'); return idx < 0 ? "" : s.substring(idx + 1); }
```

추가 enum value 2개:

```java
FORBID_GREATER_THAN_COMPARISON("forbidGreaterThanComparison") {
  @Override List<Finding> validate(Path root, JsonNode manifest) {
    List<Finding> findings = new ArrayList<>();
    for (Path file : stackSources(manifest, category)) {
      CompilationUnit cu = StaticJavaParser.parse(file);
      cu.walk(BinaryExpr.class, expr -> {
        Operator op = expr.getOperator();
        if (op == Operator.GREATER || op == Operator.GREATER_EQUALS) {
          int line = expr.getBegin().map(p -> p.line).orElse(-1);
          findings.add(new Finding(severityOf(manifest, category), category, file + ":" + line + ": forbidden `>`/`>=`; use `<`/`<=`"));
        }
      });
    }
    return findings;
  }
},
```

`FORBID_BLANK_LINE_IN_LEAF_FUNCTION`: `MethodDeclaration` 순회, body에 nested method/lambda 없으면 leaf, body 범위 raw 줄 검사.

JavaParser API:

- `StaticJavaParser.parse(Path)` → `CompilationUnit`.
- `cu.walk(Class, action)` — type-filtered visitor.
- `node.getBegin().map(Position::getLine)` — 줄 번호.

fixture (Java):

```java
class Violation {
  int max(int a, int b) {
    if (a > b) return a;  // violation
    return b;
  }
}
```

- [ ] Task 4.1 — pom.xml에 javaparser-core dependency 추가
- [ ] Task 4.2 — HarnessCheck.java에 helper + 2 enum value 추가
- [ ] Task 4.3 — 위반/통과 fixture로 검증 (`mvn -q -f harness-maven-plugin/pom.xml install && mvn -q ai.harness:harness-maven-plugin:0.1.0:validate`)

### [x] Phase 5: Kotlin AST (gradle stack) — sub-agent prompt 자료 ↓ (Phase 400~417에 dispatch 완료 보고)

대상 파일:

- `plugins/harness/skills/harness-install/assets/gradle/buildSrc/src/main/kotlin/ai/harness/gradle/HarnessCheck.kt`
- `plugins/harness/skills/harness-install/assets/gradle/buildSrc/build.gradle.kts`
- `plugins/harness/skills/harness-install/assets/gradle/buildSrc/gradle/libs.versions.toml`

기존 구조: `enum class HarnessCheck { ... }` with abstract `applies(manifest)` + `validate(root, manifest): List<Finding>` overridden per value.

추가 dependency (Version Catalog `libs.versions.toml`):

```toml
[versions]
kotlin-compiler-embeddable = "2.1.0"

[libraries]
kotlin-compiler-embeddable = { module = "org.jetbrains.kotlin:kotlin-compiler-embeddable", version.ref = "kotlin-compiler-embeddable" }
```

build.gradle.kts:

```kotlin
dependencies {
    implementation(libs.kotlin.compiler.embeddable)
}
```

PSI API 사용 패턴:

```kotlin
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.com.intellij.openapi.vfs.local.CoreLocalFileSystem
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.lexer.KtTokens

private val disposable = Disposer.newDisposable()
private val env = KotlinCoreEnvironment.createForProduction(disposable, CompilerConfiguration(), EnvironmentConfigFiles.JVM_CONFIG_FILES)
private val psi = PsiManager.getInstance(env.project)
private val vfs = CoreLocalFileSystem.getInstance()
fun parseKt(path: kotlin.io.path.Path): KtFile? {
  val vFile = vfs.findFileByPath(path.toString()) ?: return null
  return psi.findFile(vFile) as? KtFile
}
```

추가 enum value:

- `FORBID_GREATER_THAN_COMPARISON`: PSI traversal, `KtBinaryExpression` 노드의 `operationToken` 검사. `KtTokens.GT` 또는 `KtTokens.GTEQ`이면 Finding.
- `FORBID_BLANK_LINE_IN_LEAF_FUNCTION`: `KtNamedFunction` 순회. body 안에 nested `KtNamedFunction` / `KtLambdaExpression` 없으면 leaf. body text 범위 raw 빈 줄 검사.

기존 정규식 검사 마이그레이션:

- `FORBID_IMPLICIT_LAMBDA_IT`: `KtLambdaExpression`의 `valueParameters.isEmpty()` 검사 (implicit `it` 사용 의미). + body 내 `KtSimpleNameExpression`의 텍스트가 `"it"`인 reference 검사로 확정.
- `REQUIRE_SINGLE_TOP_LEVEL_KOTLIN_DECLARATION`: `KtFile.declarations.size == 1` + declaration이 `KtClass`/`KtObjectDeclaration`/`KtNamedFunction(?)` 중 허용.

fixture (Kotlin):

```kotlin
class Violation {
    fun max(a: Int, b: Int): Int {
        if (a > b) return a  // violation
        return b
    }
}
```

- [ ] Task 5.1 — gradle/libs.versions.toml + buildSrc/build.gradle.kts에 kotlin-compiler-embeddable 추가
- [ ] Task 5.2 — HarnessCheck.kt에 PSI helper + FORBID_GREATER_THAN_COMPARISON + FORBID_BLANK_LINE_IN_LEAF_FUNCTION 추가
- [ ] Task 5.3 — 기존 FORBID_IMPLICIT_LAMBDA_IT를 PSI 기반으로 마이그레이션
- [ ] Task 5.4 — 기존 REQUIRE_SINGLE_TOP_LEVEL_KOTLIN_DECLARATION을 PSI 기반으로 마이그레이션
- [ ] Task 5.5 — 위반/통과 fixture로 검증 (`./gradlew harnessValidate`)

### [x] Phase 2: Python AST (uv) — sub-agent 완료

- [x] helper `_stack_sources` / `_parse_python` / `_has_nested_function` 추가
- [x] `_validate_forbid_greater_than_comparison` / `_validate_forbid_blank_line_in_leaf_function`
- [x] `FORBID_GREATER_THAN_COMPARISON` / `FORBID_BLANK_LINE_IN_LEAF_FUNCTION` enum 추가

### [x] Phase 3: TypeScript AST (bun) — sub-agent 완료

- [x] `import * as ts from "typescript@6.0.3";` inline import (PEP 723 style; package.json / bun.lock 불요)
- [x] `stackSources` / `hasNestedFunctions` 헬퍼 + 2 spec
- [x] CLAUDE.md 정책 위반인 package.json / bun.lock / .gitignore 산출물 제거

### [x] Phase 4: Java AST (maven) — sub-agent 완료

- [x] `javaparser-core:3.28.1` pom.xml dependency
- [x] `FORBID_GREATER_THAN_COMPARISON` / `FORBID_BLANK_LINE_IN_LEAF_FUNCTION` enum + helper

### [x] Phase 5: Kotlin AST (gradle) — sub-agent 완료, 후속 작업 있음

- [x] `kotlin-compiler-embeddable:2.3.0` libs.versions.toml + buildSrc/build.gradle.kts
- [x] `PsiKotlin` helper (별도 파일)
- [x] `FORBID_GREATER_THAN_COMPARISON` / `FORBID_BLANK_LINE_IN_LEAF_FUNCTION` 신규
- [x] `FORBID_IMPLICIT_LAMBDA_IT` / `REQUIRE_SINGLE_TOP_LEVEL_KOTLIN_DECLARATION` PSI 마이그레이션
- [-] *후속*: kotlin-compiler-embeddable과 Kotlin Gradle Plugin 간 internal API 충돌 회피를 위해 isolated classloader (URLClassLoader 또는 별도 Gradle subproject)로 격리. <https://kotlinlang.org/docs/whatsnew21.html#compiler-symbols-hidden-from-the-kotlin-gradle-plugin-api> 참조. 별도 plan 필요.

### [x] Phase 6: 환경 정리

- [x] `.gitignore`에 `.kotlin/` 추가
- [x] `.gitignore`의 `plugins/harness/skills/harness-install/templates/**/bin/` → `assets/**/bin/` 갱신 (templates → assets rename 후속)

### [x] Phase 7: AST → CST 전환 (병렬, 4 stack)

- [x] Task 7.1 — Python: `ast` → `libcst@>=1.8.6` (PEP 723 inline dep). `cst.EmptyLine` 노드로 blank line 검출
- [x] Task 7.2 — TypeScript: `typescript@6.0.3` full-fidelity (`setParentNodes=true`, getFullStart inter-statement trivia walk, operatorToken.getStart)
- [x] Task 7.3 — Java: JavaParser `LexicalPreservingPrinter.setup(cu)` + JavaToken consecutive newline walk
- [x] Task 7.4 — Kotlin: PsiWhiteSpace text의 newline count 기반 blank line 검출

### [x] Phase 8: Kotlin Worker API + nested class 재구성 (Phase 8b로 정식 패턴 회귀 후 완료)

Kotlin 2.3.21이 K1 PSI API를 hard compile error로 격상. 대응:

- `kotlin-compiler-embeddable` 2.3.21 의존성 유지 (직접 `implementation(libs.kotlin.compiler.embeddable)`)
- build.gradle.kts에 `-Xwarning-level=K1_API_DEPRECATION_ERROR:warning` 적용하여 compile-time deprecation을 warning으로 강등
- Gradle Worker API (`workerExecutor.classLoaderIsolation`)로 runtime classloader 격리하여 K1 PSI를 안전하게 호출
- `WorkAction`/`WorkParameters`를 별도 파일이 아닌 `HarnessValidationTask`의 **nested class**로 구성 (사용자 지침: "inner static class 구성은 가능합니다")
- WorkAction 결과는 임시 파일에 JSON으로 직렬화 (Worker는 task와 다른 classloader이므로 객체 직접 반환 불가)
- reflection 기반 `hasDescendant`는 `com.intellij.psi.PsiRecursiveElementWalkingVisitor`로 교체

- [x] Task 8.1 — `HarnessCheck.kt`에서 중복된 PSI 함수 제거, PSI import 제거 (Phase 10 정리에 흡수).
- [x] Task 8.2 — `HarnessValidationPlugin.kt`에 nested `HarnessPsiWorkParameters` + `HarnessPsiWorkAction` 정의 (Phase 8b에서 정식 격리 패턴으로 완성).
- [x] Task 8.3 — `HarnessPsiWorkAction.execute()` PSI 호출 + 임시 파일 JSON 직렬화.
- [x] Task 8.4 — reflection 기반 `hasDescendant` → `PsiRecursiveElementWalkingVisitor` (HarnessValidationPlugin.kt:513).
- [x] Task 8.5 — `plugin-self-check.sh` 통과 확인.

### [x] Phase 8b: Kotlin 2.1 whatsnew 정식 격리 패턴 회귀 (완료, 다수 commit으로 분산)

사용자가 https://kotlinlang.org/docs/whatsnew21.html 의 정확한 Worker API 격리 예제(`@Classpath` + 별도 `myDependencyScope`/`myResolvable` Configuration + `classLoaderIsolation { classpath.from(kotlinCompiler) }`)를 제시. 이전 sub-agent가 `implementation(libs.kotlin.compiler.embeddable)`로 우회했던 부분은 정식 패턴으로 회귀.

- [x] Task 8b.1 — `buildSrc/build.gradle.kts`의 `implementation` → `compileOnly(libs.kotlin.compiler.embeddable)` 전환 (commit c039ef8).
- [x] Task 8b.2 — `HarnessValidationPlugin.apply`에 두 Configuration (`harnessKotlinCompilerDeps` + `harnessKotlinCompilerResolvable`) 및 task에 `kotlinCompiler.from(resolvable)` 주입 (commit c039ef8).
- [x] Task 8b.3 — `HarnessValidationTask`에 `@get:Classpath abstract val kotlinCompiler` 추가 및 `classLoaderIsolation { classpath.from(kotlinCompiler) }` 사용 (commit c039ef8).
- [x] Task 8b.4 — `HarnessCheck.kt` 다중 top-level declaration 분리 — Phase 10 일괄 분리로 처리 (commit c9f9b94). 25 Rule object + Result는 각 Rule의 nested로.
- [x] Task 8b.5 — `HarnessPsiResults` 생성자 `emptyList()` default 제거 (Phase 10 commit c9f9b94).
- [x] Task 8b.6 — `java.io.File` → `kotlin.io.path.Path` + Kotlin 확장 함수 전면 전환 (commit c039ef8).
- [x] Task 8b.7 — reflection `hasDescendant` 제거 + `PsiRecursiveElementWalkingVisitor` 기반 `hasDescendantOfType<T>` extension 도입 (HarnessValidationPlugin.kt 라인 513).

### [x] Phase 8c: 9개 disabled add-on 일괄 red-green + 새 companion-position add-on (commit 200a48b)

감사 결과 (manifest 32 add-on vs stack 검증 코드 정합):

- Kotlin: 25 → 32 (7 신규: forbidEarlyReturn / forbidSilentCatch / forbidMutableCollection / forbidUnstructuredLogging / requireImportOverFqn / requireDocCommentOnPublicDeclaration / requireCompanionObjectPosition)
- Java: 30 → 30 (잔여는 의도된 single-stack non-Java)
- Python: 27 → 28 (requireCiCommandMatchesHook 추가). 잔여 4건은 의도된 N/A (manifest.python=[] 또는 Kotlin-only)
- TypeScript: 29 → 31 (forbidUnsafeSymlinks + requireImportOverFqn). 잔여 1건은 Kotlin-only requireCompanionObjectPosition

`HarnessCheck.Companion.` 명시 호출은 `HarnessCheck.` 단축형으로 정리.

manifest의 9개 disabled add-on 모두를 red-green으로 활성화하고, 새 type 추가:

- forbidEarlyReturn (ERROR, 4 stack)
- forbidSilentCatch (ERROR, 4 stack)
- forbidMutableCollection (ERROR, Kotlin/Java/TS — Python 제외)
- forbidUnstructuredLogging (ERROR, 4 stack)
- forbidWildcardImport (ERROR, 4 stack)
- requireImportOverFqn (WARN, 4 stack, name-clash 예외)
- requireDocCommentOnPublicDeclaration (WARN, 4 stack)
- forbidEmptyCatchBlock (ERROR, 4 stack)
- requireBracesOnIf (ERROR, Kotlin/Java/TS — Python 제외)
- **NEW** requireCompanionObjectPosition (ERROR, Kotlin only, `parameters.position`: "top" | "bottom", 기본 "top")

진행 절차:

- [x] Task 8c.1 — `manifest.json` 일괄 편집: 9개 add-on `enabled: true` + 새 `requireCompanionObjectPosition` 추가 (commit c039ef8).
- [x] Task 8c.2 — 4 stack 검증 코드 구현. 단일 add-on 단위로 commit (e911abc forbidWildcardImport / 7826db5 forbidEmptyCatchBlock) → 이후 누락 감사로 일괄 보강 (200a48b: Kotlin 7 + TS 2 + Python 1).
- [x] Task 8c.3 — 최종 검증: self-check exit 0. 정합성 감사로 4 stack 모두 manifest 적용 의도 100% 커버 확인.

### [-] Phase 9: 검증

- [x] Task 9.1 — plugin-self-check.sh + shellcheck PASS (commit 65d6620 시점 둘 다 exit 0).
- [ ] Task 9.2 — gradle / mvn / uv / bun native validator 실행 (사용자 환경에서만 가능, agent 처리 범위 외).

### [x] Phase 7 (post-hoc): 9개 disabled add-on 활성화 (Phase 8c로 흡수)

- [x] Task 7.1 — manifest 9개 add-on enabled + 각 stack 구현 추가는 Phase 8c (commit 200a48b)로 일괄 완료.

### [x] Phase 10: HarnessCheck enum → Rule strategy 분리 (4 stack, 완료)

enum constant 안에 validate 본문이 인라인되어 있어 1000 라인 단일 파일이 됨. 구조 분리:

- enum은 `(category, ruleInstance)` 쌍과 위임 메서드만 보유.
- 새 `HarnessCheckRule` interface/ABC를 도입해 `applies` + `validate` 시그니처 통일.
- 각 enum constant에 대응하는 `*Rule` class를 개별 파일로 분리.
- 기존 외부 `*Result` DTO는 해당 Rule class의 nested class(static inner / namespace / dataclass nested / TS namespace)로 이동.

진행 절차:

- [x] Task 10.1 — Python (uv): 28개 Rule class + `rules/` 디렉터리 + `harness_check_rule.py` ABC. 완료.
- [x] Task 10.2 — Kotlin (gradle/buildSrc): 25 enum 분리 + 7개 사용 중 Result nested 이동 완료 (HarnessCheck.kt 934 → 142줄). orphan Result 7개와 그 visitor는 다음 add-on 활성화 단계에서 신규 구조로 통합.
- [x] Task 10.3 — Java (maven): enum 분리 완료 (31 Rule class, HarnessCheck.java 81줄). nested Result 이동은 별도 분기에서 처리 (현 enum이 사용하는 외부 Result 없음).
- [x] Task 10.4 — TypeScript (bun): Rule class 분리 + namespace nested Result. 완료 (30 Rule class, harness-check.ts 356줄).
- [x] Task 10.5 — 4 stack 모두 `plugin-self-check.sh` PASS 확인 후 일괄 commit (c9f9b94).

### [x] Phase 11: HarnessCheckRule.validate 반환형 List → Collection 일반화 (commit 979a71c)

`validate`는 호출부에서 합치기/순회만 하면 충분하므로 반환형을 더 일반적인 `Collection`(JVM) / `Iterable`(Python) / `readonly Finding[]`(TS)로 확대.

- [x] Task 11.1 — JVM 두 stack: `Collection<Finding>`으로 시그니처 변경.
- [x] Task 11.2 — Python: `Iterable[Finding]`로 변경.
- [x] Task 11.3 — TypeScript: `readonly Finding[]`로 변경.

### [x] Phase 12: 중간 `return emptyList()` 제거 (commit 5fc0d59)

각 Rule 본문 안의 early-return guard (예: `if (...) return emptyList()`, `return Collections.emptyList()`, `return []`)를 모두 제거하고 single-exit + buildList / Stream / list comprehension 패턴으로 통합. `forbidEarlyReturn` add-on과 자연스럽게 정합.

- [ ] Task 12.1 — Kotlin: `buildList { ... }` 또는 `flatMap` 체이닝.
- [ ] Task 12.2 — Java: `Stream.of(...).filter(...).collect(...)` 등.
- [ ] Task 12.3 — Python: list comprehension / generator 변환.
- [ ] Task 12.4 — TypeScript: `Array.from(...)` / `flatMap`.

전제: Phase 10 완료 후 진행 (가능하면 Phase 11과 동시).

### [x] Phase 12b: `*Rule`을 class → object/singleton로 전환 (commit 1eb1b8f)

각 Rule 구현은 상태가 없으므로 인스턴스가 1개면 충분. 매번 `new XxxRule()` 하는 dispatch 등록을 singleton 참조로 단순화한다.

- [ ] Task 12b.1 — Kotlin: `class XxxRule : HarnessCheckRule` → `object XxxRule : HarnessCheckRule`. `HarnessCheck` enum entry는 `XxxRule` 그대로 참조.
- [ ] Task 12b.2 — Java: `class XxxRule implements HarnessCheckRule`를 enum singleton (`enum XxxRule implements HarnessCheckRule { INSTANCE; ... }`) 또는 private 생성자 + `public static final XxxRule INSTANCE`로 전환. `HarnessCheck` enum entry에는 `XxxRule.INSTANCE` 참조.
- [ ] Task 12b.3 — Python: 모듈 레벨 singleton (`RULE = ForbidGreaterThanComparisonRule()`)을 export 하거나, 메서드를 `@classmethod`로 노출해 인스턴스 없이 호출.
- [ ] Task 12b.4 — TypeScript: `class XxxRule` 대신 `export const xxxRule: HarnessCheckRule = { applies(...) {...}, validate(...) {...} }` object literal로. dispatch table은 instance 생성 없이 singleton 참조.

전제: Phase 10 nested Result 이동 commit 후 진행.

### [x] Phase 12c: 컬렉션 빌더 패턴 → filter/map 체이닝 단순화 (commit 5ffa96b)

`mapNotNull`, `buildSet { forEach { ... ; if (cond) add(x) } }.toList()`, `buildList { items.forEach { if (cond) add(transform(it)) } }` 같은 명령형 빌더 패턴은 대부분 `.filter { ... }.map { ... }` 체이닝으로 직접 전환 가능. 선언적 표현으로 단순화.

- [ ] Task 12c.1 — Kotlin: `buildSet { forEach { add } }.toList()` → `.filter { ... }.map { ... }` (또는 `.flatMap`). `mapNotNull { if (cond) null else x }` → `.filter { cond }.map { x }`.
- [ ] Task 12c.2 — Java: `for` + `if` + `findings.add(...)` 패턴 → `stream().filter(...).map(...).collect(toList())`.
- [ ] Task 12c.3 — Python: `findings = []; for x in items: if cond: findings.append(transform(x))` → list comprehension `[transform(x) for x in items if cond]`.
- [ ] Task 12c.4 — TypeScript: 명령형 push → `items.filter(...).map(...)` 또는 `.flatMap(...)`.

전제: Phase 12b(singleton 전환) commit 후 진행. 변환 시 동작 보존, 부작용(IO, 예외)이 있는 분기는 그대로 명령형 유지.

### [x] Phase 12d: 중간 return 회피를 위한 조건 반전 패턴 정형화 (commit 368dbcd)

Phase 12에서 처리한 early-return 제거의 후속 정리. guard `if (!cond) return ...; doX(); doY();` 구조를 nesting 깊이를 늘리지 않으면서 single-exit으로 유지하기 위해 조건을 반전(`if (cond) { doX(); doY(); }`)하는 패턴을 명시적으로 적용한다.

원칙:

- 조건의 negation을 적용해 본문이 if 블록 안으로 한 단계만 들어가는 형태를 기본 채택.
- 조건식이 길거나 복합적이면 의도가 드러나는 이름의 boolean 지역 변수로 추출한 뒤 반전.
- 중첩이 2단계 이상으로 늘어나거나 else 분기가 자연스러우면 함수 분리(extract function)로 평탄화.
- 부작용(IO, 예외, 로그)이 있는 분기는 그대로 명령형 if/else 유지 — 무리한 반전 금지.

- [ ] Task 12d.1 — Kotlin: `?: return emptyList()` 제거 후 남은 명령형 분기에서 negation 적용. 복합 조건은 `val ok = ... ; if (ok) { ... }`.
- [ ] Task 12d.2 — Java: `if (x == null) return ...;` → `if (x != null) { ... }`. Stream chain으로 옮길 수 있는 곳은 Phase 12c와 합쳐 처리.
- [ ] Task 12d.3 — Python: 동일 패턴. `if not isinstance(x, dict): return []` → `if isinstance(x, dict): ...`. Walrus(`:=`)는 가독성이 명확한 곳에 한해.
- [ ] Task 12d.4 — TypeScript: 동일 패턴. type narrowing이 깨지지 않게 union 처리 주의.

전제: Phase 12c(filter/map 단순화) 완료 후 진행. 12c에서 선언형으로 바뀐 코드는 12d 대상이 아님.

### [x] Phase 12e: 단일 사용 지역 변수 inline 처리 (commit 3cb5534)

한 번만 사용되는 지역 변수는 **가능한 모든 위치에서** 사용처에 inline. 보수적 기준 없음, 적극적으로 정리. 안전 제약(아래)만 지키고 나머지는 inline 후 self-check 통과를 기준으로 한다.

안전 제약 (이 경우만 유지):

- 부작용을 가진 표현식 (IO, 예외 throw 가능, 외부 상태 변경)을 다중 inline 하면 호출 순서나 횟수가 바뀌어 동작이 달라지는 경우.
- type widening / explicit type assertion이 의미를 가지는 경우 (제거 시 컴파일러가 좁은 타입으로 추론해 호출이 깨지는 케이스).
- 디버거 watch point / log 명령에 변수 이름이 직접 등장하는 경우.

위 제약에 해당하지 않으면 *전부* inline.

- [ ] Task 12e.1 — Kotlin: `val x = computeY(); return x` → `return computeY()`. `val tmp = a.b.c; tmp.doX()` → `a.b.c.doX()`. IDE Inline 적극 활용.
- [ ] Task 12e.2 — Java: `var` / `final` 단일-사용 지역 변수 모두 inline. 람다 안의 임시 변수도 포함.
- [ ] Task 12e.3 — Python: 동일. type annotation이 의미를 가지면 cast/assert로 옮기고 inline.
- [ ] Task 12e.4 — TypeScript: 동일. type inference로 안전한 모든 곳 inline. type 좁힘이 필요한 곳은 `as`로 옮기고 inline.

전제: Phase 12d(조건 반전) 완료 후 진행. 12d에서 도입된 임시 boolean이 단일 사용이면 그 자리에서 inline.

### [x] Phase 12f: mutable 변수(var/let) → 불변(val/const) 전환 (commit 9226f1f)

외부에 노출된 가변 변수를 모두 불변으로. `buildList { }` / `buildSet { }` 같은 *수신자 안의* mutable 컨텍스트는 범위 내에 갇혀 있으므로 허용. 다음 케이스가 제거 대상:

- Kotlin: 함수 본문/클래스 멤버의 `var`.
- Java: `final` 키워드가 없는 지역 변수, 가변 field.
- TypeScript: `let` (인덱스 카운터 포함).
- Python: 누적용 `x = []; for ... x.append(...)` 패턴.

대체 수단:

- 누적은 `fold` / `reduce` / `runningFold` / `flatMap` / list comprehension / `Stream.collect`.
- 인덱스가 필요하면 `withIndex()` / `enumerate()` / `Array.map((v, i) => ...)`.
- 카운터는 `count {}` / `filter { ... }.size` / `len([... for ...])`.
- 단순 재할당은 `if`-식 또는 `when`/`switch`-식으로 단일 `val`.
- 위 어느 것도 깔끔하지 않은 누적은 `buildList` / `buildSet`로 격리.
- 재귀가 더 명료하면 재귀 사용 (Kotlin `tailrec` 권장).

- [ ] Task 12f.1 — Kotlin: `var` 제거. tail-recursive 가능한 경우 `tailrec` 함수로 추출.
- [ ] Task 12f.2 — Java: 지역 변수에 `final` 부착 시도, 실패하는 변수는 fold/Stream으로 리팩터.
- [ ] Task 12f.3 — Python: append 누적을 comprehension/`itertools.chain`/`functools.reduce`로 전환.
- [ ] Task 12f.4 — TypeScript: `let` → `const`. 누적은 `.reduce()` / spread / `flatMap`.

전제: Phase 12e(inline) 완료 후 진행. inline으로 임시 변수가 줄어든 상태에서 남은 var/let만 변환.

### [x] Phase 13: Rule class 하위 패키지(rules/) 정리 (commit 448d323)

Rule class를 모아 관리할 전용 하위 네임스페이스를 둠. Python/TS는 Phase 10에서 이미 `rules/` 디렉터리로 분리됨. Kotlin/Java는 단일 패키지에 둔 상태이므로 추가 이동 필요.

- [x] Task 13.1 — Python: `runtime/rules/`. Phase 10에서 완료.
- [x] Task 13.2 — TypeScript: `runtime/rules/`. Phase 10에서 완료.
- [ ] Task 13.3 — Kotlin: `ai.harness.gradle.rules` 하위 패키지로 이동.
- [ ] Task 13.4 — Java: `ai.harness.maven.rules` 하위 패키지로 이동.
- [ ] Task 13.5 — 각 import 경로/manifest validator 참조 갱신 + self-check.

전제: Phase 10 완료 후 진행.

### [ ] Phase 8: Plan completion

- [ ] Task 8.1 — 본 plan을 `docs/exec-plans/completed/`로 이동

## Validation

각 phase 종료 시 `plugin-self-check.sh` PASS + 해당 stack validator 실행 정상.

## Rollback Criteria

AST 의존성 도입 후 정확성 문제(false positive / false negative)가 발견되면 해당 stack 변경을 `git revert` 후 fixture를 추가하여 재시도.
