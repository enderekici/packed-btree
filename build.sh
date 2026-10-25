#!/bin/bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")" && pwd)"
if [ "$#" -eq 0 ]; then
  set -- clean verify
fi
exec "$project_dir/mvnw" --batch-mode --no-transfer-progress "$@"
