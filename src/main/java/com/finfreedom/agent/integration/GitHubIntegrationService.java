package com.finfreedom.agent.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.finfreedom.agent.config.IntegrationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * GitHub integration via GitHub MCP server.
 * <p>
 * Provides high-level operations for the SDLC pipeline output:
 * create feature branches, commit generated code, open PRs with
 * AI-generated descriptions, and manage review workflows.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(McpToolService.class)
public class GitHubIntegrationService {

    private final McpToolService mcpToolService;
    private final IntegrationProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * Create a feature branch from the base branch.
     *
     * @param branchName e.g., "feature/FIN-42-retirement-calculator"
     * @return JSON result from GitHub API
     */
    public String createBranch(String branchName) {
        log.info("Creating branch: {}", branchName);
        ObjectNode args = objectMapper.createObjectNode();
        args.put("owner", properties.getGithub().getOwner());
        args.put("repo", properties.getGithub().getRepo());
        args.put("branch", branchName);
        args.put("from_branch", properties.getGithub().getBaseBranch());
        return mcpToolService.invokeTool("create_branch", args.toString());
    }

    /**
     * Create or update a file in the repository.
     *
     * @param branch  target branch
     * @param path    file path in repo (e.g., "src/main/java/com/finfreedom/calculator/...")
     * @param content file content (will be base64 encoded)
     * @param message commit message
     * @return JSON result from GitHub API
     */
    public String commitFile(String branch, String path, String content, String message) {
        log.info("Committing file: {} to branch: {}", path, branch);
        ObjectNode args = objectMapper.createObjectNode();
        args.put("owner", properties.getGithub().getOwner());
        args.put("repo", properties.getGithub().getRepo());
        args.put("branch", branch);
        args.put("path", path);
        args.put("content", content);
        args.put("message", message);
        return mcpToolService.invokeTool("create_or_update_file", args.toString());
    }

    /**
     * Commit multiple files in a single commit using the Git tree API.
     *
     * @param branch  target branch
     * @param files   map of file path → content
     * @param message commit message
     * @return JSON result
     */
    public String commitMultipleFiles(String branch, Map<String, String> files, String message) {
        log.info("Committing {} files to branch: {}", files.size(), branch);
        ObjectNode args = objectMapper.createObjectNode();
        args.put("owner", properties.getGithub().getOwner());
        args.put("repo", properties.getGithub().getRepo());
        args.put("branch", branch);
        args.put("message", message);

        ArrayNode filesArray = args.putArray("files");
        files.forEach((path, content) -> {
            ObjectNode fileNode = objectMapper.createObjectNode();
            fileNode.put("path", path);
            fileNode.put("content", content);
            filesArray.add(fileNode);
        });

        return mcpToolService.invokeTool("push_files", args.toString());
    }

    /**
     * Open a pull request with an AI-generated description.
     *
     * @param branch      source branch
     * @param title       PR title
     * @param description PR body (markdown)
     * @return JSON result including PR number and URL
     */
    public String createPullRequest(String branch, String title, String description) {
        log.info("Creating PR: '{}' from branch: {}", title, branch);
        ObjectNode args = objectMapper.createObjectNode();
        args.put("owner", properties.getGithub().getOwner());
        args.put("repo", properties.getGithub().getRepo());
        args.put("head", branch);
        args.put("base", properties.getGithub().getBaseBranch());
        args.put("title", title);
        args.put("body", description);
        return mcpToolService.invokeTool("create_pull_request", args.toString());
    }

    /**
     * Get the contents of a file from the repository.
     */
    public String getFileContent(String path, String branch) {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("owner", properties.getGithub().getOwner());
        args.put("repo", properties.getGithub().getRepo());
        args.put("path", path);
        args.put("branch", branch);
        return mcpToolService.invokeTool("get_file_contents", args.toString());
    }

    /**
     * List open pull requests.
     */
    public String listPullRequests() {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("owner", properties.getGithub().getOwner());
        args.put("repo", properties.getGithub().getRepo());
        args.put("state", "open");
        return mcpToolService.invokeTool("list_pull_requests", args.toString());
    }

    /**
     * Add a review comment to a pull request.
     */
    public String addPrReview(int prNumber, String body, String event) {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("owner", properties.getGithub().getOwner());
        args.put("repo", properties.getGithub().getRepo());
        args.put("pull_number", prNumber);
        args.put("body", body);
        args.put("event", event); // APPROVE, REQUEST_CHANGES, COMMENT
        return mcpToolService.invokeTool("create_pull_request_review", args.toString());
    }

    /**
     * Generate a branch name from a Jira issue key and summary.
     */
    public String generateBranchName(String issueKey, String summary) {
        String sanitized = summary.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-");
        if (sanitized.length() > 40) {
            sanitized = sanitized.substring(0, 40);
        }
        return "feature/" + issueKey.toLowerCase() + "-" + sanitized;
    }

    /**
     * Generate a PR description from pipeline step results.
     */
    public String generatePrDescription(String requirement, String designSummary,
                                         List<String> changedFiles, String testSummary) {
        return """
                ## Summary
                %s

                ## Design
                %s

                ## Changed Files
                %s

                ## Tests
                %s

                ---
                *Generated by AI SDLC Pipeline — [ai-generated]*
                """.formatted(
                requirement,
                designSummary,
                changedFiles.stream()
                        .map(f -> "- `" + f + "`")
                        .reduce("", (a, b) -> a + "\n" + b),
                testSummary
        );
    }
}
