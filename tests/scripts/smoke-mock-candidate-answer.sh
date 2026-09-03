#!/usr/bin/env bash
# 模拟面试（AI 代答）candidate-answer 链路冒烟
#
# 用法：
#   tests/scripts/smoke-mock-candidate-answer.sh              # 直连 Python
#   PYTHON_BASE_URL=http://127.0.0.1:8000 tests/scripts/smoke-mock-candidate-answer.sh
#
# 说明：
# - 该端点无状态、不落库，用于给 Flutter MockAutoDriver 生成候选人回答。
# - 需要 Python 服务已加载最新代码（AI_OPENAI_COMPAT_API_KEY 已配置）。
# - 通过网关的完整链路（Java 归属校验）需要登录 token，见 tests/docs/tooling-guide.md。

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=tests/scripts/lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"

load_test_env

PYTHON_BASE_URL="${PYTHON_BASE_URL:-http://127.0.0.1:8000}"

print_section "模拟面试 candidate-answer 冒烟（直连 Python: ${PYTHON_BASE_URL}）"

payload=$(cat <<'JSON'
{
  "question": "请介绍一下你最近一个后端项目里解决过的最难的问题",
  "question_type": "project",
  "resume_content": "姓名：张三\n工作经验：5年Java开发经验\n项目经验：电商订单系统，使用 Spring Boot + Redis + RabbitMQ，优化订单查询性能，解决分布式事务问题。",
  "job_requirements": "Java 高级开发工程师，3 年以上经验，熟悉 Spring Boot、Redis、消息队列",
  "candidate_name": "张三"
}
JSON
)

http_code=$(curl -s -o /tmp/mock_candidate_answer.json -w "%{http_code}" \
  -X POST "${PYTHON_BASE_URL}/interview/mock/candidate-answer" \
  -H "Content-Type: application/json" \
  -d "${payload}")

if [[ "${http_code}" != "200" ]]; then
  echo "❌ 请求失败 HTTP ${http_code}："
  cat /tmp/mock_candidate_answer.json
  exit 1
fi

answer=$(python3 -c "import json;print(json.load(open('/tmp/mock_candidate_answer.json'))['answer'])")
if [[ -z "${answer}" ]]; then
  echo "❌ 响应缺少非空 answer 字段"
  exit 1
fi

echo "✅ 生成成功，回答摘要：${answer:0:80}..."
echo "✅ 模拟面试 candidate-answer 冒烟通过"
