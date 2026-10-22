#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
for tool in docker openssl curl; do
  command -v "$tool" >/dev/null || { echo "缺少 $tool，请先安装。" >&2; exit 1; }
done
# Do not change the user's Docker context or silently deploy to a remote engine.
endpoint="${DOCKER_HOST:-$(docker context inspect --format '{{.Endpoints.docker.Host}}')}"
case "$endpoint" in unix://*) ;; *) echo '演示脚本只允许本机 Unix socket Docker；请显式选择本机 context。' >&2; exit 1;; esac
version="$(docker compose version --short)"
version="${version#v}"
IFS=. read -r major minor patch <<< "$version"
if (( major < 2 || (major == 2 && minor < 24) || (major == 2 && minor == 24 && ${patch:-0} < 4) )); then
  echo '需要 Docker Compose 2.24.4+（ports !override）。' >&2; exit 1
fi
if [[ ! -f .env.demo ]]; then
  umask 077
  (set -o noclobber
   cat > .env.demo <<ENV
DB_USER=demo
DB_PASSWORD=$(openssl rand -hex 24)
APP_JWT_SECRET=$(openssl rand -hex 32)
RABBITMQ_USER=demo
RABBITMQ_PASSWORD=$(openssl rand -hex 24)
APP_CORS_ALLOWED_ORIGIN=http://localhost:8080
PUBLIC_PORT=8080
ALIYUN_AI_API_KEY=
ZHIPU_AI_API_KEY=
ENV
  )
fi
# Shell exports otherwise override --env-file interpolation, including production credentials.
unset DB_USER DB_PASSWORD DB_HOST DB_PORT DB_NAME APP_JWT_SECRET RABBITMQ_USER RABBITMQ_PASSWORD
unset ALIYUN_AI_API_KEY ZHIPU_AI_API_KEY APP_CORS_ALLOWED_ORIGIN PUBLIC_PORT APP_IMAGE
unset VITE_APP_BASE VITE_API_BASE_URL JAVA_OPTS SPRING_PROFILES_ACTIVE AI_ENABLED REPORT_BATCH_ENABLED
unset BOOTSTRAP_TEACHER_ENABLED BOOTSTRAP_TEACHER_USERNAME BOOTSTRAP_TEACHER_PASSWORD
compose=(docker compose -p zhixuewuli-demo --env-file .env.demo -f docker-compose.yml -f docker-compose.demo.yml)
docker info >/dev/null 2>&1 || { echo '本机 Docker daemon 未运行，请启动后重试。' >&2; exit 1; }
"${compose[@]}" config --quiet
"${compose[@]}" up -d --build
ready=false
for ((i=0; i<60; i++)); do
  health="$(curl --connect-timeout 1 --max-time 2 -fsS http://127.0.0.1:8080/api/v1/actuator/health 2>/dev/null | tr -d '[:space:]' || true)"
  if [[ "$health" == *'"status":"UP"'* ]]; then ready=true; break; fi
  sleep 1
done
if [[ "$ready" != true ]]; then
  "${compose[@]}" logs --tail=100 app >&2
  echo '应用未在启动等待期内健康。' >&2; exit 1
fi
if command -v python3 >/dev/null; then
  python3 - <<'PY'
import sys
sys.path.insert(0, 'scripts')
import seed_test_data as seed
teacher, _ = seed.login(*seed.TEACHER)
status, body = seed.req('GET', '/teacher/events', teacher)
if status != 200:
    raise SystemExit('无法检查现有任务，不执行种子写入。')
if not seed.data_of(body):
    if seed.main() != 0:
        raise SystemExit('种子初始化失败。')
else:
    print('已有任务，保留当前数据，不重复灌入。')
student, _ = seed.login('stu01', 'Student@2026')
status, body = seed.req('GET', '/teacher/events/student-tasks', student)
if status != 200 or not seed.data_of(body):
    raise SystemExit('学生任务验证失败；请检查种子日志，不要把当前环境标记为完整演示。')
print('教师/学生登录及学生任务验证通过。')
PY
  echo '学生：stu01 / Student@2026'
else
  echo '未检测到 python3：跳过种子数据；学生账号与演示作业尚未准备。安装 Python 3 后重跑。'
fi
echo '访问：http://localhost:8080（部署路由可能跳转至 /api/v1/）'
echo '教师：teacher / Teacher@2026；AI 默认关闭。只用于本机演示。'
echo '停止：docker compose -p zhixuewuli-demo --env-file .env.demo -f docker-compose.yml -f docker-compose.demo.yml down'
