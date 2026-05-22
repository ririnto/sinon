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

### [ ] Phase 2: Python AST (uv stack) — sub-agent prompt 자료 ↓

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

### [ ] Phase 3: TypeScript AST (bun stack) — sub-agent prompt 자료 ↓

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

### [ ] Phase 4: Java AST (maven stack) — sub-agent prompt 자료 ↓

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

### [ ] Phase 5: Kotlin AST (gradle stack) — sub-agent prompt 자료 ↓

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

### [-] Phase 8: Kotlin Worker API + nested class 재구성

Kotlin 2.3.21이 K1 PSI API를 hard compile error로 격상. 대응:

- `kotlin-compiler-embeddable` 2.3.21 의존성 유지 (직접 `implementation(libs.kotlin.compiler.embeddable)`)
- build.gradle.kts에 `-Xwarning-level=K1_API_DEPRECATION_ERROR:warning` 적용하여 compile-time deprecation을 warning으로 강등
- Gradle Worker API (`workerExecutor.classLoaderIsolation`)로 runtime classloader 격리하여 K1 PSI를 안전하게 호출
- `WorkAction`/`WorkParameters`를 별도 파일이 아닌 `HarnessValidationTask`의 **nested class**로 구성 (사용자 지침: "inner static class 구성은 가능합니다")
- WorkAction 결과는 임시 파일에 JSON으로 직렬화 (Worker는 task와 다른 classloader이므로 객체 직접 반환 불가)
- reflection 기반 `hasDescendant`는 `com.intellij.psi.PsiRecursiveElementWalkingVisitor`로 교체

- [-] Task 8.1 — `HarnessCheck.kt`에서 중복된 PSI 함수 (enum companion + file-level `HarnessCheckCompanion`) 모두 제거, PSI import 제거. 결과 DTO와 enum validate helper만 유지.
- [-] Task 8.2 — `HarnessValidationPlugin.kt`에 `WorkerExecutor` 주입 + `HarnessPsiWorkParameters` (nested interface) + `HarnessPsiWorkAction` (nested abstract class) 정의.
- [-] Task 8.3 — `HarnessPsiWorkAction.execute()`에서 PSI 호출, 결과를 `RegularFileProperty`로 받은 임시 파일에 JSON 직렬화.
- [-] Task 8.4 — reflection 기반 `hasDescendant`를 `PsiRecursiveElementWalkingVisitor`로 교체.
- [-] Task 8.5 — `plugin-self-check.sh` 통과 확인.

### [ ] Phase 8b: Kotlin 2.1 whatsnew 정식 격리 패턴 회귀

사용자가 https://kotlinlang.org/docs/whatsnew21.html 의 정확한 Worker API 격리 예제(`@Classpath` + 별도 `myDependencyScope`/`myResolvable` Configuration + `classLoaderIsolation { classpath.from(kotlinCompiler) }`)를 제시. 이전 sub-agent가 `implementation(libs.kotlin.compiler.embeddable)`로 우회했으므로 정식 패턴으로 회귀 + 누락 항목 일괄 처리.

- [-] Task 8b.1 — `buildSrc/build.gradle.kts`의 `implementation` → `compileOnly(libs.kotlin.compiler.embeddable)` 전환 (KGP API hidden 우회).
- [-] Task 8b.2 — `HarnessValidationPlugin.apply`에 두 Configuration 생성 (`harnessKotlinCompilerDeps` + `harnessKotlinCompilerResolvable`) 및 task 등록 시 `kotlinCompiler.from(resolvable)` 주입.
- [-] Task 8b.3 — `HarnessValidationTask`에 `@get:Classpath abstract val kotlinCompiler: ConfigurableFileCollection` 추가 및 `classLoaderIsolation { classpath.from(kotlinCompiler) }` 사용.
- [-] Task 8b.4 — `HarnessCheck.kt` 다중 top-level declaration 분리 — 5개 DTO를 각 별도 파일로 (`requireSingleTopLevelKotlinDeclaration` add-on 자기검증 RED→GREEN).
- [-] Task 8b.5 — `HarnessPsiResults` 등 생성자 `emptyList()` default 제거. 사용처에서 명시적 채우기.
- [-] Task 8b.6 — `java.io.File` → `kotlin.io.path.Path` + Kotlin 확장 함수 전면 전환 (Gradle boundary 제외).
- [-] Task 8b.7 — reflection `hasDescendant` 완전 제거 + `PsiRecursiveElementWalkingVisitor` 기반 `hasDescendantOfType` extension 도입.

### [ ] Phase 8c: 9개 disabled add-on 일괄 red-green + 새 companion-position add-on

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

- [ ] Task 8c.1 — `manifest.json` 일괄 편집 (main agent): 9개 add-on `enabled: true`로 전환 + 새 `requireCompanionObjectPosition` 추가.
- [ ] Task 8c.2 — 4 stack 병렬 dispatch (각 sub-agent가 자기 stack의 모든 add-on validator 구현 + 위반 수정):
    - K (Kotlin): `HarnessCheck.kt`에 enum constant 10개 추가 + PSI 기반 검증 로직 + buildSrc 위반 수정.
    - J (Java): `HarnessCheck.java`에 enum constant 8개 추가 + JavaParser 검증 + harness-maven-plugin 위반 수정.
    - P (Python): `harness_check.py`에 7개 검증 추가 (libcst) + uv 위반 수정.
    - T (TypeScript): `harness-check.ts`에 9개 검증 추가 (typescript compiler api) + bun 위반 수정.
- [ ] Task 8c.3 — 최종 검증: 각 stack validator 실행 PASS 확인.

### [ ] Phase 9: 검증

- [ ] Task 9.1 — plugin-self-check.sh + shellcheck PASS
- [ ] Task 9.2 — gradle / mvn / uv / bun native validator 실행 (사용자 환경)

### [ ] Phase 7: 9개 disabled add-on 활성화 (별도 후속 작업, 본 plan 범위 외)

- [ ] Task 7.1 — manifest의 forbidEarlyReturn 등 9개 add-on enabled로 전환 + 각 stack에 구현 추가. *별도 plan으로 분리*.

### [ ] Phase 8: Plan completion

- [ ] Task 8.1 — 본 plan을 `docs/exec-plans/completed/`로 이동

## Validation

각 phase 종료 시 `plugin-self-check.sh` PASS + 해당 stack validator 실행 정상.

## Rollback Criteria

AST 의존성 도입 후 정확성 문제(false positive / false negative)가 발견되면 해당 stack 변경을 `git revert` 후 fixture를 추가하여 재시도.
