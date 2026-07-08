# Use runtime-managed SQLite for first-stage agent checkpoints

The first LangGraph checkpoint implementation will use a runtime-managed SQLite Checkpoint Store for local and dev environments instead of the Java business PostgreSQL database. Checkpoint files and operational databases are deployment-managed runtime state, not git-versioned artifacts; only configuration templates, setup scripts, and documentation belong in the repository.
