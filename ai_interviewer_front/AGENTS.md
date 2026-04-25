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

<skills_system priority="1">

## Available Skills

<!-- SKILLS_TABLE_START -->
<usage>
When users ask you to perform tasks, check if any of the available skills below can help complete the task more effectively. Skills provide specialized capabilities and domain knowledge.

How to use skills:
- Invoke: Bash("openskills read <skill-name>")
- The skill content will load with detailed instructions on how to complete the task
- Base directory provided in output for resolving bundled resources (references/, scripts/, assets/)

Usage notes:
- Only use skills listed in <available_skills> below
- Do not invoke a skill that is already loaded in your context
- Each skill invocation is stateless
</usage>

<available_skills>

<skill>
<name>prompt-optimizer</name>
<description>Prompt engineering expert that helps users craft optimized prompts using 57 proven frameworks. Use when users want to optimize prompts, improve AI instructions, create better prompts for specific tasks, or need help selecting the best prompt framework for their use case.</description>
<location>global</location>
</skill>

</available_skills>
<!-- SKILLS_TABLE_END -->

</skills_system>

# AI Interviewer — Flutter Frontend

## Quick Start

```bash
flutter pub get && flutter run
```

## Architecture

- Provider DI + `ChangeNotifier` services
- Dio HTTP client with auth interceptor
- Token persisted in `SharedPreferences` (`accessToken`)

## HTTP Targets

- Current base URLs in code:
  - user/auth: `http://localhost:9001`
  - job: `http://localhost:9004`
- No direct calls to Python AI service in Flutter code

## Key Files

```
lib/main.dart                # MultiProvider + routes
lib/api/api_client.dart      # Dio + Authorization: Bearer injection
lib/api/user_api.dart        # /auth/*, /users/*
lib/api/job_api.dart         # /api/v1/jobs*
lib/services/auth_service.dart
lib/services/job_service.dart
```

## Gotchas

- `ApiClient.onError` has a TODO for refresh-token handling on 401

