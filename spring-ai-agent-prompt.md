# AI-Powered SDLC Agent System — Bootstrap Spec

> **How to use:**
> 1. Edit the `## CONFIGURATION` section below to match your project
> 2. Open an empty directory in Claude Code and send:
>    **`@spring-ai-agent-prompt.md — Bootstrap this project from the configuration block`**
> 3. Claude Code generates everything. Then run the app and verify.

---

## CONFIGURATION

> Edit everything in this section. The rest of the file generates from it.

---

### Project identity

```
PROJECT_NAME:    fin-freedom
PACKAGE:         com.finfreedom
DESCRIPTION:     Financial Freedom Calculator — AI-powered SDLC demo
PORT:            8080
```

---

### Tech stack

Choose one option per row. Delete the options you don't want.

```
FRAMEWORK:       Spring Boot 4.1.0
LANGUAGE:        Java 21
BUILD:           Maven | Gradle
DATABASE:        H2 in-memory | PostgreSQL | MySQL | MongoDB
AUTH:            JWT (jjwt 0.12.6) | OAuth2 Resource Server | None
API_DOCS:        SpringDoc OpenAPI 2.8.9 | None
UTILITIES:       Lombok
```

**Database connection** (fill in if not H2):
```
DB_HOST:         localhost
DB_PORT:         5432
DB_NAME:         finfreedom
DB_USER:         ${DB_USER}
DB_PASSWORD:     ${DB_PASSWORD}
```

**Auth users** (for JWT — in-memory for dev):
```
USERS:
  - username: demo,  password: demo123, roles: USER
  - username: admin, password: admin123, roles: USER ADMIN
```

---

### Agent roles

Add, remove, or edit rows. Claude generates one `.st` prompt file and one `AgentRole` enum entry per row.

| Role key | Display name | Persona | Model | Temp | Max tokens | Output format |
|---|---|---|---|---|---|---|
| PM | Product Manager | Senior PM focused on user value and testable acceptance criteria | claude-sonnet-4-6 | 0.7 | 4096 | Markdown: Epic + numbered User Stories with Given/When/Then ACs, priority, story points |
| ARCHITECT | Solution Architect | Senior architect who designs APIs, data models, and component boundaries | claude-sonnet-4-6 | 0.6 | 4096 | Markdown: API contracts, data model, component design, architecture decisions with trade-offs |
| DEVELOPER | Senior Developer | Produces production-quality compilable code following project conventions exactly | claude-sonnet-4-6 | 0.3 | 8192 | Complete compilable source files, one class per file, with package + all imports |
| QA | QA Engineer | Writes comprehensive tests covering happy path, edge cases, and boundaries | claude-sonnet-4-6 | 0.4 | 4096 | JUnit 5 + Mockito + AssertJ test files. Naming: should_{expected}_when_{condition} |
| REVIEWER | Staff Reviewer | Staff engineer reviewing for correctness, security, performance, and conventions | claude-sonnet-4-6 | 0.2 | 4096 | Structured review: APPROVE/REQUEST_CHANGES verdict, findings with file:line + fix snippets |
| JIRA | Jira Agent | Converts artifacts into structured Jira-ready issue JSON | claude-sonnet-4-6 | 0.3 | 2048 | JSON array of issues: issueType, summary, description, acceptanceCriteria, priority, storyPoints, labels |

**Pipeline order** (matches `PipelineStep` enum — adjust if you change roles):
```
1. REQUIREMENT  → PM
2. STORIES      → JIRA
3. DESIGN       → ARCHITECT
4. CODE         → DEVELOPER
5. TEST         → QA
6. REVIEW       → REVIEWER
7. FIX          → DEVELOPER
```

**Context flow** (which prior roles feed into each step):
```
STORIES   ← PM
DESIGN    ← PM
CODE      ← ARCHITECT
TEST      ← DEVELOPER
REVIEW    ← DEVELOPER, QA
FIX       ← REVIEWER, DEVELOPER
```

---

### Domain modules

Add one row per module. Claude generates the full `entity → dto → repository → service → controller → exception` stack for each.

