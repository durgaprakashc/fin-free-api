package com.finfreedom.agent.integration;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST endpoints for MCP tool integration with Jira, GitHub, and Confluence.
 * These endpoints are available only when MCP servers are connected.
 */
@RestController
@RequestMapping("/api/integrations")
@RequiredArgsConstructor
@ConditionalOnBean(McpToolService.class)
@Tag(name = "Integrations", description = "Jira, GitHub, and Confluence MCP tool operations")
public class IntegrationController {

    private final McpToolService mcpToolService;
    private final JiraIntegrationService jiraService;
    private final GitHubIntegrationService githubService;
    private final ConfluenceIntegrationService confluenceService;
    private final PipelineIntegrationService pipelineIntegrationService;

    // ---- MCP Tool Discovery ----

    @GetMapping("/tools")
    @Operation(summary = "List all available MCP tools from connected servers")
    public ResponseEntity<List<String>> listTools() {
        return ResponseEntity.ok(mcpToolService.listToolNames());
    }

    @PostMapping("/tools/{toolName}")
    @Operation(summary = "Invoke a raw MCP tool by name with JSON arguments")
    public ResponseEntity<String> invokeTool(@PathVariable String toolName,
                                              @RequestBody String jsonArgs) {
        return ResponseEntity.ok(mcpToolService.invokeTool(toolName, jsonArgs));
    }

    // ---- Jira ----

    @PostMapping("/jira/issues")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a Jira issue")
    public ResponseEntity<String> createJiraIssue(@RequestBody @Valid CreateIssueRequest request) {
        String result = jiraService.createIssue(
                request.getIssueType(),
                request.getSummary(),
                request.getDescription(),
                request.getPriority(),
                request.getStoryPoints(),
                request.getLabels(),
                request.getParentKey()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/jira/search")
    @Operation(summary = "Search Jira issues via JQL")
    public ResponseEntity<String> searchJira(@RequestParam String jql) {
        return ResponseEntity.ok(jiraService.searchIssues(jql));
    }

    @GetMapping("/jira/sprint")
    @Operation(summary = "Get issues in the current sprint")
    public ResponseEntity<String> getSprintIssues() {
        return ResponseEntity.ok(jiraService.getSprintIssues());
    }

    @GetMapping("/jira/issues/{issueKey}")
    @Operation(summary = "Get a single Jira issue")
    public ResponseEntity<String> getJiraIssue(@PathVariable String issueKey) {
        return ResponseEntity.ok(jiraService.getIssue(issueKey));
    }

    @PostMapping("/jira/issues/{issueKey}/transition")
    @Operation(summary = "Transition a Jira issue to a new status")
    public ResponseEntity<String> transitionIssue(@PathVariable String issueKey,
                                                   @RequestParam String status) {
        return ResponseEntity.ok(jiraService.transitionIssue(issueKey, status));
    }

    @PostMapping("/jira/issues/{issueKey}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a comment to a Jira issue")
    public ResponseEntity<String> addComment(@PathVariable String issueKey,
                                              @RequestBody String comment) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(jiraService.addComment(issueKey, comment));
    }

    // ---- GitHub ----

    @PostMapping("/github/branches")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a feature branch")
    public ResponseEntity<String> createBranch(@RequestParam String branchName) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(githubService.createBranch(branchName));
    }

    @PostMapping("/github/commits")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Commit multiple files to a branch")
    public ResponseEntity<String> commitFiles(@RequestBody @Valid CommitFilesRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(githubService.commitMultipleFiles(
                        request.getBranch(), request.getFiles(), request.getMessage()));
    }

    @PostMapping("/github/pull-requests")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a pull request")
    public ResponseEntity<String> createPullRequest(@RequestBody @Valid CreatePrRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(githubService.createPullRequest(
                        request.getBranch(), request.getTitle(), request.getDescription()));
    }

