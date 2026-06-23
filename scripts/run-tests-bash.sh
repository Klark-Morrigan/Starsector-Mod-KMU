#!/usr/bin/env bash
# Runs the bash-test half of this repo's CI locally: every *.bats suite
# (reports 0 tests when none). Thin shim to Common-Automation's
# _run-tests-bash.sh, pointed at this repo via
# COMMON_AUTOMATION_TARGET_REPO. The lint half is
# run-lint-yaml-and-bash.sh; run-ci-yaml-and-bash.sh runs both.
# Common-Automation is expected as a sibling checkout under the same
# parent directory.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"
common_automation_root="$(cd "${repo_root}/../Common-Automation" && pwd)"

COMMON_AUTOMATION_TARGET_REPO="${repo_root}" exec "${common_automation_root}/scripts/_run-tests-bash.sh"
