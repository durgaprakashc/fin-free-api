package com.finfreedom.agent.integration;

import com.finfreedom.agent.config.IntegrationProperties;
import com.finfreedom.agent.core.AgentResponse;
import com.finfreedom.agent.core.AgentRole;
import com.finfreedom.agent.core.PipelineStep;
import com.finfreedom.agent.memory.ConversationMemory;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Orchestrates the full integrated pipeline:
 * Jira story → AI agents (PM → Architect → Dev → QA → Reviewer) →
 * GitHub branch + commit + PR → Confluence design docs → Jira status update.
 * <p>
 * This is the Phase 2 evolution of PipelineOrchestrator — it adds
 * external tool integration at the start and end of the pipeline.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(McpToolService.class)
public class PipelineIntegrationService {

    private final JiraIntegrationService jiraService;
    private final GitHubIntegrationService githubService;
    private final ConfluenceIntegrationService confluenceService;
    private final ConversationMemory conversationMemory;
    private final IntegrationProperties properties;

    /**
     * Execute a full integrated pipeline triggered by a Jira issue.
     * <p>
     * Flow:
     * 1. Read Jira story → extract requirement
     * 2. Transition to "In Progress"
     * 3. Run AI pipeline (each step records to conversation memory)
     * 4. Create GitHub branch + commit code + open PR
     * 5. Store design doc + test plan in Confluence
     * 6. Transition Jira to "In Review"
     * 7. Add pipeline summary comment to Jira issue
     *
     * @param issueKey       Jira issue key (e.g., "FIN-42")
     * @param conversationId pipeline conversation ID
     * @return summary of all actions taken
     */
    public IntegratedPipelineResult runFromJiraStory(String issueKey, String conversationId) {
        log.info("Starting integrated pipeline for {}", issueKey);
        var result = IntegratedPipelineResult.builder()
                .issueKey(issueKey)
                .conversationId(conversationId)
                .startTime(Instant.now())
                .actions(new ArrayList<>())
                .build();

        try {
            // Step 1: Read the Jira story
            String storyJson = jiraService.getIssue(issueKey);
            result.getActions().add("Read Jira story: " + issueKey);

            // Step 2: Transition to In Progress
            jiraService.transitionIssue(issueKey, "In Progress");
            result.getActions().add("Transitioned to 'In Progress'");

            // At this point, the AI pipeline steps (PM → Architect → Dev → QA → Reviewer)
            // would be executed by the AgentService using ChatClient beans.
            // In demo mode, these are recorded via the /conversations/{id}/record endpoint
            // by Claude Code acting as each agent persona.

            // Step 3: After pipeline completes, check for outputs in conversation memory
            Optional<String> designOutput = conversationMemory.getLatestByRole(conversationId, AgentRole.ARCHITECT);
            Optional<String> codeOutput = conversationMemory.getLatestByRole(conversationId, AgentRole.DEVELOPER);
            Optional<String> testOutput = conversationMemory.getLatestByRole(conversationId, AgentRole.QA);
            Optional<String> reviewOutput = conversationMemory.getLatestByRole(conversationId, AgentRole.REVIEWER);

            // Step 4: Create GitHub branch + commit + PR (if code was generated)
            if (codeOutput.isPresent()) {
                String branchName = githubService.generateBranchName(issueKey, "pipeline-output");
                githubService.createBranch(branchName);
                result.getActions().add("Created branch: " + branchName);
                result.setBranchName(branchName);

                // Commit the generated code
                Map<String, String> files = new LinkedHashMap<>();
                files.put("pipeline-output/" + issueKey + "/generated-code.java", codeOutput.get());
                testOutput.ifPresent(test ->
                        files.put("pipeline-output/" + issueKey + "/generated-tests.java", test));

                githubService.commitMultipleFiles(branchName, files,
                        "[AI Pipeline] " + issueKey + " — generated code and tests");
                result.getActions().add("Committed " + files.size() + " files");

                // Open PR
                String prDescription = githubService.generatePrDescription(
                        "Pipeline output for " + issueKey,
                        designOutput.orElse("No design document generated"),
                        new ArrayList<>(files.keySet()),
                        testOutput.isPresent() ? "Tests included" : "No tests generated"
                );
                String prResult = githubService.createPullRequest(
                        branchName,
                        "[AI] " + issueKey + " — Pipeline Generated",
                        prDescription
                );
                result.getActions().add("Created pull request");
                result.setPrUrl(extractUrl(prResult));
            }

            // Step 5: Store docs in Confluence
            designOutput.ifPresent(design -> {
                confluenceService.storeDesignDoc(issueKey, design);
                result.getActions().add("Stored design doc in Confluence");
            });
            testOutput.ifPresent(test -> {
                confluenceService.storeTestPlan(issueKey, test);
                result.getActions().add("Stored test plan in Confluence");
            });
            reviewOutput.ifPresent(review -> {
                confluenceService.storeReviewReport(issueKey, review);
                result.getActions().add("Stored review report in Confluence");
            });

            // Step 6: Transition Jira to "In Review"
            jiraService.transitionIssue(issueKey, "In Review");
            result.getActions().add("Transitioned to 'In Review'");

            // Step 7: Add summary comment
            String summary = buildPipelineSummary(result);
            jiraService.addComment(issueKey, summary);
            result.getActions().add("Added pipeline summary comment to Jira");

            result.setStatus("COMPLETED");
        } catch (Exception e) {
            log.error("Integrated pipeline failed for {}: {}", issueKey, e.getMessage(), e);
            result.setStatus("FAILED");
            result.setError(e.getMessage());
            result.getActions().add("FAILED: " + e.getMessage());
        }

        result.setEndTime(Instant.now());
        return result;
    }

