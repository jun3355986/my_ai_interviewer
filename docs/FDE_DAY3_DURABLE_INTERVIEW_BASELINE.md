# FDE Day 3 — Durable Interview Flow Baseline

Date: 2026-08-14

## Objective

Exercise the real durable path rather than the legacy `/interviews/chat` compatibility endpoint:

```text
Flutter durable start/tail request
  -> Gateway
  -> Java Turn Attempt + PostgreSQL canonical state
  -> Python durable turn processor + model/retrieval
  -> Java atomic commit
  -> canonical transcript
  -> Evaluation report
```

The Day 3 runner is [`tests/scripts/durable_interview_replay.py`](../tests/scripts/durable_interview_replay.py). It intentionally excludes JWTs, candidate answers, and question text from its JSON report.

## Flow Repairs Included in This Baseline

1. A project follow-up is explicitly marked and does not increment the main-project-question quota.
2. A technical-stage startup rejects an untyped/project last AI message instead of repeating it as the first technical question.
3. The last technical answer calls one idempotent Python conclusion in the same durable turn; the final summary is persisted as a terminal AI message.
4. `is_followup` travels from Python score SSE through Java `TurnModelResult` into `t_score_record.is_followup`.
5. The Flutter result page requests the completed branch's Java Evaluation report, rather than rendering a zero-filled local `MatchResult`. It shows the persisted total score, four dimensions, summary, strengths, and improvements.

## Measured Evidence

| Layer | Result |
| --- | --- |
| Python model provider, state-machine, durable processor, snapshot reconstruction, SSE, observability regression | 47 passed (`uv run pytest` focused suite) |
| Java model SSE parser and compatibility score persistence | 6 passed after a JDK 21 clean build |
| Java PostgreSQL Turn Attempt integration | 26 passed; includes a `t_score_record.is_followup = true` database assertion |
| Flutter persisted Evaluation result page | 1 widget test passed in the cached Flutter container |
| OpenCode Go provider preflight | Compatibility `/models` discovery and a minimal non-sensitive `deepseek-v4-flash` chat request both returned HTTP 200. Available target IDs: `deepseek-v4-flash`, `mimo-v2.5`, `mimo-v2.5-pro`. |
| Local Docker services | Gateway, Python AI, PostgreSQL, Interview, and Evaluation were healthy/reachable at the time of the replay. Python runtime reported OpenCode Go primary `deepseek-v4-flash`, fallbacks `mimo-v2.5,mimo-v2.5-pro`, SQL trace storage enabled, and raw-payload storage disabled. |
| Real durable replay | Completed 14 durable Gateway steps: opening, 9 project-stage turns, 3 technical-stage turns, then one terminal conclusion. The canonical Branch reached status `2` (`completed`) and generated one persisted Evaluation report. |
| PostgreSQL canonical read-back | Completed attempt; 26 canonical messages; 11 linked score records; 1 Evaluation report; current stage `concluded`. |
| Trace read-back | 13 persisted traces, 21 trace steps, 20 LLM calls, 19,330 aggregate tokens, 150,385 ms aggregate LLM latency; all observed model calls used `deepseek-v4-flash`. Zero prompt/response payload fields were stored. |
| Volcano Ark Agent Plan embedding runtime | Direct OpenAI-compatible and in-process LangChain smokes both succeeded with `doubao-embedding-vision`; requested `dimensions=1024` returned 1024 dimensions. The new isolated Collection was re-embedded from the legacy collection: 615 source records, 615 target records, and exact ID-set equality. |

## Model and Retrieval Configuration

Chat generation uses the OpenCode Go OpenAI-compatible endpoint through the generic provider configuration:

1. Primary: `deepseek-v4-flash`.
2. Ordered fallbacks: `mimo-v2.5`, then `mimo-v2.5-pro`.
3. Each configured chat model is constructed as an independent client. The default chat client uses the ordered fallback chain; an explicit per-call model selection deliberately stays single-model.
4. Embeddings use Volcano Ark Agent Plan's dedicated OpenAI-compatible `/api/plan/v3` endpoint, `doubao-embedding-vision`, and an explicit `dimensions=1024` request. The actual API defaults to a larger vector when `dimensions` is omitted, so the dimension stays explicit and is verified in runtime.
5. Existing `interview_questions` vectors are isolated from the new model in `interview_questions_doubao_embedding_vision_251215_1024_v1`. Both vector spaces happen to use 1024 dimensions, but they are not semantically interchangeable. The repeatable migration tool copies only source IDs into an empty target, limits each provider request to ten inputs, and supports verified resume/backoff after a rate limit.
6. If an embedding key is absent or unavailable, technical-question retrieval still degrades to keyword search without allowing Chroma to silently download/use a different default embedding model. This preserves the interview flow rather than producing hidden provider drift.

The live replay exercised the healthy primary model only. The fallback routing order is covered by `test_model_provider.py`; no runtime fallback event occurred in this run.

## Successful Runtime Acceptance

The authoritative de-identified evidence is [durable-day3-20260814-011051.json](../tests/reports/durable-replay/durable-day3-20260814-011051.json). It contains no JWT, candidate answer, question text, API key, endpoint value, prompt, or model response payload. The separate embedding proof is [volcengine-agent-plan-doubao-embedding-vision-20260815.json](../tests/reports/embedding-provider/volcengine-agent-plan-doubao-embedding-vision-20260815.json); it records only public provider/model configuration and aggregate migration checks.

The synthetic replay used deliberately generic candidate answers. Its low score and `REJECT` recommendation therefore demonstrate that the persisted scoring/evaluation path works; they are not a judgment of a real candidate or a model-quality benchmark.

Re-run the local gate with a valid, non-committed `OPENCODE_GO_API_KEY`:

```bash
OPENCODE_GO_API_KEY='<supplied-at-runtime>' \
  docker compose -f ai_interview_backend/docker-compose.yml up -d --build python-ai
python3 tests/scripts/durable_interview_replay.py --timeout 90 --max-turns 30
```

For a passing run, read back the generated report plus PostgreSQL status/counts: one completed root/branch, a terminal `t_interview_turn_attempt`, canonical business messages, linked score records, one Evaluation report, and aggregate trace data with raw payload storage still disabled.
