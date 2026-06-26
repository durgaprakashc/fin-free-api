package com.finfreedom.agent.orchestrator;

import com.finfreedom.agent.core.AgentResponse;
import com.finfreedom.agent.core.PipelineStep;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class PipelineResponse {

    private String conversationId;
    private Map<PipelineStep, AgentResponse> stepResults;
    private List<PipelineStep> completedSteps;
    private List<PipelineStep> skippedSteps;
    private PipelineStatus status;
    private Instant startTime;
    private Instant endTime;
    private AgentResponse.TokenUsageInfo totalTokenUsage;

    public enum PipelineStatus { IN_PROGRESS, COMPLETED, FAILED }
}
