# OpenSpec Instructions - Root Level

Instructions for AI coding assistants using OpenSpec in this monorepo.

## Monorepo OpenSpec Structure

This project uses a **hierarchical OpenSpec structure** across three sub-projects:

```
openspec/                          # Root: cross-project global specs
├── project.md                     # Global project context
├── config.yaml
├── specs/
│   ├── _global/                   # Global constraints
│   │   ├── architecture/spec.md   # System architecture
│   │   └── api-contracts/spec.md  # Cross-service API contracts
│   └── cross-cutting/             # Cross-project features
│       ├── interview-flow/spec.md # End-to-end interview flow
│       └── auth-flow/spec.md      # End-to-end auth flow
└── changes/

ai_interviewer_front/openspec/     # Frontend specs
├── specs/
│   ├── core-networking/spec.md
│   ├── user-auth/spec.md
│   └── job-match/spec.md
└── changes/

ai_interview_backend/openspec/     # Backend specs
├── specs/
│   ├── gateway-auth/spec.md
│   ├── sse-proxy/spec.md
│   ├── user-service/spec.md
│   ├── interview-session/spec.md
│   ├── resume-service/spec.md
│   ├── job-service/spec.md
│   ├── evaluation/spec.md
│   └── notification/spec.md
└── changes/

ai_interviewer/openspec/           # Model service specs
├── specs/
│   ├── interview-orchestration/spec.md
│   ├── resume-parsing/spec.md
│   ├── question-bank/spec.md
│   └── scoring/spec.md
└── changes/
```

## Spec Ownership Rules

| Scope | Location | Examples |
|-------|----------|---------|
| Single sub-project only | `{sub-project}/openspec/` | Frontend UI component, backend service logic |
| 2+ sub-projects affected | Root `openspec/` | API contract changes, auth flow changes |
| Global constraints | Root `openspec/specs/_global/` | Architecture decisions, security policy |

## Workflow for Cross-Project Changes

1. Create proposal in **root** `openspec/changes/<change-id>/`
2. In `design.md`, explicitly list affected sub-projects
3. In `tasks.md`, break down tasks per sub-project
4. Each sub-project implements its portion independently
5. Archive at root level when all sub-projects are done

## Standard OpenSpec Workflow

Refer to each sub-project's `openspec/AGENTS.md` for the standard three-stage workflow:
1. **Propose** - Create change proposal with spec deltas
2. **Apply** - Implement tasks from the proposal
3. **Archive** - Move completed changes to archive, update specs
