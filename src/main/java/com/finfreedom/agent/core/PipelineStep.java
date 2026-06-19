package com.finfreedom.agent.core;

public enum PipelineStep {
    REQUIREMENT(AgentRole.PM, 1, "Convert raw requirement into epics and user stories"),
    STORIES(AgentRole.JIRA, 2, "Convert stories into Jira-ready JSON artifacts"),
    DESIGN(AgentRole.ARCHITECT, 3, "Design technical architecture for the stories"),
    CODE(AgentRole.DEVELOPER, 4, "Generate production-quality implementation code"),
    TEST(AgentRole.QA, 5, "Generate comprehensive test cases"),
    REVIEW(AgentRole.REVIEWER, 6, "Code review and quality assessment"),
    FIX(AgentRole.DEVELOPER, 7, "Apply review feedback and fix identified issues");

    private final AgentRole agentRole;
    private final int order;
    private final String description;

    PipelineStep(AgentRole agentRole, int order, String description) {
        this.agentRole = agentRole;
        this.order = order;
        this.description = description;
    }

    public AgentRole getAgentRole() { return agentRole; }
    public int getOrder() { return order; }
    public String getDescription() { return description; }
}