| Module | Table name | REST path | Key domain fields | Core calculation |
|---|---|---|---|---|
| retirement | retirement_projections | /api/calculator/retirement | currentAge(int), retirementAge(int), currentSavings(BigDecimal), monthlyContribution(BigDecimal), expectedReturnRate(double), inflationRate(double) → projectedCorpus(BigDecimal), monthlyRetirementIncome(BigDecimal) | Compound interest FV of savings + annuity FV of contributions, inflation-adjusted, 4% safe withdrawal rate |

> To add more modules (e.g. investment, debt), add rows here. Each row generates 6 Java files.

---

### Skills (Claude Code slash commands)

Add, remove, or edit rows. Claude generates one `.md` file per row in `.claude/commands/`.

| Skill file | Trigger | What it does |
|---|---|---|
| fin-new-module | `/fin-new-module <name> <description>` | Scaffolds a full domain module (6 files) from the domain module pattern. Derives package, class names, table name, REST path from the name arg. Prints the target directory. |
| fin-pipeline | `/fin-pipeline <requirement>` | Runs the full pipeline in order: login → for each step, fetch prompt from API → activate persona → record output. Prints a summary table. Applies CRITICAL reviewer fixes before finishing. |
| fin-tests | `/fin-tests <file-path>` | Reads the class, detects Service or Controller, generates a complete test file with @Nested per method, fixture helpers, and full coverage (happy path, not-found, boundaries, domain edge cases). |
| fin-review | `/fin-review [file]` | Runs git diff HEAD if no file given. Reviews against all 8 dimensions (correctness, security, validation, conventions, performance, error handling, maintainability, test coverage). Applies CRITICAL fixes if verdict is REQUEST_CHANGES. |

---

## CODING CONVENTIONS
> These are injected into the DEVELOPER agent prompt and CLAUDE.md. Claude enforces them on all generated code.

```
- Constructor injection via @RequiredArgsConstructor — NEVER @Autowired
- Controllers return DTOs — NEVER entities
- DTOs have static from(Entity) factory — NEVER constructor mapping in service
- Exceptions use @ResponseStatus(HttpStatus.NOT_FOUND) — NO @ControllerAdvice
- @Service @Transactional @RequiredArgsConstructor on every service class
- BigDecimal with RoundingMode.HALF_UP for all money calculations
- Instant for all timestamps — never java.util.Date
- @Valid on all @RequestBody parameters
- Optional.orElseThrow() — NEVER .get()
- @Operation(summary) and @Tag(name) on all controllers
- new domain module order: entity → dto → repository → service → controller → exception
```

---

## GENERATION RULES
> Claude reads the configuration above and applies these rules. Do not edit this section.

### 1. Maven / Gradle setup
- Read `FRAMEWORK`, `LANGUAGE`, `BUILD`, `DATABASE`, `AUTH`, `API_DOCS`, `UTILITIES` from config
- Generate `pom.xml` or `build.gradle` with the correct dependencies for the chosen options:
  - H2 → `com.h2database:h2:runtime`
  - PostgreSQL → `org.postgresql:postgresql:runtime`
  - MySQL → `com.mysql:mysql-connector-j:runtime`
  - MongoDB → `spring-boot-starter-data-mongodb` (replace JPA starter)
  - JWT → `io.jsonwebtoken:jjwt-api/impl/jackson:0.12.6`
  - OAuth2 → `spring-boot-starter-oauth2-resource-server`
  - SpringDoc → `springdoc-openapi-starter-webmvc-ui:2.8.9`
- Always add Spring Milestones and Snapshots repositories (needed for Spring Boot 4.x)

### 2. application.yml
- Generate datasource config from `DATABASE` choice and connection variables
- Generate JWT config (`app.jwt.secret`, `app.jwt.expiration-ms`) if AUTH = JWT
- Public endpoints: `/api/auth/**`, `/swagger-ui/**`, `/api-docs/**`, `/h2-console/**` (H2 only), `/api/agents/roles`
- Enable H2 console only if DATABASE = H2

### 3. Security layer
- If AUTH = JWT: generate `JwtUtil`, `JwtAuthFilter`, `AuthController` (POST /api/auth/login), `SecurityConfig`
  - In-memory users from the `USERS` config block
  - Stateless session, JWT filter before UsernamePasswordAuthenticationFilter
- If AUTH = OAuth2: generate `SecurityConfig` with `.oauth2ResourceServer(oauth2 -> oauth2.jwt(...))`
- If AUTH = None: generate `SecurityConfig` that permits all requests

