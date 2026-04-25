<!-- OPENSPEC:START -->
# OpenSpec Instructions

These instructions are for AI assistants working in this project.

Always open `@/openspec/AGENTS.md` when the request:
- Mentions planning or proposals (words like proposal, spec, change, plan)
- Introduces new capabilities, breaking changes, architecture shifts, or big performance/security work
- Sounds ambiguous and you need the authoritative spec before coding

Use `@/openspec/AGENTS.md` to learn:
- How to create and apply change proposals
- Spec format and conventions
- Project structure and guidelines

Keep this managed block so 'openspec update' can refresh the instructions.

<!-- OPENSPEC:END -->

# AI Interviewer — Java Backend

Ports/infra: see root `AGENTS.md`

## Gateway Routing & Auth

- Routes (from gateway `application.yml`): `/api/v1/...` -> `lb://ai-interviewer-*` with `StripPrefix=2`
- Whitelist (no auth): `/api/v1/auth/login`, `/api/v1/auth/register`, `/api/v1/auth/refresh` (+ docs endpoints)
- Auth filter validates `Authorization: Bearer <jwt>` and forwards identity via headers:
  - `X-User-Id`, `X-User-Name`, `X-User-Roles`

## Python AI Integration

- Resume service config: `python-ai.base-url` (default `http://localhost:8000`)
- Interview service config: `python.ai.base-url` (env `PYTHON_AI_BASE_URL`, default `http://localhost:8000`); long timeouts for SSE

## Code Pattern

- Controller → Service → Mapper (MyBatis-Plus) with entities mapped to `t_*` tables
- Shared response wrapper: `com.aiinterviewer.common.model.Result<T>`

## Gotchas

- Minimal automated tests
- docker-compose includes Swarm-style `deploy:` under nacos; ignore unless using Swarm
