# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## Claude Operating Rules

These rules are **non-negotiable** and apply to every task in this project. Read them before every implementation.

### Memory & Grounding
- **Always load project memory first.** Run `MEMORY.md` recall before any implementation task. Do not invent facts about the codebase — read the files.
- **No hallucination.** If a class, method, or file is referenced, verify it exists before using it. When in doubt, `Grep` or `Read`.
- **Minimal context window.** Read only the files directly relevant to the task. Do not load entire packages speculatively. Prefer `Grep` over `Read` for discovery.

### Model Selection
| Task type | Model to use |
|---|---|
| Planning, architecture, requirements analysis | `claude-opus-4-8` — deep reasoning |
| Implementation, code generation, refactoring | `claude-sonnet-4-6` — balanced speed and quality |
| Simple lookups, single-file edits, quick fixes | `claude-haiku-4-5` — fast and cheap |

Never use a heavier model when a lighter one is sufficient. Never use a lighter model for architecture decisions.

### Skill Usage
Always invoke the right skill rather than doing the work ad-hoc:

| Goal | Skill |
|---|---|
| Scaffold a new domain module | `/fin-new-module` |
| Run full PM → Architect → Dev → QA → Review pipeline | `/fin-pipeline` |
| Generate tests for a class | `/fin-tests` |
| Review changed files against conventions | `/fin-review` |
| End-to-end pipeline (Req → Jira → Branch → Code → PR) | `/fin-pipeline-e2e` |
| Structured feature development (phases 1–6) | `/sdd-init` → `/sdd-feature` → `/sdd-refine` → `/sdd-plan` → `/sdd-implement` → `/sdd-review` |

Do not skip skills and implement manually when a skill covers the task.

### Java & Code Standards
- **Java 21 idioms are mandatory.** Use records, sealed classes, pattern matching (`instanceof` patterns, switch expressions), text blocks, `var`, and `SequencedCollection` where they apply. Do not write Java 8-style code.
- **Method length ≤ 20 lines.** Extract private helpers when a method exceeds this. No exceptions.
- **No imperative loops over streams.** Use `Stream`, `Collectors`, `Optional`, and method references instead of `for`/`while` blocks wherever the intent is a data transformation.
- **Immutability by default.** Prefer `final` fields, records, and unmodifiable collections. Mutate only when necessary.
- **No magic numbers or strings.** Use named constants, enums, or config values.

### Reactive Development
- Prefer **Project Reactor** (`Mono`/`Flux`) for any new async or I/O-bound logic.
- Use **Spring WebFlux** for new controller endpoints when the underlying service is reactive.
- Use **R2DBC** instead of blocking JPA for new persistence in reactive flows.
- Do not block a reactive pipeline (`block()`, `blockFirst()`, `blockLast()`) outside of test code.
- Wrap legacy blocking calls with `Schedulers.boundedElastic()` when reactive wrapping is unavoidable.

### Testing
- **Tests are not optional.** Every new class gets a corresponding JUnit 5 test class. Every new public method gets at least one positive and one negative test case.
- Use `/fin-tests` skill to generate the test scaffold, then fill in assertions.
- Tests must cover: happy path, boundary values, and exception paths.
- Use `@ExtendWith(MockitoExtension.class)` + Mockito for unit tests. Use `@SpringBootTest` only for integration tests that truly need the full context.
- Assert on behaviour, not implementation details. Never assert on private method calls.

### Project Conventions (non-negotiable)
- Follow the **entity → DTO → repository → service → controller → exception** sequence for every new domain module.
- `@ResponseStatus` on exceptions — never `@ControllerAdvice`.
- Constructor injection via `@RequiredArgsConstructor` — never `@Autowired`.
- Money fields: always `BigDecimal` with `RoundingMode.HALF_UP`, scale 2.
- Username always from `@AuthenticationPrincipal` — never from the request body.
- Response body is always the DTO — never the raw entity.
- Do not deviate from the package structure defined below.

---

# Financial Freedom Calculator — AI-Powered SDLC Demo

