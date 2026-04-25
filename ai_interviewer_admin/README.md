# AI Interviewer Admin

Independent Spring Boot admin service for the AI Interviewer monorepo.

## Overview

- Service name: `ai-interviewer-admin`
- Default port: `9010`
- Java: `21`
- Spring Boot: `3.3.5`
- Service discovery/config: Nacos, disabled by default for local startup and tests
- Database: PostgreSQL, using environment defaults compatible with the existing Docker Compose setup
- Cache: Redis, using environment defaults compatible with the existing Docker Compose setup

## Local Environment

```bash
DB_HOST=localhost
DB_PORT=5433
DB_NAME=ai_interviewer
DB_USERNAME=postgres
DB_PASSWORD=postgres
REDIS_HOST=localhost
REDIS_PORT=6380
NACOS_ENABLED=false
NACOS_SERVER_ADDR=localhost:8848
```

## Run

```bash
mvn spring-boot:run
```

Enable Nacos when a local or remote Nacos instance is available:

```bash
NACOS_ENABLED=true mvn spring-boot:run
```

## Test

This project is independent and does not currently include a Maven wrapper.

```bash
mvn test
```

The bootstrap context test disables external database, Flyway, MyBatis-Plus, and Nacos auto-configuration so it can run without PostgreSQL, Redis, or Nacos.
