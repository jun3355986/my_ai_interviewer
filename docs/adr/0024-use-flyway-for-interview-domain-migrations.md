---
status: accepted
---

# Use Flyway for interview domain migrations

The Java Interview Service will own versioned Flyway migrations for interview lineage, branch, message, assessment, and turn-processing schema changes, including conservative backfill of existing sessions. Docker `init.sql` remains a fresh-environment bootstrap aid but is not an upgrade mechanism for populated databases; migrations must baseline the existing schema, add nullable structures before backfill, validate counts and relationships, and only then tighten constraints.
