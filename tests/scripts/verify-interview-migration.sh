#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
BACKUP_PATH="${INTERVIEW_BACKUP_PATH:-}"
ANCHOR_ID="${INTERVIEW_LEGACY_ANCHOR_ID:-ef3d58eb84c74358a4b55dd09ff635b2}"
EXPECTED_ANCHOR_MESSAGES="${INTERVIEW_EXPECTED_ANCHOR_MESSAGE_COUNT:-6}"
EXPECTED_FLYWAY_VERSION="${INTERVIEW_EXPECTED_FLYWAY_VERSION:-6}"
POSTGRES_IMAGE="${INTERVIEW_MIGRATION_POSTGRES_IMAGE:-postgres:16-alpine}"
FLYWAY_IMAGE="${INTERVIEW_MIGRATION_FLYWAY_IMAGE:-flyway/flyway:10.17-alpine}"
FLYWAY_PLATFORM="${INTERVIEW_MIGRATION_FLYWAY_PLATFORM:-linux/amd64}"
MIGRATIONS_DIR="${ROOT_DIR}/ai_interview_backend/ai-interviewer-interview/src/main/resources/db/migration"

if [[ -z "${BACKUP_PATH}" ]]; then
  echo "INTERVIEW_BACKUP_PATH is required" >&2
  exit 2
fi
if [[ ! -f "${BACKUP_PATH}" ]]; then
  echo "Interview backup does not exist: ${BACKUP_PATH}" >&2
  exit 2
fi
if [[ ! -d "${MIGRATIONS_DIR}" ]]; then
  echo "Interview migration directory does not exist: ${MIGRATIONS_DIR}" >&2
  exit 2
fi
if [[ ! "${ANCHOR_ID}" =~ ^[[:alnum:]_-]{1,100}$ ]]; then
  echo "INTERVIEW_LEGACY_ANCHOR_ID must be 1-100 letters, numbers, underscores, or hyphens" >&2
  exit 2
fi
if [[ ! "${EXPECTED_ANCHOR_MESSAGES}" =~ ^[0-9]+$ ]]; then
  echo "INTERVIEW_EXPECTED_ANCHOR_MESSAGE_COUNT must be a non-negative integer" >&2
  exit 2
fi
if [[ ! "${EXPECTED_FLYWAY_VERSION}" =~ ^[0-9]+$ ]]; then
  echo "INTERVIEW_EXPECTED_FLYWAY_VERSION must be a non-negative integer" >&2
  exit 2
fi

LATEST_AVAILABLE_VERSION="$(find "${MIGRATIONS_DIR}" -maxdepth 1 -type f \
  -name 'V*__*.sql' -print \
  | sed -E 's#^.*/V([0-9]+)__.*#\1#' \
  | sort -n \
  | tail -n 1)"
if [[ -z "${LATEST_AVAILABLE_VERSION}" ]]; then
  echo "No versioned Interview migrations were found in ${MIGRATIONS_DIR}" >&2
  exit 2
fi
if [[ "${LATEST_AVAILABLE_VERSION}" != "${EXPECTED_FLYWAY_VERSION}" ]]; then
  echo "Latest available Interview migration is V${LATEST_AVAILABLE_VERSION}, expected V${EXPECTED_FLYWAY_VERSION}" >&2
  exit 1
fi

RUN_ID="interview-migration-verify-$$-${RANDOM}"
NETWORK_NAME="${RUN_ID}-net"
DB_CONTAINER="${RUN_ID}-db"

