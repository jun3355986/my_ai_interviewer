---
status: accepted
---

# Persist message semantics for fork eligibility

Interview Fork eligibility will be based on persisted message semantics and completion state rather than the broad candidate/AI role alone. Completed candidate answers and completed AI prompts that explicitly expect a response are forkable; feedback, stage transitions, final summaries, system triggers, transport errors, and interrupted stream fragments remain visible where appropriate but cannot start a branch. This requires message type, response expectation, and delivery status to be durable parts of the interview-message contract.
