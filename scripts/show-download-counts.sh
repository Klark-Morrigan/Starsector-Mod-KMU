#!/usr/bin/env bash
# Shows the download counts and badge figures KMU's scheduled badge run would
# publish, publishing nothing. Thin shim to KMLib's show-download-counts.sh,
# handed this repo's root: KMLib owns how a mod's badge inputs are derived.
# KMLib is expected as a sibling checkout under the same parent directory, and
# Common-Automation beside it.
#
# Needs gh on PATH, logged in with gh auth login.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"
kmlib_root="$(cd "${repo_root}/../Starsector-Mod-KMLib" && pwd)"

exec "${kmlib_root}/scripts/show-download-counts.sh" "${repo_root}"
