#!/usr/bin/env bash
# Local equivalent of this repo's ci-yaml.yml + ci-bash.yml workflows:
# runs the full lint suite and the bats tests, then reports a combined
# pass/fail. Thin shim to Common-Automation's run-ci-yaml-and-bash.sh
# orchestrator, pointed at this repo via COMMON_AUTOMATION_TARGET_REPO.
# To run a single half, see run-lint-yaml-and-bash.sh / run-tests-bash.sh.
# Common-Automation is expected as a sibling checkout under the same
# parent directory.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"
common_automation_root="$(cd "${repo_root}/../Common-Automation" && pwd)"

COMMON_AUTOMATION_TARGET_REPO="${repo_root}" exec "${common_automation_root}/scripts/run-ci-yaml-and-bash.sh"
