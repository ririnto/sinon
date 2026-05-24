package ai.harness.maven;

import ai.harness.maven.rules.HarnessCheckRule;
import ai.harness.maven.rules.FilePresenceRule;
import ai.harness.maven.rules.DirectoryPresenceRule;
import ai.harness.maven.rules.EmptyDirectoryPlaceholdersRule;
import ai.harness.maven.rules.TemplateGroupsRule;
import ai.harness.maven.rules.DocHeadingsRule;
import ai.harness.maven.rules.DocContentRule;
import ai.harness.maven.rules.AgentFrontmatterRule;
import ai.harness.maven.rules.SkillFrontmatterRule;
import ai.harness.maven.rules.ScaffoldLeaksRule;
import ai.harness.maven.rules.HookShebangRule;
import ai.harness.maven.rules.HookExecutableRule;
import ai.harness.maven.rules.HookGeneratedMarkerRule;
import ai.harness.maven.rules.HookStageRule;
import ai.harness.maven.rules.HookCommandRule;
import ai.harness.maven.rules.CiHookCommandParityRule;
import ai.harness.maven.rules.EnvShebangUsageRule;
import ai.harness.maven.rules.UncheckedTasksRule;
import ai.harness.maven.rules.SymlinkSafetyRule;
import ai.harness.maven.rules.KotlinTopLevelDeclarationCountRule;
import ai.harness.maven.rules.GreaterThanComparisonRule;
import ai.harness.maven.rules.LeafFunctionBlankLinesRule;
import ai.harness.maven.rules.EarlyReturnRule;
import ai.harness.maven.rules.SilentCatchRule;
import ai.harness.maven.rules.MutableCollectionRule;
import ai.harness.maven.rules.UnstructuredLoggingRule;
import ai.harness.maven.rules.WildcardImportRule;
import ai.harness.maven.rules.ImportOverFqnRule;
import ai.harness.maven.rules.PublicDeclarationDocCommentRule;
import ai.harness.maven.rules.EmptyCatchBlockRule;
import ai.harness.maven.rules.IfStatementBracesRule;
import ai.harness.maven.rules.ClassMemberOrderingRule;
import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.nio.file.Path;
import java.util.Collection;

/**
 * Enum of harness validation checks. Each value holds a rule implementation.
 */
enum HarnessCheck {
    FILE_PRESENCE(FilePresenceRule.INSTANCE),
    DIRECTORY_PRESENCE(DirectoryPresenceRule.INSTANCE),
    EMPTY_DIRECTORY_PLACEHOLDERS(EmptyDirectoryPlaceholdersRule.INSTANCE),
    TEMPLATE_GROUPS(TemplateGroupsRule.INSTANCE),
    DOC_HEADINGS(DocHeadingsRule.INSTANCE),
    DOC_CONTENT(DocContentRule.INSTANCE),
    AGENT_FRONTMATTER(AgentFrontmatterRule.INSTANCE),
    SKILL_FRONTMATTER(SkillFrontmatterRule.INSTANCE),
    SCAFFOLD_LEAKS(ScaffoldLeaksRule.INSTANCE),
    HOOK_SHEBANG(HookShebangRule.INSTANCE),
    HOOK_EXECUTABLE(HookExecutableRule.INSTANCE),
    HOOK_GENERATED_MARKER(HookGeneratedMarkerRule.INSTANCE),
    HOOK_STAGE(HookStageRule.INSTANCE),
    HOOK_COMMAND(HookCommandRule.INSTANCE),
    CI_HOOK_COMMAND_PARITY(CiHookCommandParityRule.INSTANCE),
    ENV_SHEBANG_USAGE(EnvShebangUsageRule.INSTANCE),
    UNCHECKED_TASKS(UncheckedTasksRule.INSTANCE),
    SYMLINK_SAFETY(SymlinkSafetyRule.INSTANCE),
    KOTLIN_TOP_LEVEL_DECLARATION_COUNT(KotlinTopLevelDeclarationCountRule.INSTANCE),
    GREATER_THAN_COMPARISON(GreaterThanComparisonRule.INSTANCE),
    LEAF_FUNCTION_BLANK_LINES(LeafFunctionBlankLinesRule.INSTANCE),
    EARLY_RETURN(EarlyReturnRule.INSTANCE),
    SILENT_CATCH(SilentCatchRule.INSTANCE),
    MUTABLE_COLLECTION(MutableCollectionRule.INSTANCE),
    UNSTRUCTURED_LOGGING(UnstructuredLoggingRule.INSTANCE),
    WILDCARD_IMPORT(WildcardImportRule.INSTANCE),
    IMPORT_OVER_FQN(ImportOverFqnRule.INSTANCE),
    PUBLIC_DECLARATION_DOC_COMMENT(PublicDeclarationDocCommentRule.INSTANCE),
    EMPTY_CATCH_BLOCK(EmptyCatchBlockRule.INSTANCE),
    IF_STATEMENT_BRACES(IfStatementBracesRule.INSTANCE),
    CLASS_MEMBER_ORDERING(ClassMemberOrderingRule.INSTANCE);
    private final HarnessCheckRule rule;

    HarnessCheck(HarnessCheckRule rule) {
        this.rule = rule;
    }

    /**
     * Gets the category name for this check.
     *
     * @return the category name
     */
    public String category() {
        return rule.category();
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
