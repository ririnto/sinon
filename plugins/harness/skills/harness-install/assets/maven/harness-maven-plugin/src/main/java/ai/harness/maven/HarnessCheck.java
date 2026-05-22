package ai.harness.maven;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.nio.file.Path;
import java.util.Collection;

/**
 * Enum of harness validation checks. Each value holds a category name and a rule implementation.
 */
enum HarnessCheck {
    REQUIRE_FILES_EXIST("requireFilesExist", new RequireFilesExistRule()),
    REQUIRE_DIRECTORIES_EXIST("requireDirectoriesExist", new RequireDirectoriesExistRule()),
    REQUIRE_KEEPFILE_IN_EMPTY_DIRECTORIES("requireKeepfileInEmptyDirectories", new RequireKeepfileInEmptyDirectoriesRule()),
    REQUIRE_TEMPLATE_GROUPS("requireTemplateGroups", new RequireTemplateGroupsRule()),
    REQUIRE_DOC_HEADINGS("requireDocHeadings", new RequireDocHeadingsRule()),
    REQUIRE_DOC_CONTENT("requireDocContent", new RequireDocContentRule()),
    REQUIRE_AGENT_FRONTMATTER("requireAgentFrontmatter", new RequireAgentFrontmatterRule()),
    REQUIRE_SKILL_FRONTMATTER("requireSkillFrontmatter", new RequireSkillFrontmatterRule()),
    FORBID_SCAFFOLD_LEAKS("forbidScaffoldLeaks", new ForbidScaffoldLeaksRule()),
    REQUIRE_HOOK_SHEBANG("requireHookShebang", new RequireHookShebangRule()),
    REQUIRE_HOOK_EXECUTABLE("requireHookExecutable", new RequireHookExecutableRule()),
    REQUIRE_HOOK_GENERATED_MARKER("requireHookGeneratedMarker", new RequireHookGeneratedMarkerRule()),
    REQUIRE_HOOK_STAGE("requireHookStage", new RequireHookStageRule()),
    REQUIRE_HOOK_COMMAND("requireHookCommand", new RequireHookCommandRule()),
    REQUIRE_CI_COMMAND_MATCHES_HOOK("requireCiCommandMatchesHook", new RequireCiCommandMatchesHookRule()),
    REQUIRE_ENV_SHEBANG_UNDER("requireEnvShebangUnder", new RequireEnvShebangUnderRule()),
    FORBID_UNCHECKED_TASKS_UNDER("forbidUncheckedTasksUnder", new ForbidUncheckedTasksUnderRule()),
    FORBID_UNSAFE_SYMLINKS("forbidUnsafeSymlinks", new ForbidUnsafeSymlinksRule()),
    REQUIRE_SINGLE_TOP_LEVEL_KOTLIN_DECLARATION("requireSingleTopLevelKotlinDeclaration", new RequireSingleTopLevelKotlinDeclarationRule()),
    FORBID_GREATER_THAN_COMPARISON("forbidGreaterThanComparison", new ForbidGreaterThanComparisonRule()),
    FORBID_BLANK_LINE_IN_LEAF_FUNCTION("forbidBlankLineInLeafFunction", new ForbidBlankLineInLeafFunctionRule()),
    FORBID_EARLY_RETURN("forbidEarlyReturn", new ForbidEarlyReturnRule()),
    FORBID_SILENT_CATCH("forbidSilentCatch", new ForbidSilentCatchRule()),
    FORBID_MUTABLE_COLLECTION("forbidMutableCollection", new ForbidMutableCollectionRule()),
    FORBID_UNSTRUCTURED_LOGGING("forbidUnstructuredLogging", new ForbidUnstructuredLoggingRule()),
    FORBID_WILDCARD_IMPORT("forbidWildcardImport", new ForbidWildcardImportRule()),
    REQUIRE_IMPORT_OVER_FQN("requireImportOverFqn", new RequireImportOverFqnRule()),
    REQUIRE_DOC_COMMENT_ON_PUBLIC_DECLARATION("requireDocCommentOnPublicDeclaration", new RequireDocCommentOnPublicDeclarationRule()),
    FORBID_EMPTY_CATCH_BLOCK("forbidEmptyCatchBlock", new ForbidEmptyCatchBlockRule()),
    REQUIRE_BRACES_ON_IF("requireBracesOnIf", new RequireBracesOnIfRule());

    private final String category;
    private final HarnessCheckRule rule;

    HarnessCheck(String category, HarnessCheckRule rule) {
        this.category = category;
        this.rule = rule;
    }

    /**
     * Gets the category name for this check.
     *
     * @return the category name
     */
    public String category() {
        return category;
    }

    /**
     * Determines whether this check applies to the given manifest.
     *
     * @param manifest the manifest JSON node
     * @return true if this check should be validated
     */
    public boolean applies(JsonNode manifest) {
        return rule.applies(manifest);
    }

    /**
     * Validates the given project directory against this check.
     *
     * @param root the project root directory
     * @param manifest the manifest JSON node
     * @return a collection of findings; empty if validation passes
     * @throws MojoExecutionException if validation fails
     */
    public Collection<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
        return rule.validate(root, manifest);
    }
}