cleanup() {
  docker rm -f "${DB_CONTAINER}" >/dev/null 2>&1 || true
  docker network rm "${NETWORK_NAME}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker network create "${NETWORK_NAME}" >/dev/null
docker run -d --name "${DB_CONTAINER}" \
  --network "${NETWORK_NAME}" --network-alias db \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=ai_interviewer \
  -v "${BACKUP_PATH}:/backup/ai_interviewer.dump:ro" \
  "${POSTGRES_IMAGE}" >/dev/null

for _ in $(seq 1 80); do
  if docker exec "${DB_CONTAINER}" \
      psql -U postgres -d ai_interviewer -At -c "SELECT 1" >/dev/null 2>&1; then
    break
  fi
  sleep 0.25
done
docker exec "${DB_CONTAINER}" \
  psql -U postgres -d ai_interviewer -At -c "SELECT 1" >/dev/null

docker exec "${DB_CONTAINER}" pg_restore \
  --no-owner --no-privileges \
  -U postgres -d ai_interviewer /backup/ai_interviewer.dump

query() {
  docker exec "${DB_CONTAINER}" \
    psql -v ON_ERROR_STOP=1 -U postgres -d ai_interviewer -At -F '|' -c "$1"
}

BUSINESS_COUNTS_SQL="SELECT
  (SELECT count(*) FROM t_interview_session),
  (SELECT count(*) FROM t_interview_message),
  (SELECT count(*) FROM t_score_record),
  (SELECT count(*) FROM t_evaluation);"
BUSINESS_DIGEST_SQL="SELECT
  (SELECT md5(COALESCE(string_agg(
      (to_jsonb(session_row) - ARRAY[
        'lineage_id', 'parent_session_id', 'fork_point_message_id',
        'fork_trigger_message_id', 'branch_label', 'branch_version',
        'last_business_activity_at', 'legacy_migrated'
      ]::text[])::text,
      E'\\n' ORDER BY session_row.id), ''))
     FROM t_interview_session session_row),
  (SELECT md5(COALESCE(string_agg(
      (to_jsonb(message_row) - ARRAY[
        'turn_id', 'message_type', 'expects_response',
        'delivery_status', 'metadata'
      ]::text[])::text,
      E'\\n' ORDER BY message_row.id), ''))
     FROM t_interview_message message_row),
  (SELECT md5(COALESCE(string_agg(
      (to_jsonb(score_row) - ARRAY[
        'turn_id', 'question_message_id', 'answer_message_id'
      ]::text[])::text,
      E'\\n' ORDER BY score_row.id), ''))
     FROM t_score_record score_row),
  (SELECT md5(COALESCE(string_agg(
      to_jsonb(evaluation_row)::text,
      E'\\n' ORDER BY evaluation_row.id), ''))
     FROM t_evaluation evaluation_row);"
SCHEMA_DIGEST_SQL="WITH definitions AS (
  SELECT 'column|' || table_name || '|' || ordinal_position || '|' || column_name
      || '|' || data_type || '|' || COALESCE(udt_name, '')
      || '|' || is_nullable || '|' || COALESCE(column_default, '') AS definition
  FROM information_schema.columns
  WHERE table_schema = 'public'
    AND table_name IN (
      't_interview_session', 't_interview_message', 't_score_record',
      't_evaluation', 't_interview_lineage', 't_interview_turn_attempt'
    )
  UNION ALL
  SELECT 'constraint|' || relation.relname || '|' || constraint_row.conname
      || '|' || pg_get_constraintdef(constraint_row.oid)
  FROM pg_constraint constraint_row
  JOIN pg_class relation ON relation.oid = constraint_row.conrelid
  JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
  WHERE namespace.nspname = 'public'
    AND relation.relname IN (
      't_interview_session', 't_interview_message', 't_score_record',
      't_evaluation', 't_interview_lineage', 't_interview_turn_attempt'
    )
  UNION ALL
  SELECT 'index|' || tablename || '|' || indexname || '|' || indexdef
  FROM pg_indexes
  WHERE schemaname = 'public'
    AND tablename IN (
      't_interview_session', 't_interview_message', 't_score_record',
      't_evaluation', 't_interview_lineage', 't_interview_turn_attempt'
    )
)
SELECT md5(COALESCE(string_agg(definition, E'\\n' ORDER BY definition), ''))
FROM definitions;"
HISTORY_STATE_SQL="SELECT
  count(*),
  max(version::integer),
  md5(COALESCE(string_agg(
    installed_rank || '|' || COALESCE(version, '') || '|' || description
      || '|' || type || '|' || COALESCE(checksum::text, '') || '|' || success::text,
    E'\\n' ORDER BY installed_rank), ''))
