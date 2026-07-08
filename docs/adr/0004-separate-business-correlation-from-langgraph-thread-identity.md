# Separate business correlation from LangGraph thread identity

We will use the Interview Session ID as the business correlation key and a distinct Agent Run ID as the first-stage LangGraph `thread_id`. This keeps whole-interview investigation grouped by the business session while preserving clear checkpoint semantics for each Single-Turn Agent Run.