## Project Identity

**fin-freedom** is a Spring Boot 4.1 app with two layers:
1. **SDLC Agent Layer** — Agents (PM, Architect, Developer, QA, Reviewer) that orchestrate development via Claude
2. **Domain Layer** — Financial Freedom Calculator (retirement projections, investment planning, debt payoff)

The domain layer is what the agents build features for. The agent layer demonstrates AI-driven SDLC.

**Dev endpoints (after `mvn spring-boot:run`):**
- Swagger UI: http://localhost:8080/swagger-ui.html
- API docs: http://localhost:8080/api-docs
- H2 Console: http://localhost:8080/h2-console (JDBC: `jdbc:h2:mem:appdb`)

**Demo credentials (hardcoded in `SecurityConfig` — intentional for demo simplicity):**

| User | Password | Role |
|---|---|---|
| `demo` | `demo123` | USER |
| `admin` | `admin123` | USER, ADMIN |

Get a token: `POST /api/auth/login` → use `Authorization: Bearer <token>` on all other calls.

---

## Commands

```bash
export ANTHROPIC_API_KEY=your-key-here   # required for agent layer
mvn spring-boot:run                      # JWT_SECRET defaults to dev value — safe locally
mvn test
mvn test -Dtest=RetirementServiceTest#shouldCalculateProjection
mvn package -DskipTests
```

---

## Tech Stack

Spring Boot 4.1.0 / Java 21 / Maven · Spring Data JPA + H2 (dev) / PostgreSQL (prod) · Spring Security + JWT (jjwt 0.12.6) · SpringDoc OpenAPI 2.8.9 · Lombok · Spring AI 2.0.0-M8 (MCP Client only — Claude Code is the AI orchestrator)

---

## Package Structure

```
src/main/java/com/finfreedom/
├── FinFreedomApplication.java
├── agent/
│   ├── core/
│   │   ├── AgentRole.java              # Enum: PM, ARCHITECT, DEVELOPER, QA, REVIEWER, JIRA
│   │   ├── AgentRequest.java           # Input DTO: role + prompt + context map
│   │   ├── AgentResponse.java          # Output DTO: content + TokenUsageInfo inner record (DO NOT MOVE)
│   │   ├── AgentService.java           # Routes to role-specific ChatClient
│   │   ├── AgentController.java        # POST /api/agents/execute, /pipeline
│   │   └── PipelineStep.java           # Enum: REQUIREMENT → STORIES → DESIGN → CODE → TEST → REVIEW
│   ├── config/
│   │   ├── AgentConfig.java            # Empty — Claude Code is the AI, not Spring Boot
│   │   ├── IntegrationProperties.java  # @ConfigurationProperties for Jira/GitHub/Confluence
│   │   └── PromptConfig.java           # Loads .st files from src/main/resources/prompts/
│   ├── orchestrator/
│   │   ├── PipelineOrchestrator.java   # Chains agents: PM → Architect → Dev → QA → Reviewer
│   │   ├── PipelineRequest.java        # Input: requirement text + which steps to run
│   │   └── PipelineResponse.java       # Output: Map<PipelineStep, AgentResponse> + PipelineStatus
│   ├── integration/                    # Phase 2: MCP tool integrations
│   │   ├── McpToolService.java         # Wraps MCP tool callbacks — discovery + invocation
│   │   ├── McpToolNotFoundException.java
│   │   ├── JiraIntegrationService.java # Create issues, JQL search, transitions, comments
│   │   ├── GitHubIntegrationService.java # Branches, commits, PRs, file ops
│   │   ├── ConfluenceIntegrationService.java # Create/update pages, store design docs
│   │   ├── PipelineIntegrationService.java   # Full flow: Jira → AI → GitHub PR → Confluence
│   │   └── IntegrationController.java        # REST: /api/integrations/**
│   └── memory/
│       ├── ConversationMemory.java     # Appends/retrieves entries per conversationId
│       ├── ConversationMemoryStore.java# Interface — swap impl to Redis for multi-instance prod
│       └── InMemoryConversationMemoryStore.java
├── calculator/
│   ├── retirement/                     # Fully implemented reference module — copy its shape
│   ├── investment/                     # Planned — same shape as retirement
│   └── debt/                           # Planned — same shape
├── security/
│   ├── JwtUtil.java                    # generate + validate HS256 JWT
│   ├── JwtAuthFilter.java              # OncePerRequestFilter — extracts username from token
│   └── AuthController.java            # POST /api/auth/login → {token, type, username}
└── config/
    ├── SecurityConfig.java             # filterChain + in-memory users (demo/admin) + BCrypt
    └── OpenApiConfig.java

src/main/resources/
├── application.yml
├── prompts/                           # System prompts (.st = StringTemplate) per agent role
│   ├── pm-agent.st
│   ├── architect-agent.st
│   ├── developer-agent.st
│   ├── qa-agent.st
│   ├── reviewer-agent.st
│   └── jira-agent.st
└── context/
    └── project-conventions.md         # Injected into developer/reviewer agent context
```

