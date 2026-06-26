# Financial Freedom Calculator

An AI-powered SDLC demo built on Spring Boot 4.1 / Java 21.

The app has two layers:
- **Domain layer** — Financial Freedom Calculator (retirement projections, investment planning, debt payoff)
- **SDLC Agent layer** — PM / Architect / Developer / QA / Reviewer agents orchestrated by Claude Code

## Quick Start

```bash
export ANTHROPIC_API_KEY=your-key-here
mvn spring-boot:run
```

| URL | Purpose |
|-----|---------|
| http://localhost:8080/swagger-ui.html | Interactive API docs |
| http://localhost:8080/api-docs | OpenAPI spec |
| http://localhost:8080/h2-console | In-memory DB (dev only) |

## Auth

```bash
# Get a JWT
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"demo123"}'

# Use the token
curl http://localhost:8080/api/calculator/retirement \
  -H "Authorization: Bearer <token>"
```

Demo users: `demo / demo123` · `admin / admin123`

## Build & Test

```bash
mvn test
mvn package -DskipTests
```

## Tech Stack

Spring Boot 4.1 · Java 21 · Maven · Spring Data JPA · H2 (dev) · Spring Security + JWT · SpringDoc OpenAPI · Spring AI 2.0 (MCP client)
