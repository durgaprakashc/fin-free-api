package com.finfreedom.agent.orchestrator;

import com.finfreedom.agent.core.PipelineStep;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineRequest {

    @NotBlank
    private String requirement;

    private Set<PipelineStep> skipSteps;

    @Builder.Default
    private int maxFixIterations = 2;

    private String conversationId;
}
