package com.ririnto.sinon.harness;

import com.ririnto.sinon.harness.core.RuleContext;
import com.ririnto.sinon.harness.rules.HarnessCheckRule;
import com.ririnto.sinon.harness.rules.fs.FilePresenceRule;
import com.ririnto.sinon.harness.rules.fs.DirectoryPresenceRule;
import com.ririnto.sinon.harness.rules.fs.EmptyDirectoryPlaceholdersRule;
import com.ririnto.sinon.harness.rules.text.TemplateGroupsRule;
import com.ririnto.sinon.harness.rules.text.DocHeadingsRule;
import com.ririnto.sinon.harness.rules.text.DocContentRule;
import com.ririnto.sinon.harness.rules.text.AgentFrontmatterRule;
import com.ririnto.sinon.harness.rules.text.SkillFrontmatterRule;
import com.ririnto.sinon.harness.rules.text.ScaffoldLeaksRule;
import com.ririnto.sinon.harness.rules.text.HookShebangRule;
import com.ririnto.sinon.harness.rules.text.HookExecutableRule;
import com.ririnto.sinon.harness.rules.text.HookGeneratedMarkerRule;
import com.ririnto.sinon.harness.rules.text.HookStageRule;
import com.ririnto.sinon.harness.rules.text.HookCommandRule;
import com.ririnto.sinon.harness.rules.text.CiHookCommandParityRule;
import com.ririnto.sinon.harness.rules.text.EnvShebangUsageRule;
import com.ririnto.sinon.harness.rules.text.UncheckedTasksRule;
import com.ririnto.sinon.harness.rules.fs.SymlinkSafetyRule;
import com.ririnto.sinon.harness.rules.ast.GreaterThanComparisonRule;
import com.ririnto.sinon.harness.rules.ast.LeafFunctionBlankLinesRule;
import com.ririnto.sinon.harness.rules.ast.LeadingUnderscoreRule;
import com.ririnto.sinon.harness.rules.ast.UnstructuredLoggingRule;
import com.ririnto.sinon.harness.rules.ast.WildcardImportRule;
import com.ririnto.sinon.harness.rules.ast.ImportOverFqnRule;
import com.ririnto.sinon.harness.rules.ast.PublicDeclarationDocCommentRule;
import com.ririnto.sinon.harness.rules.ast.MultilineDocStyleRule;
import com.ririnto.sinon.harness.rules.ast.IfStatementBracesRule;
import com.ririnto.sinon.harness.rules.ast.ClassMemberOrderingRule;
import com.ririnto.sinon.harness.rules.ast.UncheckedCastSuppressionRule;
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
    GREATER_THAN_COMPARISON(GreaterThanComparisonRule.INSTANCE),
    LEAF_FUNCTION_BLANK_LINES(LeafFunctionBlankLinesRule.INSTANCE),
    UNSTRUCTURED_LOGGING(UnstructuredLoggingRule.INSTANCE),
    WILDCARD_IMPORT(WildcardImportRule.INSTANCE),
    IMPORT_OVER_FQN(ImportOverFqnRule.INSTANCE),
    PUBLIC_DECLARATION_DOC_COMMENT(PublicDeclarationDocCommentRule.INSTANCE),
    LEADING_UNDERSCORE(LeadingUnderscoreRule.INSTANCE),
    MULTILINE_DOC_STYLE(MultilineDocStyleRule.INSTANCE),
    IF_STATEMENT_BRACES(IfStatementBracesRule.INSTANCE),
    CLASS_MEMBER_ORDERING(ClassMemberOrderingRule.INSTANCE),
    UNCHECKED_CAST_SUPPRESSION(UncheckedCastSuppressionRule.INSTANCE);
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
     * Determines whether this check applies to the given context.
     *
     * @param ctx shared rule context
     * @return true if this check should be validated
     */
    public boolean applies(RuleContext ctx) {
        return rule.applies(ctx);
    }

    /**
     * Validates the given project directory against this check.
     *
     * @param ctx shared rule context
     * @return a collection of findings; empty if validation passes
     * @throws MojoExecutionException if validation fails
     */
    public Collection<Finding> validate(RuleContext ctx) throws MojoExecutionException {
        return rule.validate(ctx);
    }

    /**
     * Auto-formats the given project directory against this check.
     *
     * @param ctx shared rule context
     * @return a collection of absolute paths modified by this check; empty if no changes
     * @throws MojoExecutionException if formatting fails
     */
    public Collection<Path> format(RuleContext ctx) throws MojoExecutionException {
        return rule.format(ctx);
    }
}