    /**
     * Execute a full end-to-end pipeline starting from a raw requirement.
     * <p>
     * Flow:
     * 1. Create Jira epic from the requirement
     * 2. Create child stories from PM agent output (must be recorded in conversation memory first)
     * 3. Transition stories to "In Progress"
     * 4. Create GitHub feature branch
     * 5. After AI pipeline completes: commit code + open PR
     * 6. Store design doc + test plan in Confluence
     * 7. Transition Jira stories to "In Review"
     * 8. Add pipeline summary comment
     *
     * @param requirement    the raw requirement text
     * @param conversationId pipeline conversation ID
     * @param stories        parsed stories from PM/Jira agent output
     * @return summary of all actions taken
     */
    public IntegratedPipelineResult runFromRequirement(String requirement, String conversationId,
                                                       List<Map<String, Object>> stories) {
        log.info("Starting end-to-end pipeline for requirement: {}", truncateLog(requirement, 80));
        var result = IntegratedPipelineResult.builder()
                .conversationId(conversationId)
                .startTime(Instant.now())
                .actions(new ArrayList<>())
                .build();

        try {
            // Step 1: Create Jira epic
            String epicSummary = truncateForJira(requirement, 100);
            String epicResult = jiraService.createIssue("Epic", epicSummary, requirement,
                    "High", null, List.of("sdlc-pipeline", "ai-generated"), null);
            String epicKey = extractIssueKey(epicResult);
            result.setIssueKey(epicKey);
            result.getActions().add("Created Jira epic: " + epicKey);

            // Step 2: Create child stories under the epic
            List<String> storyKeys = new ArrayList<>();
            for (Map<String, Object> story : stories) {
                String storyResult = jiraService.createIssue(
                        "Story",
                        (String) story.getOrDefault("summary", "Untitled Story"),
                        (String) story.getOrDefault("description", ""),
                        (String) story.getOrDefault("priority", "Medium"),
                        story.get("storyPoints") instanceof Number sp ? sp.intValue() : 3,
                        List.of("sdlc-pipeline", "ai-generated"),
                        epicKey
                );
                String storyKey = extractIssueKey(storyResult);
                storyKeys.add(storyKey);
            }
            result.getActions().add("Created " + storyKeys.size() + " stories: " + String.join(", ", storyKeys));

            // Step 3: Transition stories to In Progress
            for (String storyKey : storyKeys) {
                try {
                    jiraService.transitionIssue(storyKey, "In Progress");
                } catch (Exception e) {
                    log.warn("Could not transition {}: {}", storyKey, e.getMessage());
                }
            }
            result.getActions().add("Transitioned stories to 'In Progress'");

            // Step 4: Create GitHub feature branch
            String branchName = githubService.generateBranchName(epicKey, epicSummary);
            githubService.createBranch(branchName);
            result.setBranchName(branchName);
            result.getActions().add("Created branch: " + branchName);

            // Steps 5-8 happen after the AI pipeline runs — call completeEndToEnd() when done
            result.setStatus("AWAITING_PIPELINE");

        } catch (Exception e) {
            log.error("End-to-end pipeline setup failed: {}", e.getMessage(), e);
            result.setStatus("FAILED");
            result.setError(e.getMessage());
            result.getActions().add("FAILED: " + e.getMessage());
        }

        return result;
    }