---

## Coding Conventions

New domain modules always follow this sequence: **entity → DTO → repository → service → controller → exception**

### Entity

```java
@Entity @Table(name = "retirement_projections")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RetirementProjection {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;            // owner field — never a FK to a User entity

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal currentSavings;  // money: always BigDecimal, never double/float

    @Column(nullable = false, updatable = false)
    private Instant createdAt;          // timestamps: always Instant

    @PrePersist void onCreate() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate  void onUpdate() { updatedAt = Instant.now(); }
}
```

### DTO — `static from(Entity)` factory is mandatory; never call constructors in mappers

```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RetirementDTO {
    private Long id;

    @NotNull @Min(18) @Max(100)
    private Integer currentAge;         // validation lives on DTO, not entity

    private BigDecimal projectedCorpus; // computed by service — ignored on create/update input

    public static RetirementDTO from(RetirementProjection entity) {
        return RetirementDTO.builder()
                .id(entity.getId())
                .currentAge(entity.getCurrentAge())
                .projectedCorpus(entity.getProjectedCorpus())
                .build();
    }
}
```

### Exception — `@ResponseStatus`, never `@ControllerAdvice`

```java
@ResponseStatus(HttpStatus.NOT_FOUND)
public class RetirementNotFoundException extends RuntimeException {
    public RetirementNotFoundException(Long id) {
        super("Retirement projection not found with id: " + id);
    }
}
```

### Service — owns all business logic; `@Transactional` at class level

```java
@Service @Transactional @RequiredArgsConstructor
public class RetirementService {
    private final RetirementRepository repository;

    public RetirementDTO create(RetirementDTO dto, String username) {
        RetirementProjection entity = RetirementProjection.builder()
                .username(username)
                .currentAge(dto.getCurrentAge())
                // ...
                .build();
        calculate(entity);                        // business logic in private method
        return RetirementDTO.from(repository.save(entity));
    }

    @Transactional(readOnly = true)               // always annotate reads explicitly
    public RetirementDTO findById(Long id, String username) {
        return repository.findByIdAndUsername(id, username)
                .map(RetirementDTO::from)
                .orElseThrow(() -> new RetirementNotFoundException(id));
    }
}
```

### Controller — thin: validate input, delegate, return DTO

```java
@RestController @RequestMapping("/api/calculator/retirement")
@RequiredArgsConstructor @Tag(name = "Retirement Calculator")
public class RetirementController {
    private final RetirementService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<RetirementDTO> create(
            @RequestBody @Valid RetirementDTO dto,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(dto, user.getUsername()));
    }
}
```

### Phase 3: End-to-End Pipeline (`/fin-pipeline-e2e`)

The full automated SDLC flow. Type a requirement, get a Jira epic, feature branch, code, tests, PR, and Confluence docs — all automated.

**Flow:**
```
Requirement → PM Agent → Jira Agent → [Jira Epic + Stories + GitHub Branch] →
Architect Agent → Developer Agent → QA Agent → Reviewer Agent →
[GitHub Commit + PR] → [Confluence Docs] → [Jira Transition to In Review]
```

