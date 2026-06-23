#!/usr/bin/env bash
# One-time per-clone setup: wires this repo's .githooks/ into git (via
# core.hooksPath) so the pre-commit auto-+x hook fires on commit. Without
# it, .git/hooks/ stays empty and the hook does nothing.
#
# Thin shim to Common-Automation's setup-hooks engine, pointed at this repo
# via COMMON_AUTOMATION_TARGET_REPO - the same single-source reuse as the
# other scripts/ shims. Common-Automation is expected as a sibling checkout
# under the same parent directory.

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"
common_automation_root="$(cd "${repo_root}/../Common-Automation" && pwd)"

COMMON_AUTOMATION_TARGET_REPO="${repo_root}" exec "${common_automation_root}/scripts/setup-hooks.sh"
