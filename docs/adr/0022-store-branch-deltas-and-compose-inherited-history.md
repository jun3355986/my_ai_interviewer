---
status: accepted
---

# Store branch deltas and compose inherited history

Each child Interview Branch will persist its parent, Fork Point, and Branch Delta rather than copying inherited messages and assessments into new rows. Branch Transcripts and Python state snapshots are composed from the immutable ancestor path plus the focused branch's delta, preserving one Owning Branch per message, preventing exponential duplication, and requiring referenced history to be hidden or soft-deleted rather than physically removed.