FROM flyway_interview_schema_history;"
COUNTS_BEFORE="$(query "${BUSINESS_COUNTS_SQL}")"
DIGEST_BEFORE="$(query "${BUSINESS_DIGEST_SQL}")"

run_flyway() {
  docker run --rm --platform "${FLYWAY_PLATFORM}" \
    --network "${NETWORK_NAME}" \
    -v "${MIGRATIONS_DIR}:/flyway/sql:ro" \
    "${FLYWAY_IMAGE}" \
    -url=jdbc:postgresql://db:5432/ai_interviewer \
    -user=postgres \
    -password=postgres \
    -locations=filesystem:/flyway/sql \
    -table=flyway_interview_schema_history \
    -baselineOnMigrate=true \
    -baselineVersion=0 \
    -validateOnMigrate=true \
    migrate
}

run_flyway

COUNTS_AFTER="$(query "${BUSINESS_COUNTS_SQL}")"
if [[ "${COUNTS_AFTER}" != "${COUNTS_BEFORE}" ]]; then
  echo "Business row counts changed: before=${COUNTS_BEFORE}, after=${COUNTS_AFTER}" >&2
  exit 1
fi
DIGEST_AFTER="$(query "${BUSINESS_DIGEST_SQL}")"
if [[ "${DIGEST_AFTER}" != "${DIGEST_BEFORE}" ]]; then
  echo "Legacy business content changed: before=${DIGEST_BEFORE}, after=${DIGEST_AFTER}" >&2
  exit 1
fi

LINEAGE_COUNTS="$(query "SELECT
  (SELECT count(*) FROM t_interview_session),
  (SELECT count(*) FROM t_interview_lineage);"
)"
LINEAGE_SESSIONS="${LINEAGE_COUNTS%%|*}"
LINEAGE_ROOTS="${LINEAGE_COUNTS##*|}"
if [[ "${LINEAGE_SESSIONS}" != "${LINEAGE_ROOTS}" ]]; then
  echo "Expected one root Lineage per legacy Session: ${LINEAGE_COUNTS}" >&2
  exit 1
fi

INTEGRITY_COUNTS="$(query "SELECT
  (SELECT count(*) FROM t_interview_session s
     LEFT JOIN t_interview_lineage l ON l.id = s.lineage_id
    WHERE l.id IS NULL),
  (SELECT count(*) FROM t_interview_lineage l
     LEFT JOIN t_interview_session s ON s.id = l.root_session_id
    WHERE s.id IS NULL),
  (SELECT count(*) FROM t_interview_session s
     LEFT JOIN t_interview_session p ON p.id = s.parent_session_id
    WHERE s.parent_session_id IS NOT NULL AND p.id IS NULL),
  (SELECT count(*) FROM t_interview_message m
     LEFT JOIN t_interview_session s ON s.id = m.session_id
    WHERE s.id IS NULL),
  (SELECT count(*) FROM t_score_record score
     LEFT JOIN t_interview_session s ON s.id = score.session_id
    WHERE s.id IS NULL),
  (SELECT count(*) FROM t_interview_turn_attempt attempt
     LEFT JOIN t_interview_session s ON s.id = attempt.session_id
     LEFT JOIN t_interview_lineage l ON l.id = attempt.lineage_id
    WHERE s.id IS NULL OR l.id IS NULL);"
)"
if [[ "${INTEGRITY_COUNTS}" != "0|0|0|0|0|0" ]]; then
  echo "Interview migration reference integrity failed: ${INTEGRITY_COUNTS}" >&2
  exit 1
