package com.finfreedom.agent.core;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
public class AgentResponse {

    private AgentRole role;
    private String content;
    private Instant timestamp;
    private PipelineStep pipelineStep;
    private Map<String, Object> metadata;
    private TokenUsageInfo tokenUsage;

    public record TokenUsageInfo(int inputTokens, int outputTokens, int totalTokens) {}
}
