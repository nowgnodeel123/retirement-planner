#!/bin/bash
# PreToolUse 훅: git commit 시도 시 기존 Flyway 마이그레이션 파일이
# "수정(M)"되었는지 검사. 신규 추가(A)는 허용, 기존 파일 수정은 차단.
# 지침 8.1: "적용된 마이그레이션 절대 수정 금지, 새 버전 파일로만 추가"
#
# 세션을 워크스페이스 루트(nest/)에서 열든, 백엔드 폴더(retirement-planner/)에서
# 직접 열든 항상 동일하게 작동하도록, shell의 현재 위치가 아니라
# "이 스크립트 파일 자신의 위치"를 기준으로 백엔드 레포 경로를 찾는다.

INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty')

# git commit 관련 명령이 아니면 통과
if [[ "$COMMAND" != *"git commit"* ]]; then
  exit 0
fi

# 이 스크립트는 항상 <백엔드레포>/.claude/hooks/ 안에 있다.
# 스크립트 위치에서 두 단계 위로 가면 백엔드 레포 루트.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

if [[ ! -d "$REPO_ROOT/.git" ]]; then
  # 레포 루트를 못 찾으면 안전하게 통과 (차단은 오탐보다 나쁠 게 없지만,
  # 스크립트 자체 결함으로 커밋이 막히는 것도 바람직하지 않음)
  exit 0
fi

CHANGED=$(git -C "$REPO_ROOT" diff --cached --name-status -- '*/db/migration/V*.sql' 2>/dev/null)

if [[ -z "$CHANGED" ]]; then
  exit 0
fi

# 상태가 M(수정)인 마이그레이션 파일이 있는지 검사
MODIFIED=$(echo "$CHANGED" | awk '$1 == "M" {print $2}')

if [[ -n "$MODIFIED" ]]; then
  echo "차단됨: 이미 적용된 Flyway 마이그레이션 파일이 수정 상태로 커밋에 포함되어 있습니다." >&2
  echo "수정하려던 파일:" >&2
  echo "$MODIFIED" >&2
  echo "" >&2
  echo "지침 8.1: 적용된 마이그레이션은 절대 수정 금지, 새 버전 파일(V{n}__...)로만 추가하세요." >&2
  exit 2
fi

exit 0
