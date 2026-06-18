package com.finfreedom.agent.core;

public enum AgentRole {
    PM("pm-agent", "Product Manager", "claude-sonnet-4-6", 0.7, 4096),
    ARCHITECT("architect-agent", "Solution Architect", "claude-sonnet-4-6", 0.6, 4096),
    DEVELOPER("developer-agent", "Senior Developer", "claude-sonnet-4-6", 0.3, 8192),
    QA("qa-agent", "QA Engineer", "claude-sonnet-4-6", 0.4, 4096),
    REVIEWER("reviewer-agent", "Staff Engineer Reviewer", "claude-sonnet-4-6", 0.2, 4096),
    JIRA("jira-agent", "Jira Integration Agent", "claude-sonnet-4-6", 0.3, 2048);

    private final String promptKey;
    private final String displayName;
    private final String model;
    private final double defaultTemperature;
    private final int defaultMaxTokens;

    AgentRole(String promptKey, String displayName, String model,
              double defaultTemperature, int defaultMaxTokens) {
        this.promptKey = promptKey;
        this.displayName = displayName;
        this.model = model;
        this.defaultTemperature = defaultTemperature;
        this.defaultMaxTokens = defaultMaxTokens;
    }

    public String getPromptKey() { return promptKey; }
    public String getDisplayName() { return displayName; }
    public String getModel() { return model; }
    public double getDefaultTemperature() { return defaultTemperature; }
    public int getDefaultMaxTokens() { return defaultMaxTokens; }
}
