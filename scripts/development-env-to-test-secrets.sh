#!/usr/bin/env bash
# Deprecated: merged into consolidated secrets under "development_env".
exec "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/merge-development-env-into-secrets.sh" "$@"
