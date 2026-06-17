# ZAP Scan Rules

Initial ZAP scans are baseline scans against local web entrypoints:

- User web: `USER_WEB_BASE_URL`, default `http://localhost:8088`
- Admin web: `ADMIN_WEB_BASE_URL`, default `http://localhost:8090`

Start with passive/baseline scans. Authenticated active scans should be added only after test accounts and allowed target scope are explicit.
