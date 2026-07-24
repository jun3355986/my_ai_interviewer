---
status: accepted
---

# Create child interview sessions for replay forks

When a user continues from a selected historical message, the system will create a child Interview Session with its own progress, messages, scores, and evaluation while keeping the source session immutable. Continuing the latest progress of an unfinished session remains an in-place continuation and does not create a branch; this preserves the evidentiary value of completed histories and prevents later answers from rewriting earlier results. Selecting a historical message only prepares a Branch Draft: selecting a candidate answer inherits the context before that answer and pre-fills it for editing, while selecting an AI message inherits through that message and waits for a new answer. The child session is created only when the candidate explicitly submits the draft. A completed branch cannot be extended at its tail and is read-only by default, but any eligible historical message may still be used to create a child branch. Creating a child branch does not pause, cancel, or otherwise change its source branch, so one Interview Lineage may contain multiple Active Branches.
