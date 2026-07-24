---
status: accepted
---

# Commit interview turns atomically

Each submitted candidate answer will create a durable, idempotent Turn Attempt, but it will become part of the canonical Interview Branch only when the complete AI response, assessment, and state transition can be committed together. Failed or interrupted attempts retain the candidate answer for retry, editing, or discard while excluding partial AI output and transport errors from Business Messages; retries must reuse a stable turn identity so they cannot duplicate answers, scores, or stage advancement. Any unresolved attempt is exposed through Turn Recovery outside the Branch Transcript, and resolved failure details remain diagnostic audit data rather than ordinary replay messages.
