# Keep Java interview session as the business authority

We will keep the Java Interview Service and PostgreSQL as the authority for Interview Session state, while using LangGraph only for Python AI Agent Run checkpoints and LangSmith for observability and evaluation. This avoids competing session authorities across Java, Python, and LangGraph while still allowing checkpoint replay, fork, and token/cost analysis inside the agent layer.
