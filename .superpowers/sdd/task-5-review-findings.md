# Task 5 Independent Review Findings

Date: 2026-07-24

Final status: 0 Critical / 0 Important findings remaining.

The first whole-change review reported one Critical and seven Important findings. Follow-up review of the remediations surfaced additional release-blocking compatibility and runtime-wiring gaps. Every finding below was resolved and covered by focused or full regression tests before the final review passed.

## Resolved findings

1. Interview and Evaluation were directly published on host ports even though downstream services trust Gateway identity headers. Compose now keeps both services internal and publishes only Gateway port 9000.
2. Interview endpoints accepted a fallback user when `X-User-Id` was absent. All compatibility, history, replay, start, fork, and Turn Attempt endpoints now require authenticated identity.
3. Turn Attempt reads and mutations did not consistently combine immutable Attempt owner with current Branch and Lineage ownership. All operations and live SSE emissions now enforce all three boundaries.
4. Startup-only stale recovery could leave later orphaned Attempts processing forever. Recovery is periodic and publishes a terminal `INTERRUPTED` event after commit to attached clients.
5. Evaluation could be generated for an incomplete Branch or race the final Turn commit. Generation now locks Lineage then Branch, requires current ownership and `status=2`, and serializes concurrent report creation.
6. Evaluation runtime wiring omitted the branch guard and inherited-assessment services, while transitive Flyway could migrate the shared schema under the wrong history table. Runtime imports are explicit; Flyway auto-configuration, configuration, and dependencies are disabled for Evaluation.
7. Flutter attachment recovery had no bounded automatic reconnect or stale delayed-attachment fence. It now retries at 250 ms, 500 ms, and 1 second, with single-flight and Branch/Attempt fencing.
8. A pending durable start could be cleared by an older late response or inherited by another account. SharedPreferences deletion is conditional and checked; logout, new login, and session expiry clear pending identity.
9. Completed exact start/tail replay could leave the Chat page waiting without canonical data. Terminal replay now reloads the transcript, retries transient refresh failures within bounds, and exposes an explicit UI retry after exhaustion.
10. The populated migration gate checked row counts but not enough semantic/no-op evidence. It now pins the latest expected migration, compares deterministic legacy content, schema, and Flyway-history digests, and proves the second run is unchanged.
11. Compatibility SSE errors exposed internal provider/storage details to clients. Public error payloads now contain stable codes and sanitized messages.
12. Communication report scores used nondeterministic randomness. Equivalent composed assessment paths now produce identical deterministic scores.
13. History transcript/assessment authorization checked the Branch owner but not the current Lineage owner. All composed read paths now fail closed on full or partial reassignment.
14. Legacy list/incomplete/get/cancel/chat/resume paths could continue using a Session after Lineage-only reassignment. Lists join both owners; direct reads validate both; cancellation and every compatibility stream write execute inside one transaction that locks Lineage before Branch and rechecks ownership.

## Final independent evidence

- Java 21: Interview 99 + Evaluation 10 = 109 passed, 0 failures, 0 errors.
- Python maintained tests: 89 passed.
- Flutter: 45 passed; analyzer found no issues.
- Populated PostgreSQL backup: 65 Sessions, 256 Messages, 42 Scores, 0 Evaluations; 65 root Lineages; all six integrity counters zero; Flyway V6; second run unchanged.
- Evaluation dependency tree contains no Flyway dependencies.
- Independent final code review: 0 Critical / 0 Important.

No commit, stage, push, deployment, shared service restart, authoritative database mutation, or real model-provider request was performed.
