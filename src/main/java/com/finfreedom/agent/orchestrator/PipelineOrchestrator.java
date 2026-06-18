package com.finfreedom.agent.orchestrator;

import com.finfreedom.agent.core.AgentRole;
import com.finfreedom.agent.core.PipelineStep;
import com.finfreedom.agent.memory.ConversationMemory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Documents the SDLC pipeline step order and provides context retrieval.
 * The actual AI execution happens in Claude Code — this service just
 * tracks state and answers "what comes next?"
 */
@Service
@RequiredArgsConstructor
public class PipelineOrchestrator {

    private final ConversationMemory conversationMemory;

    public List<PipelineStep> orderedSteps() {
        return Arrays.stream(PipelineStep.values())
                .sorted(Comparator.comparingInt(PipelineStep::getOrder))
                .toList();
    }

    public String getContextForStep(String conversationId, PipelineStep step) {
        return conversationMemory.getContextForStep(conversationId, step);
    }

    public PipelineStepGuide guideForStep(PipelineStep step) {
        AgentRole role = step.getAgentRole();
        return new PipelineStepGuide(
                step,
                step.getOrder(),
                step.getDescription(),
                role,
                "GET /api/agents/prompts/" + role.name() + " to retrieve the system prompt, "
                        + "then run it in Claude Code with your input. "
                        + "POST /api/agents/conversations/{id}/record to save the output."
        );
    }

    public record PipelineStepGuide(
            PipelineStep step,
            int order,
            String description,
            AgentRole agentRole,
            String instructions
    ) {}
}
