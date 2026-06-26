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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
     * Stories are automatically added to the active sprint so they appear on the Scrum board.
     */
    public String createEpicWithStories(String epicSummary, String epicDescription,
                                        List<Map<String, Object>> stories) {
        log.info("Creating epic '{}' with {} stories", epicSummary, stories.size());

        String epicResult = createIssue("Epic", epicSummary, epicDescription,
                "High", null, List.of("sdlc-pipeline"), null);

        String epicKey = extractIssueKey(epicResult);
        StringBuilder results = new StringBuilder();
        results.append("Epic: ").append(epicKey).append("\n");

        List<String> storyKeys = new ArrayList<>();
        for (Map<String, Object> story : stories) {
            String storyResult = createIssue(
                    "Story",
                    (String) story.getOrDefault("summary", "Untitled Story"),
                    (String) story.getOrDefault("description", ""),
                    (String) story.getOrDefault("priority", "Medium"),
                    story.get("storyPoints") instanceof Number sp ? sp.intValue() : 3,
                    List.of("sdlc-pipeline"),
                    epicKey
            );
            String storyKey = extractIssueKey(storyResult);
            storyKeys.add(storyKey);
            results.append("  Story: ").append(storyKey).append(" - ")
                    .append(story.getOrDefault("summary", "")).append("\n");
        }

        String sprintResult = assignStoriesToActiveSprint(properties.getJira().getBoardId(), storyKeys);
        results.append(sprintResult);

        return results.toString();
    }

    /**
     * Find the active sprint for a board and add the given issues to it.
     * On a Scrum board, issues must be in a sprint to appear on the board view.
     *
     * @param boardId   Jira board ID (e.g., "2")
     * @param issueKeys list of issue keys to add to the active sprint
     * @return status message
     */
    public String assignStoriesToActiveSprint(String boardId, List<String> issueKeys) {
        if (issueKeys == null || issueKeys.isEmpty()) {
            return "";
        }
        String activeSprintId = getActiveSprintId(boardId);
        if (activeSprintId == null) {
            log.warn("No active sprint found for board {} — {} issues remain in backlog",
                    boardId, issueKeys.size());
            return "  [No active sprint on board " + boardId + " — stories are in backlog]\n";
        }
        log.info("Adding {} issues to sprint {} on board {}", issueKeys.size(), activeSprintId, boardId);
        addIssuesToSprint(activeSprintId, issueKeys);
        return "  Assigned " + issueKeys.size() + " stories to sprint " + activeSprintId + "\n";
    }

    /**
     * Add a list of issues to a specific sprint.
     */
    public String addIssuesToSprint(String sprintId, List<String> issueKeys) {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("sprintId", sprintId);
        ArrayNode keys = args.putArray("issueKeys");
        issueKeys.forEach(keys::add);
        return mcpToolService.invokeTool("jira_add_issues_to_sprint", args.toString());
    }

    /**
     * Return the ID of the active sprint for a board, or null if none exists.
     */
    private String getActiveSprintId(String boardId) {
        try {
            ObjectNode args = objectMapper.createObjectNode();
            args.put("boardId", boardId);
            args.put("state", "active");
            String sprintsJson = mcpToolService.invokeTool("jira_get_sprints_from_board", args.toString());

            JsonNode root = objectMapper.readTree(sprintsJson);
            JsonNode values = root.has("values") ? root.get("values") : root;
            if (values.isArray() && !values.isEmpty()) {
                return values.get(0).get("id").asText();
            }
        } catch (JsonProcessingException e) {
            log.warn("Could not parse sprints response: {}", e.getMessage());
        }
        return null;
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

    private static final Pattern JIRA_KEY_PATTERN = Pattern.compile("\\b([A-Z][A-Z0-9]+-\\d+)\\b");

    private String extractIssueKey(String createResponse) {
        // 1. Try top-level "key" in JSON
        try {
            JsonNode node = objectMapper.readTree(createResponse);
            if (node.has("key")) {
                return node.get("key").asText();
            }
            // 2. Try one level deep (e.g. {"issue": {"key": "FIN-1"}})
            JsonNode keyNode = node.findValue("key");
            if (keyNode != null && !keyNode.isNull()) {
                return keyNode.asText();
            }
        } catch (JsonProcessingException e) {
            log.debug("Response is not JSON, falling back to regex key extraction");
        }
        // 3. Regex fallback for plain-text responses like "Created issue FIN-42"
        Matcher m = JIRA_KEY_PATTERN.matcher(createResponse);
        if (m.find()) {
            return m.group(1);
        }
        log.warn("Could not extract issue key from response: {}", createResponse);
        return "UNKNOWN";
    }

    private String truncate(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength - 3) + "...";
    }
}