    /**
     * Complete the end-to-end pipeline after AI agents have finished.
     * Reads outputs from conversation memory, commits to GitHub, publishes to Confluence,
     * and transitions Jira stories.
     *
     * @param epicKey        the Jira epic key created during setup
     * @param branchName     the GitHub branch created during setup
     * @param conversationId the conversation ID with recorded agent outputs
     * @return summary of completion actions
     */
    public IntegratedPipelineResult completeEndToEnd(String epicKey, String branchName,
                                                      String conversationId) {
        log.info("Completing end-to-end pipeline for {} on branch {}", epicKey, branchName);
        var result = IntegratedPipelineResult.builder()
                .issueKey(epicKey)
                .branchName(branchName)
                .conversationId(conversationId)
                .startTime(Instant.now())
                .actions(new ArrayList<>())
                .build();

        try {
            Optional<String> designOutput = conversationMemory.getLatestByRole(conversationId, AgentRole.ARCHITECT);
            Optional<String> codeOutput = conversationMemory.getLatestByRole(conversationId, AgentRole.DEVELOPER);
            Optional<String> testOutput = conversationMemory.getLatestByRole(conversationId, AgentRole.QA);
            Optional<String> reviewOutput = conversationMemory.getLatestByRole(conversationId, AgentRole.REVIEWER);

            // Step 5: Commit generated code to the feature branch
            if (codeOutput.isPresent()) {
                Map<String, String> files = new LinkedHashMap<>();
                files.put("pipeline-output/" + epicKey + "/generated-code.java", codeOutput.get());
                testOutput.ifPresent(test ->
                        files.put("pipeline-output/" + epicKey + "/generated-tests.java", test));

                githubService.commitMultipleFiles(branchName, files,
                        "[AI Pipeline] " + epicKey + " — generated code and tests");
                result.getActions().add("Committed " + files.size() + " files to " + branchName);

                // Open PR
                String prDescription = githubService.generatePrDescription(
                        "Pipeline output for " + epicKey,
                        designOutput.orElse("No design document generated"),
                        new ArrayList<>(files.keySet()),
                        testOutput.isPresent() ? "Tests included" : "No tests generated"
                );
                String prResult = githubService.createPullRequest(
                        branchName,
                        "[AI] " + epicKey + " — Pipeline Generated",
                        prDescription
                );
                result.getActions().add("Created pull request");
                result.setPrUrl(extractUrl(prResult));
            } else {
                result.getActions().add("No code output found in conversation memory — skipping GitHub commit");
            }

            // Step 6: Store docs in Confluence
            designOutput.ifPresent(design -> {
                confluenceService.storeDesignDoc(epicKey, design);
                result.getActions().add("Stored design doc in Confluence");
            });
            testOutput.ifPresent(test -> {
                confluenceService.storeTestPlan(epicKey, test);
                result.getActions().add("Stored test plan in Confluence");
            });
            reviewOutput.ifPresent(review -> {
                confluenceService.storeReviewReport(epicKey, review);
                result.getActions().add("Stored review report in Confluence");
            });

            // Step 7: Transition Jira stories to "In Review"
            try {
                jiraService.transitionIssue(epicKey, "In Review");
                result.getActions().add("Transitioned epic to 'In Review'");
            } catch (Exception e) {
                log.warn("Could not transition {} to In Review: {}", epicKey, e.getMessage());
            }

            // Step 8: Add summary comment
            String summary = buildPipelineSummary(result);
            jiraService.addComment(epicKey, summary);
            result.getActions().add("Added pipeline summary comment to Jira");

            result.setStatus("COMPLETED");
        } catch (Exception e) {
            log.error("End-to-end pipeline completion failed for {}: {}", epicKey, e.getMessage(), e);
            result.setStatus("FAILED");
            result.setError(e.getMessage());
            result.getActions().add("FAILED: " + e.getMessage());
        }

        result.setEndTime(Instant.now());
        return result;
    }

    /**
     * Publish pipeline outputs to external tools after a manual pipeline run.
     * Call this after all agent steps are recorded in conversation memory.
     */
    public IntegratedPipelineResult publishPipelineOutputs(String issueKey, String conversationId) {
        log.info("Publishing pipeline outputs for {}", issueKey);
        // Delegates to the same flow but skips the Jira read + initial transition
        return runFromJiraStory(issueKey, conversationId);
    }

    private String buildPipelineSummary(IntegratedPipelineResult result) {
        var sb = new StringBuilder();
        sb.append("h3. AI SDLC Pipeline Summary\n\n");
        sb.append("||Step||Status||\n");
        result.getActions().forEach(action ->
                sb.append("| ").append(action).append(" | (/) Done |\n"));
        if (result.getBranchName() != null) {
            sb.append("\n*Branch:* ").append(result.getBranchName());
        }
        if (result.getPrUrl() != null) {
            sb.append("\n*PR:* [View PR|").append(result.getPrUrl()).append("]");
        }
        sb.append("\n\n_Generated by AI SDLC Pipeline_");
        return sb.toString();
    }

    private String extractIssueKey(String createResponse) {
        if (createResponse.contains("\"key\"")) {
            int start = createResponse.indexOf("\"key\"") + 7;
            int end = createResponse.indexOf("\"", start);
            if (end > start) {
                return createResponse.substring(start, end);
            }
        }
        return "UNKNOWN";
    }

    private String truncateForJira(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength - 3) + "...";
    }

    private String truncateLog(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    private String extractUrl(String jsonResult) {
        // Simple extraction — in production, use proper JSON parsing
        if (jsonResult.contains("html_url")) {
            int start = jsonResult.indexOf("html_url") + 12;
            int end = jsonResult.indexOf("\"", start);
            if (end > start) {
                return jsonResult.substring(start, end);
            }
        }
        return null;
    }

    @Data
    @Builder
    public static class IntegratedPipelineResult {
        private String issueKey;
        private String conversationId;
        private String status;
        private List<String> actions;
        private String branchName;
        private String prUrl;
        private String error;
        private Instant startTime;
        private Instant endTime;
    }
}
