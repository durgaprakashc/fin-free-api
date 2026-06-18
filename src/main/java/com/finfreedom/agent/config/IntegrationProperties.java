package com.finfreedom.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.integration")
public class IntegrationProperties {

    private JiraConfig jira = new JiraConfig();
    private GitHubConfig github = new GitHubConfig();
    private ConfluenceConfig confluence = new ConfluenceConfig();

    @Data
    public static class JiraConfig {
        private String projectKey = "FIN";
        private String boardId = "1";
    }

    @Data
    public static class GitHubConfig {
        private String owner = "your-org";
        private String repo = "fin-free";
        private String baseBranch = "main";
    }

    @Data
    public static class ConfluenceConfig {
        private String spaceKey = "FIN";
    }
}
