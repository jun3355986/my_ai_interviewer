#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
VECTOR_DB_DIR="${VECTOR_DB_DIR:-${REPO_ROOT}/ai_interviewer/storage/vector_db}"
BACKUP_ROOT="${BACKUP_ROOT:-${REPO_ROOT}/backups/vector-db}"

usage() {
  cat <<'USAGE'
Usage:
  scripts/manage-vector-db.sh init
  scripts/manage-vector-db.sh status
  scripts/manage-vector-db.sh backup [backup-dir]
  scripts/manage-vector-db.sh restore <archive.tgz>
  scripts/manage-vector-db.sh clear --yes
  scripts/manage-vector-db.sh rebuild --yes

Manages the Python AI ChromaDB runtime directory.

Environment:
  VECTOR_DB_DIR  Override vector DB directory.
  BACKUP_ROOT    Override backup output directory.

Notes:
  - ai_interviewer/storage/vector_db is runtime data and must not be committed.
  - rebuild clears local ChromaDB files; repopulate them by running the Admin
    question-bank sync flow after the Python AI service is back online.
USAGE
}

require_yes() {
  if [[ "${1:-}" != "--yes" ]]; then
    echo "Refusing destructive action without --yes." >&2
    exit 2
  fi
}

init_vector_db() {
  mkdir -p "${VECTOR_DB_DIR}"
  echo "Vector DB directory ready: ${VECTOR_DB_DIR}"
}

status_vector_db() {
  if [[ -d "${VECTOR_DB_DIR}" ]]; then
    local size file_count
    size="$(du -sh "${VECTOR_DB_DIR}" | awk '{print $1}')"
    file_count="$(find "${VECTOR_DB_DIR}" -type f | wc -l | tr -d ' ')"
    echo "Vector DB directory: ${VECTOR_DB_DIR}"
    echo "Size: ${size}"
    echo "Files: ${file_count}"
  else
    echo "Vector DB directory does not exist: ${VECTOR_DB_DIR}"
  fi

  local tracked
  tracked="$(git -C "${REPO_ROOT}" ls-files ai_interviewer/storage/vector_db || true)"
  if [[ -n "${tracked}" ]]; then
    echo
    echo "Tracked files that should be removed from Git index:"
    echo "${tracked}"
    exit 1
  fi
}

backup_vector_db() {
  local output_dir="${1:-${BACKUP_ROOT}}"
  init_vector_db >/dev/null
  mkdir -p "${output_dir}"

  local timestamp archive
  timestamp="$(date +%Y%m%d-%H%M%S)"
  archive="${output_dir}/vector-db-${timestamp}.tgz"

  tar -C "$(dirname "${VECTOR_DB_DIR}")" -czf "${archive}" "$(basename "${VECTOR_DB_DIR}")"
  echo "Backup written: ${archive}"
}

restore_vector_db() {
  local archive="${1:-}"
  if [[ -z "${archive}" ]]; then
    echo "Missing archive path." >&2
    usage
    exit 2
  fi
  if [[ ! -f "${archive}" ]]; then
    echo "Archive not found: ${archive}" >&2
    exit 1
  fi

  local parent
  parent="$(dirname "${VECTOR_DB_DIR}")"
  mkdir -p "${parent}"
  rm -rf "${VECTOR_DB_DIR}"
  tar -C "${parent}" -xzf "${archive}"
  echo "Vector DB restored from: ${archive}"
}

clear_vector_db() {
  require_yes "${1:-}"
  rm -rf "${VECTOR_DB_DIR}"
  init_vector_db
}

rebuild_vector_db() {
  require_yes "${1:-}"
  local backup_dir="${BACKUP_ROOT}/pre-rebuild"
  if [[ -d "${VECTOR_DB_DIR}" ]]; then
    backup_vector_db "${backup_dir}"
  fi
  clear_vector_db --yes
  cat <<EOF
Vector DB files have been cleared.

Next steps:
  1. Start Python AI and backend services.
  2. Trigger the Admin question-bank sync flow to repopulate ChromaDB.
EOF
}

main() {
  local command="${1:-}"
  shift || true

  case "${command}" in
    init) init_vector_db "$@" ;;
    status) status_vector_db "$@" ;;
    backup) backup_vector_db "$@" ;;
    restore) restore_vector_db "$@" ;;
    clear) clear_vector_db "$@" ;;
    rebuild) rebuild_vector_db "$@" ;;
    -h|--help|help|"") usage ;;
    *)
      echo "Unknown command: ${command}" >&2
      usage
      exit 2
      ;;
  esac
}

main "$@"
