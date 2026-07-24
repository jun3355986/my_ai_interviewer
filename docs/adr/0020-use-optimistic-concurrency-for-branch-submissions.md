---
status: accepted
---

# Use optimistic concurrency for branch submissions

Candidate submissions will include the expected Branch Version and tail message so only one Turn Attempt can advance a particular branch state. A stale or concurrent submission is rejected without overwriting history or silently creating a fork; the client preserves the answer as a Branch Draft and lets the user refresh or explicitly create a child branch from the original question.
