#!/usr/bin/env bash
# Re-stages +x on tracked *.sh, the gradlew wrappers, and .githooks scripts in
# this repo missing it. The widened (Java/Gradle) fix runner lives in
# Common-Java; this shim only points it at this repo via COMMON_JAVA_TARGET_REPO
# so the pathspec set is not duplicated here. Both Common-Java and
# Common-Automation are expected as sibling checkouts under the same parent
# directory.

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"
common_java_root="$(cd "${repo_root}/../Common-Java" && pwd)"

COMMON_JAVA_TARGET_REPO="${repo_root}" exec "${common_java_root}/scripts/fix-permissions.sh"