**Prerequisites:** App must be running with MCP servers connected. Set env vars:
`ATLASSIAN_SITE_URL`, `ATLASSIAN_USER_EMAIL`, `ATLASSIAN_API_TOKEN`, `GITHUB_TOKEN`

**Key integration endpoints (Phase 3):**

| Endpoint | Purpose |
|---|---|
| `POST /api/integrations/pipeline/from-requirement` | Creates Jira epic + stories + GitHub branch from a raw requirement |
| `POST /api/integrations/pipeline/complete/{epicKey}` | Commits code, opens PR, publishes Confluence docs, transitions Jira |
| `POST /api/integrations/pipeline/from-jira/{issueKey}` | Runs pipeline starting from an existing Jira story |
| `POST /api/integrations/pipeline/publish/{issueKey}` | Publishes existing pipeline outputs to external tools |

### Integration API Conventions

| Path prefix | Auth | Notes |
|---|---|---|
| `/api/integrations/tools` | Yes | MCP tool discovery and raw invocation |
| `/api/integrations/jira/**` | Yes | Jira operations: create issues, JQL search, transitions |
| `/api/integrations/github/**` | Yes | GitHub operations: branches, commits, PRs |
| `/api/integrations/confluence/**` | Yes | Confluence operations: create/search pages |
| `/api/integrations/pipeline/**` | Yes | Full integrated pipeline: Jira → AI → GitHub PR → Confluence |

**Environment variables for MCP servers (no Anthropic API key needed — Claude Code is the AI):**

