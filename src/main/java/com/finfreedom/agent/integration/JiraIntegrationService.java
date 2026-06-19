package com.finfreedom.agent.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
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
 * Jira integration via Atlassian MCP server.
 * <p>
 * Provides high-level operations: create epics/stories from pipeline output,
 * search issues via JQL, transition issue statuses, and read sprint backlogs.
 * All calls delegate to the Atlassian MCP server's tool callbacks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(McpToolService.class)
public class JiraIntegrationService {

    private final McpToolService mcpToolService;
    private final IntegrationProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * Create a Jira issue (Epic, Story, Task, Bug).
     *
     * @param issueType one of: Epic, Story, Task, Bug, Sub-task
     * @param summary   issue title (max 100 chars)
     * @param description full description in markdown
     * @param priority  Highest, High, Medium, Low, Lowest
     * @param storyPoints fibonacci: 1, 2, 3, 5, 8, 13
     * @param labels    labels to apply (always includes "ai-generated")
     * @param parentKey parent epic key (e.g., "FIN-1") for stories; null for epics
     * @return JSON string of the created issue
     */
    public String createIssue(String issueType, String summary, String description,
                              String priority, Integer storyPoints, List<String> labels,
                              String parentKey) {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("projectKey", properties.getJira().getProjectKey());
        args.put("issueType", issueType);
        args.put("summary", truncate(summary, 100));
        args.put("description", description);

        if (priority != null) {
            args.put("priority", priority);
        }
        if (storyPoints != null) {
            args.put("storyPoints", storyPoints);
        }
        if (parentKey != null) {
            args.put("parentKey", parentKey);
        }

        ArrayNode labelsArray = args.putArray("labels");
        labelsArray.add("ai-generated");
        if (labels != null) {
            labels.forEach(labelsArray::add);
        }

        return mcpToolService.invokeTool("jira_create_issue", args.toString());
    }

    /**
     * Create an epic and its child stories from pipeline PM agent output.
     * Expects a structured map with epic details and a list of stories.
     */
    public String createEpicWithStories(String epicSummary, String epicDescription,
                                        List<Map<String, Object>> stories) {
        log.info("Creating epic '{}' with {} stories", epicSummary, stories.size());

        String epicResult = createIssue("Epic", epicSummary, epicDescription,
                "High", null, List.of("sdlc-pipeline"), null);

        String epicKey = extractIssueKey(epicResult);
        StringBuilder results = new StringBuilder();
        results.append("Epic: ").append(epicKey).append("\n");

        for (Map<String, Object> story : stories) {
            String storyResult = createIssue(
                    "Story",
                    (String) story.getOrDefault("summary", "Untitled Story"),
                    (String) story.getOrDefault("description", ""),
                    (String) story.getOrDefault("priority", "Medium"),
                    (Integer) story.getOrDefault("storyPoints", 3),
                    List.of("sdlc-pipeline"),
                    epicKey
            );
            String storyKey = extractIssueKey(storyResult);
            results.append("  Story: ").append(storyKey).append(" - ")
                    .append(story.getOrDefault("summary", "")).append("\n");
        }

        return results.toString();
    }

    /**
     * Search Jira issues using JQL.
     */
    public String searchIssues(String jql) {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("jql", jql);
        args.put("maxResults", 50);
        return mcpToolService.invokeTool("jira_search_issues", args.toString());
    }

    /**
     * Get issues in the current sprint.
     */
    public String getSprintIssues() {
        String jql = String.format("project = %s AND sprint in openSprints() ORDER BY priority DESC",
                properties.getJira().getProjectKey());
        return searchIssues(jql);
    }

    /**
     * Get a single issue by key.
     */
    public String getIssue(String issueKey) {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("issueKey", issueKey);
        return mcpToolService.invokeTool("jira_get_issue", args.toString());
    }

    /**
     * Get available transitions for an issue.
     */
    public String getTransitions(String issueKey) {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("issueKey", issueKey);
        return mcpToolService.invokeTool("jira_get_transitions", args.toString());
    }

    /**
     * Transition an issue to a new status.
     * Looks up available transitions first to find the matching transitionId.
     *
     * @param issueKey  e.g., "FIN-42"
     * @param status    target status name, e.g., "In Progress", "In Review", "Done"
     */
    public String transitionIssue(String issueKey, String status) {
        log.info("Transitioning {} to '{}'", issueKey, status);

        // Step 1: Get available transitions
        String transitionsJson = getTransitions(issueKey);
        String transitionId = findTransitionId(transitionsJson, status);

        if (transitionId == null) {
            log.warn("No transition found for {} to status '{}'. Available: {}", issueKey, status, transitionsJson);
            return "No matching transition found for status: " + status;
        }

        // Step 2: Execute the transition
        ObjectNode args = objectMapper.createObjectNode();
        args.put("issueKey", issueKey);
        args.put("transitionId", transitionId);
        return mcpToolService.invokeTool("jira_transition_issue", args.toString());
    }

    /**
     * Find a transition ID by matching the target status name (case-insensitive).
     */
    private String findTransitionId(String transitionsJson, String targetStatus) {
        try {
            JsonNode root = objectMapper.readTree(transitionsJson);
            JsonNode transitions = root.has("transitions") ? root.get("transitions") : root;
            if (transitions.isArray()) {
                for (JsonNode t : transitions) {
                    String name = t.has("name") ? t.get("name").asText() : "";
                    String toName = t.has("to") && t.get("to").has("name")
                            ? t.get("to").get("name").asText() : "";
                    if (name.equalsIgnoreCase(targetStatus) || toName.equalsIgnoreCase(targetStatus)) {
                        return t.get("id").asText();
                    }
                }
            }
        } catch (JsonProcessingException e) {
            log.warn("Could not parse transitions JSON: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Add a comment to an issue (e.g., pipeline progress updates).
     */
    public String addComment(String issueKey, String comment) {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("issueKey", issueKey);
        args.put("body", comment);
        return mcpToolService.invokeTool("jira_add_comment", args.toString());
    }

    private String extractIssueKey(String createResponse) {
        try {
            JsonNode node = objectMapper.readTree(createResponse);
            if (node.has("key")) {
                return node.get("key").asText();
            }
        } catch (JsonProcessingException e) {
            log.warn("Could not parse issue key from response: {}", createResponse);
        }
        return "UNKNOWN";
    }

    private String truncate(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength - 3) + "...";
    }
}
