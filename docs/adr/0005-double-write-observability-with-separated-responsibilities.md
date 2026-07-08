# Double-write observability with separated responsibilities

We will keep the Project Observability Store and add LangSmith as an opt-in external observability and evaluation target instead of replacing the existing tables and admin views. The project store remains the local diagnostic and raw-payload authority, while LangSmith is used for LLM/Agent trace inspection, token and cost analysis, datasets, evaluations, and experiment comparison.