| Variable | Purpose |
|---|---|
| `ATLASSIAN_SITE_URL` | Atlassian domain e.g., `your-site.atlassian.net` (no https://) |
| `ATLASSIAN_USER_EMAIL` | Atlassian account email |
| `ATLASSIAN_API_TOKEN` | Atlassian API token (generate at id.atlassian.com/manage-profile/security/api-tokens) |
| `GITHUB_TOKEN` | GitHub Personal Access Token |
| `JIRA_PROJECT_KEY` | Jira project key (default: FIN) |
| `GITHUB_OWNER` | GitHub org/user (default: your-org) |
| `GITHUB_REPO` | GitHub repo name (default: fin-free) |
| `CONFLUENCE_SPACE_KEY` | Confluence space key (default: FIN) |

### Spring AI ChatClient (not used — Claude Code is the AI orchestrator)

```java
@Bean
public ChatClient pmAgentClient(ChatClient.Builder builder) {
    return builder
        .defaultSystem(promptConfig.getPrompt("pm-agent"))
        .defaultOptions(AnthropicChatOptions.builder()
            .model("claude-sonnet-4-6")
            .maxTokens(4096)
            .temperature(0.7)
            .build())
        .build();
}
```

---

## API Conventions

| Path prefix | Auth | Notes |
|---|---|---|
| `/api/auth/**` | No | Login only |
| `/api/agents/roles` | No | Public role list |
| `/api/agents/**` | Yes | All other agent endpoints |
| `/api/calculator/**` | Yes | Domain endpoints |
| `/swagger-ui/**`, `/api-docs/**` | No | OpenAPI |
| `/h2-console/**` | No | Dev only |

**Rules:**
- Response body is always the DTO, never the raw entity
- 201 on POST create; 204 on DELETE; 200 otherwise
- 400 validation errors, 404 not-found, 401/403 auth errors
- Username always comes from `@AuthenticationPrincipal UserDetails` — never from the request body

---

## Architecture Decisions

- **In-memory users** — Demo simplicity; no `User` entity or registration flow. Financial data is scoped per username. Swap to a DB-backed `UserDetailsService` bean when real auth is needed.
- **`@Transactional(readOnly = true)` on queries** — Skips Hibernate dirty-checking. Class-level `@Transactional` covers writes; reads override explicitly.
- **`ConversationMemoryStore` is an interface** — In-memory impl for dev; Redis-backed impl swappable for multi-instance prod without touching orchestration code.
- **`.st` files for prompts** — Spring AI `PromptTemplate` supports StringTemplate variable injection. Externalised so prompt engineers iterate without recompiling.
- **No `@ControllerAdvice`** — `@ResponseStatus` on exception classes keeps the HTTP contract co-located with the exception. Sufficient at this scope.
- **`PipelineStep` is an enum** — Steps are fixed at compile time; `Map<PipelineStep, AgentResponse>` is cleaner than a join table.

---

## Calculator Business Rules (do not change without updating tests)

- **Projected corpus** = FV of current savings + FV of monthly contributions (compound annuity formula)
- **Inflation adjustment** = nominal corpus ÷ (1 + inflationRate/100)^yearsToRetirement
- **Monthly income** = inflationAdjustedCorpus × 4% ÷ 12 (4% safe withdrawal rule)
- Edge case: if `retirementAge ≤ currentAge`, corpus = currentSavings, income = currentSavings ÷ 240
- All money uses `BigDecimal` with `RoundingMode.HALF_UP`, scale 2; rates use `double`

---

## Known Constraints (must not regress)

### Lombok annotation processor
`maven-compiler-plugin` in `pom.xml` must declare Lombok in `annotationProcessorPaths`. Already present. Do not remove — without it Lombok annotations silently produce no code and every build fails with `cannot find symbol`.

### `AgentResponse.TokenUsageInfo` inner record
`PipelineResponse.totalTokenUsage` is typed as `AgentResponse.TokenUsageInfo`. This nested record must remain inside `AgentResponse`. Do not move or extract it to a top-level class.

### `SecurityConfig` ↔ `JwtAuthFilter` circular dependency
Inject `JwtAuthFilter` as a **method parameter** on `filterChain()`, never as a class-level field. Class-field injection creates a startup cycle: `SecurityConfig → JwtAuthFilter → UserDetailsService → SecurityConfig`.

```java
// WRONG — startup fails with circular dependency
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthFilter jwtAuthFilter;
}

// CORRECT — Spring resolves method params after both beans exist
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) { ... }
}
```

### Spring AI MCP integration (Phase 2 + Phase 3)
Claude Code (covered by Claude Pro) is the AI orchestrator — no Anthropic API key needed in the Spring Boot app. `AgentConfig.java` is intentionally empty. All integration services use `@ConditionalOnBean(McpToolService.class)` — if MCP servers are not configured, the integration layer is cleanly absent (no startup errors). The Atlassian MCP server handles both Jira and Confluence; the GitHub MCP server is separate.

**MCP servers** are configured in `src/main/resources/mcp-servers.json`. The app spawns each server as an `npx` subprocess on startup, performs the MCP initialize handshake, and discovers available tools. Two servers are configured: `github` (for branches, commits, PRs) and `atlassian` (for Jira issues, Confluence pages).

**Phase 3 end-to-end flow** adds `runFromRequirement()` and `completeEndToEnd()` methods to `PipelineIntegrationService`. The `/fin-pipeline-e2e` Claude Code command orchestrates: PM → Jira Agent → create epic/stories/branch → Architect → Dev → QA → Reviewer → commit/PR/Confluence/Jira transitions.

---

## What NOT to do

- Do NOT use `@Autowired` — constructor injection via `@RequiredArgsConstructor`
- Do NOT hardcode API tokens — use env vars (`${GITHUB_TOKEN}`, `${ATLASSIAN_API_TOKEN}`, etc.)
- Do NOT add `spring-ai-starter-model-anthropic` — Claude Code is the AI orchestrator, not this app
- Do NOT put business logic in controllers — it belongs in the service
- Do NOT return entities from controllers — always use DTOs with `from(entity)`
- Do NOT create `@ControllerAdvice` — use `@ResponseStatus` on exception classes
- Do NOT inject `JwtAuthFilter` as a class field in `SecurityConfig` (circular dep — see above)
- Do NOT use `double` or `float` for money — use `BigDecimal` with scale 2