fi

ANCHOR_STATE="$(query "SELECT
  count(message.id),
  count(message.id) FILTER (
    WHERE message.delivery_status = 'completed'
      AND message.metadata ->> 'legacyForkEligible' = 'false'
  )
FROM t_interview_session session
JOIN t_interview_lineage lineage
  ON lineage.id = session.lineage_id
 AND lineage.root_session_id = session.id
LEFT JOIN t_interview_message message ON message.session_id = session.id
WHERE session.id = '${ANCHOR_ID}'
  AND session.lineage_id = session.id
  AND session.branch_version = 1
  AND session.legacy_migrated = TRUE;"
)"
if [[ "${ANCHOR_STATE}" != "${EXPECTED_ANCHOR_MESSAGES}|${EXPECTED_ANCHOR_MESSAGES}" ]]; then
  echo "Legacy anchor is missing, incomplete, or forkable: ${ANCHOR_STATE}" >&2
  exit 1
fi

HISTORY_STATE_AFTER="$(query "${HISTORY_STATE_SQL}")"
HISTORY_VERSION="$(printf '%s' "${HISTORY_STATE_AFTER}" | cut -d '|' -f 2)"
if [[ "${HISTORY_VERSION}" != "${EXPECTED_FLYWAY_VERSION}" ]]; then
  echo "Migrated Interview Flyway version is V${HISTORY_VERSION}, expected V${EXPECTED_FLYWAY_VERSION}" >&2
  exit 1
fi
SCHEMA_DIGEST_AFTER="$(query "${SCHEMA_DIGEST_SQL}")"

run_flyway

COUNTS_AFTER_RERUN="$(query "${BUSINESS_COUNTS_SQL}")"
DIGEST_AFTER_RERUN="$(query "${BUSINESS_DIGEST_SQL}")"
SCHEMA_DIGEST_AFTER_RERUN="$(query "${SCHEMA_DIGEST_SQL}")"
HISTORY_STATE_AFTER_RERUN="$(query "${HISTORY_STATE_SQL}")"
if [[ "${COUNTS_AFTER_RERUN}" != "${COUNTS_AFTER}" ]]; then
  echo "Business row counts changed on Flyway rerun: before=${COUNTS_AFTER}, after=${COUNTS_AFTER_RERUN}" >&2
  exit 1
fi
if [[ "${DIGEST_AFTER_RERUN}" != "${DIGEST_AFTER}" ]]; then
  echo "Business content changed on Flyway rerun: before=${DIGEST_AFTER}, after=${DIGEST_AFTER_RERUN}" >&2
  exit 1
fi
if [[ "${SCHEMA_DIGEST_AFTER_RERUN}" != "${SCHEMA_DIGEST_AFTER}" ]]; then
  echo "Interview schema changed on Flyway rerun: before=${SCHEMA_DIGEST_AFTER}, after=${SCHEMA_DIGEST_AFTER_RERUN}" >&2
  exit 1
fi
if [[ "${HISTORY_STATE_AFTER_RERUN}" != "${HISTORY_STATE_AFTER}" ]]; then
  echo "Interview Flyway history changed on no-op rerun: before=${HISTORY_STATE_AFTER}, after=${HISTORY_STATE_AFTER_RERUN}" >&2
  exit 1
fi

echo "Interview migration verification passed"
echo "business_counts=${COUNTS_AFTER}"
echo "business_digest=${DIGEST_AFTER}"
echo "lineage_counts=${LINEAGE_COUNTS}"
echo "integrity_counts=${INTEGRITY_COUNTS}"
echo "anchor_state=${ANCHOR_STATE}"
echo "flyway_version=${HISTORY_VERSION}"
echo "schema_digest=${SCHEMA_DIGEST_AFTER}"
echo "flyway_history=${HISTORY_STATE_AFTER}"
