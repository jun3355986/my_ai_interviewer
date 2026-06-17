# Test Troubleshooting

## Gateway Health Fails

Check the compose stack:

```bash
cd ai_interview_backend
docker compose ps
docker compose logs -f gateway
```

## Login Fails

Check:

- `SMOKE_USERNAME`
- `SMOKE_PASSWORD`
- user service logs
- gateway auth route

## SSE Fails

Check:

- `interview` service logs
- `python-ai` service logs
- `PYTHON_AI_BASE_URL`
- AI provider API keys
- `SSE_MAX_TIME`

## Playwright Cannot Find Text

Flutter Web may render text differently depending on renderer and semantics.

Prefer adding stable Flutter keys or semantics labels to critical controls:

```dart
Key('login_button')
Key('start_interview_button')
Key('send_answer_button')
```

## k6 SSE Fails

SSE checks are opt-in. Confirm valid test data first:

```bash
K6_RUN_SSE=1 bash tests/scripts/run-performance.sh
```

## Trivy Fails Because Command Is Missing

Install Trivy:

```bash
brew install trivy
```
