---
status: accepted
---

# Serialize turn processing within a lineage

An Interview Lineage will expose one Lineage Processing Slot, so its branches may all remain active but only one Turn Attempt can perform AI processing at a time. Other branches may be viewed and edited as drafts, but submissions are rejected with the active processing context instead of being silently queued; users must wait or explicitly stop the current generation before another branch consumes model capacity.
