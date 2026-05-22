package ai.harness.maven;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.nio.file.Path;
import java.util.Collection;

/**
 * Enum of harness validation checks. Each value holds a category name and a rule implementation.
 */
enum HarnessCheck {
    REQUIRE_FILES_EXIST("requireFilesExist", RequireFilesExistRule.INSTANCE),
    REQUIRE_DIRECTORIES_EXIST("requireDirectoriesExist", RequireDirectoriesExistRule.INSTANCE),
    REQUIRE_KEEPFILE_IN_EMPTY_DIRECTORIES("requireKeepfileInEmptyDirectories", RequireKeepfileInEmptyDirectoriesRule.INSTANCE),
    REQUIRE_TEMPLATE_GROUPS("requireTemplateGroups", RequireTemplateGroupsRule.INSTANCE),
    REQUIRE_DOC_HEADINGS("requireDocHeadings", RequireDocHeadingsRule.INSTANCE),
    REQUIRE_DOC_CONTENT("requireDocContent", RequireDocContentRule.INSTANCE),
    REQUIRE_AGENT_FRONTMATTER("requireAgentFrontmatter", RequireAgentFrontmatterRule.INSTANCE),
    REQUIRE_SKILL_FRONTMATTER("requireSkillFrontmatter", RequireSkillFrontmatterRule.INSTANCE),
    FORBID_SCAFFOLD_LEAKS("forbidScaffoldLeaks", ForbidScaffoldLeaksRule.INSTANCE),
    REQUIRE_HOOK_SHEBANG("requireHookShebang", RequireHookShebangRule.INSTANCE),
    REQUIRE_HOOK_EXECUTABLE("requireHookExecutable", RequireHookExecutableRule.INSTANCE),
    REQUIRE_HOOK_GENERATED_MARKER("requireHookGeneratedMarker", RequireHookGeneratedMarkerRule.INSTANCE),
    REQUIRE_HOOK_STAGE("requireHookStage", RequireHookStageRule.INSTANCE),
    REQUIRE_HOOK_COMMAND("requireHookCommand", RequireHookCommandRule.INSTANCE),
    REQUIRE_CI_COMMAND_MATCHES_HOOK("requireCiCommandMatchesHook", RequireCiCommandMatchesHookRule.INSTANCE),
    REQUIRE_ENV_SHEBANG_UNDER("requireEnvShebangUnder", RequireEnvShebangUnderRule.INSTANCE),
    FORBID_UNCHECKED_TASKS_UNDER("forbidUncheckedTasksUnder", ForbidUncheckedTasksUnderRule.INSTANCE),
    FORBID_UNSAFE_SYMLINKS("forbidUnsafeSymlinks", ForbidUnsafeSymlinksRule.INSTANCE),
    REQUIRE_SINGLE_TOP_LEVEL_KOTLIN_DECLARATION("requireSingleTopLevelKotlinDeclaration", RequireSingleTopLevelKotlinDeclarationRule.INSTANCE),
    FORBID_GREATER_THAN_COMPARISON("forbidGreaterThanComparison", ForbidGreaterThanComparisonRule.INSTANCE),
    FORBID_BLANK_LINE_IN_LEAF_FUNCTION("forbidBlankLineInLeafFunction", ForbidBlankLineInLeafFunctionRule.INSTANCE),
    FORBID_EARLY_RETURN("forbidEarlyReturn", ForbidEarlyReturnRule.INSTANCE),
    FORBID_SILENT_CATCH("forbidSilentCatch", ForbidSilentCatchRule.INSTANCE),
    FORBID_MUTABLE_COLLECTION("forbidMutableCollection", ForbidMutableCollectionRule.INSTANCE),
    FORBID_UNSTRUCTURED_LOGGING("forbidUnstructuredLogging", ForbidUnstructuredLoggingRule.INSTANCE),
    FORBID_WILDCARD_IMPORT("forbidWildcardImport", ForbidWildcardImportRule.INSTANCE),
    REQUIRE_IMPORT_OVER_FQN("requireImportOverFqn", RequireImportOverFqnRule.INSTANCE),
    REQUIRE_DOC_COMMENT_ON_PUBLIC_DECLARATION("requireDocCommentOnPublicDeclaration", RequireDocCommentOnPublicDeclarationRule.INSTANCE),
    FORBID_EMPTY_CATCH_BLOCK("forbidEmptyCatchBlock", ForbidEmptyCatchBlockRule.INSTANCE),
    REQUIRE_BRACES_ON_IF("requireBracesOnIf", RequireBracesOnIfRule.INSTANCE);

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
