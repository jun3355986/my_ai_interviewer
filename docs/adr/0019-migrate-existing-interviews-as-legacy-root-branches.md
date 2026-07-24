---
status: accepted
---

# Migrate existing interviews as legacy root branches

Every existing interview session will become the root Legacy Branch of its own Interview Lineage without changing its persisted conversation, scores, or evaluation. Migration will classify old messages with deterministic conservative rules, keep ambiguous content visible but non-forkable, rebuild resumable AI state from Java/PostgreSQL business data, and remain idempotent with an audit report instead of using model-generated guesses or silently excluding historical interviews.
