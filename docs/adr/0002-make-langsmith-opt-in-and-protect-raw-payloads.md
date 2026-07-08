# Make LangSmith opt-in and protect raw payloads

We will integrate LangSmith behind explicit environment flags: disabled by default, enabled for local or dev diagnostics, and blocked from uploading Raw Payloads in production unless a later decision approves it. Interview data can contain resumes, candidate answers, job requirements, prompts, and model responses, so the first integration should prioritize correlation metadata, token/cost/latency, and non-sensitive summaries over raw capture.
