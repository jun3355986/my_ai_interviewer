---
status: accepted
---

# Decouple turn processing from client streams

Turn Processing will continue on the server after the Flutter page, network connection, or SSE subscription closes; the client stream observes progress but does not own the task lifecycle. Reopening the interview attaches to the durable Turn Attempt or loads its committed result, while only an explicit user cancellation may stop processing and prevent a late result from being committed. This makes leaving the page safe and prevents navigation or transient connectivity from silently discarding submitted answers.
