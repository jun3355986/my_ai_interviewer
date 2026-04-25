# Admin API Smoke Test

This document records the manual smoke sequence for the admin API. It mirrors
`AdminApiSmokeTest`, but uses `curl` against a running admin service.

## Preconditions

- Admin service is running on `http://localhost:9010`.
- PostgreSQL has an enabled user with `ROLE_ADMIN`.
- The admin service is configured with the same database that stores `t_user`,
  `t_role`, and `t_user_role`.

## Login

Request:

```bash
curl -sS -X POST 'http://localhost:9010/admin/auth/login' \
  -H 'Content-Type: application/json' \
  -d '{
    "username": "admin",
    "password": "pass123456"
  }'
```

Expected response shape:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "accessToken": "<jwt>",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "admin": {
      "id": 1,
      "username": "admin"
    },
    "roles": ["ROLE_ADMIN"]
  },
  "timestamp": 1710000000000
}
```

Export the token:

```bash
export ADMIN_TOKEN='<jwt-from-login-response>'
```

## Smoke Curl Sequence

Every request must include the admin Bearer token:

```bash
curl -sS 'http://localhost:9010/admin/auth/me' \
  -H "Authorization: Bearer ${ADMIN_TOKEN}"

curl -sS 'http://localhost:9010/admin/dashboard/overview' \
  -H "Authorization: Bearer ${ADMIN_TOKEN}"

curl -sS 'http://localhost:9010/admin/users' \
  -H "Authorization: Bearer ${ADMIN_TOKEN}"

curl -sS 'http://localhost:9010/admin/jobs' \
  -H "Authorization: Bearer ${ADMIN_TOKEN}"

curl -sS 'http://localhost:9010/admin/interviews' \
  -H "Authorization: Bearer ${ADMIN_TOKEN}"

curl -sS 'http://localhost:9010/admin/questions' \
  -H "Authorization: Bearer ${ADMIN_TOKEN}"

curl -sS 'http://localhost:9010/admin/audit/logs' \
  -H "Authorization: Bearer ${ADMIN_TOKEN}"
```

Expected wrapper for each request:

```json
{
  "code": 200,
  "message": "success",
  "data": "<endpoint-specific object or page>",
  "timestamp": 1710000000000
}
```

List endpoints may return empty page records when the database has no matching
business data. That is acceptable for this smoke test.

## Automated Smoke Test

Run from `ai_interviewer_admin`:

```bash
JENV_ROOT="$HOME/.jenv" /opt/homebrew/bin/jenv exec mvn -Dtest=AdminApiSmokeTest test
```

The automated test uses Testcontainers PostgreSQL and MockMvc. It does not start
the real Docker Compose stack.