    @GetMapping("/github/pull-requests")
    @Operation(summary = "List open pull requests")
    public ResponseEntity<String> listPullRequests() {
        return ResponseEntity.ok(githubService.listPullRequests());
    }

    // ---- Confluence ----

    @PostMapping("/confluence/pages")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a Confluence page")
    public ResponseEntity<String> createConfluencePage(@RequestBody @Valid CreatePageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(confluenceService.createPage(
                        request.getTitle(), request.getContent(), request.getParentId()));
    }

    @GetMapping("/confluence/search")
    @Operation(summary = "Search Confluence pages via CQL")
    public ResponseEntity<String> searchConfluence(@RequestParam String cql) {
        return ResponseEntity.ok(confluenceService.searchPages(cql));
    }

    // ---- Integrated Pipeline ----

    @PostMapping("/pipeline/from-jira/{issueKey}")
    @Operation(summary = "Run full integrated pipeline from a Jira story → AI agents → GitHub PR → Confluence docs")
    public ResponseEntity<PipelineIntegrationService.IntegratedPipelineResult> runFromJira(
            @PathVariable String issueKey,
            @RequestParam(defaultValue = "") String conversationId) {
        String convId = conversationId.isBlank()
                ? java.util.UUID.randomUUID().toString()
                : conversationId;
        return ResponseEntity.ok(
                pipelineIntegrationService.runFromJiraStory(issueKey, convId));
    }

    @PostMapping("/pipeline/publish/{issueKey}")
    @Operation(summary = "Publish existing pipeline outputs to Jira/GitHub/Confluence")
    public ResponseEntity<PipelineIntegrationService.IntegratedPipelineResult> publishOutputs(
            @PathVariable String issueKey,
            @RequestParam String conversationId) {
        return ResponseEntity.ok(
                pipelineIntegrationService.publishPipelineOutputs(issueKey, conversationId));
    }

    @PostMapping("/pipeline/from-requirement")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start end-to-end pipeline from a raw requirement — creates Jira epic + stories, GitHub branch")
    public ResponseEntity<PipelineIntegrationService.IntegratedPipelineResult> runFromRequirement(
            @RequestBody @Valid StartFromRequirementRequest request) {
        String convId = request.getConversationId() != null && !request.getConversationId().isBlank()
                ? request.getConversationId()
                : java.util.UUID.randomUUID().toString();
        return ResponseEntity.status(HttpStatus.CREATED).body(
                pipelineIntegrationService.runFromRequirement(
                        request.getRequirement(), convId, request.getStories()));
    }

    @PostMapping("/pipeline/complete/{epicKey}")
    @Operation(summary = "Complete end-to-end pipeline — commit code to GitHub, publish to Confluence, transition Jira")
    public ResponseEntity<PipelineIntegrationService.IntegratedPipelineResult> completeEndToEnd(
            @PathVariable String epicKey,
            @RequestParam String branchName,
            @RequestParam String conversationId) {
        return ResponseEntity.ok(
                pipelineIntegrationService.completeEndToEnd(epicKey, branchName, conversationId));
    }

    // ---- Request DTOs ----

    @Data
    public static class CreateIssueRequest {
        @NotBlank private String issueType;
        @NotBlank private String summary;
        @NotBlank private String description;
        private String priority;
        private Integer storyPoints;
        private List<String> labels;
        private String parentKey;
    }

    @Data
    public static class CommitFilesRequest {
        @NotBlank private String branch;
        @NotBlank private String message;
        private Map<String, String> files;
    }

    @Data
    public static class CreatePrRequest {
        @NotBlank private String branch;
        @NotBlank private String title;
        private String description;
    }

    @Data
    public static class CreatePageRequest {
        @NotBlank private String title;
        @NotBlank private String content;
        private String parentId;
    }

    @Data
    public static class StartFromRequirementRequest {
        @NotBlank private String requirement;
        private String conversationId;
        private List<Map<String, Object>> stories;
    }
}