### 4. Agent layer
For each row in the **Agent roles** table:
- Add an enum constant to `AgentRole` with the role key, display name, model, temperature, maxTokens
- Generate a `.st` prompt file at `src/main/resources/prompts/<role-key-lowercase>-agent.st`
  - First line: `You are a <display name>. <persona>.`
  - Then: output format rules, quality constraints, and relevant project conventions
  - DEVELOPER agent prompt must include the full coding conventions list
  - REVIEWER agent prompt must reference all 8 review dimensions

For the **Pipeline order** and **Context flow**:
- Generate `PipelineStep` enum entries in the declared order
- Wire context flow into `InMemoryConversationMemoryStore.relevantRolesForStep()`

### 5. Domain modules
For each row in the **Domain modules** table, generate 6 files under `src/main/java/<PACKAGE>/calculator/<module>/`:
- **Entity**: `@Entity @Table(name=<table_name>)`, all listed key domain fields, `username String`, `createdAt/updatedAt Instant` via `@PrePersist/@PreUpdate`. Money fields as `BigDecimal(precision=19,scale=2)`.
- **DTO**: mirror all input fields with Bean Validation (`@NotNull`, `@Positive`, `@Min/@Max`, `@DecimalMin/@DecimalMax` as appropriate). Output-only fields (computed + timestamps + id) have no validation. `static from(Entity)` factory.
- **Repository**: extends `JpaRepository`. Methods: `findByUsername(String)`, `findByIdAndUsername(Long, String)`.
- **Exception**: `@ResponseStatus(HttpStatus.NOT_FOUND)`, message includes the bad id.
- **Service**: `@Service @Transactional @RequiredArgsConstructor`. Full CRUD. Private `calculate(Entity)` method implementing the **core calculation** from the config row using `BigDecimal` and `RoundingMode.HALF_UP`.
- **Controller**: `@RestController @RequestMapping(<REST path>)`. CRUD: `POST→201`, `GET list`, `GET/{id}`, `PUT/{id}`, `DELETE/{id}→204`. All use `@AuthenticationPrincipal UserDetails`. All have `@Operation(summary)`.

### 6. Skills
For each row in the **Skills** table:
- Generate `.claude/commands/<skill-file>.md`
- The file starts with a one-line description and usage/example
- The body contains the instructions Claude Code follows when the skill is invoked
- `$ARGUMENTS` receives everything after the slash command name
- Skills that call the API use the base URL `http://localhost:<PORT>` from project config
- The pipeline skill fetches JWT before each run using the first `ADMIN`-role user in the USERS config

### 7. CLAUDE.md
Generate `CLAUDE.md` at project root containing:
- Project description (two layers: agent + domain)
- Tech stack table (from config)
- Full coding conventions
- New module pattern
- What NOT to do (the hard constraints)
- Run instructions with correct port, Swagger URL, H2 console URL (if applicable), and all users
- Skills table (from the skills config rows)

---

## VERIFY

After generating all files, run in this exact order:

```bash
# 1. Compile — must succeed with zero errors
mvn compile          # or: gradle compileJava

# 2. Start the app
mvn spring-boot:run  # or: gradle bootRun

# 3. Get a token (adjust user/password from USERS config)
curl -s -X POST http://localhost:${PORT}/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"demo123"}' | jq -r .token

# 4. Smoke-test domain API (adjust payload for your first domain module)
curl -s -X POST http://localhost:${PORT}/api/calculator/retirement \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"currentAge":30,"retirementAge":60,"currentSavings":50000,
       "monthlyContribution":1000,"expectedReturnRate":8.0,"inflationRate":3.0}' | jq .

# 5. Verify agent roles
curl -s http://localhost:${PORT}/api/agents/roles | jq .

# 6. Fetch a prompt
curl -s http://localhost:${PORT}/api/agents/prompts/PM \
  -H "Authorization: Bearer $TOKEN" | jq -r .content
```

**Pass criteria:**
- Step 4 returns JSON with computed output fields (e.g. `projectedCorpus`, `monthlyRetirementIncome`)
- Step 5 returns exactly as many roles as rows in the Agent roles table
- Step 6 returns a non-empty prompt string

If any step fails, diagnose and fix before reporting done.
