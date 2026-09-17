#!/usr/bin/env bash
# Trims trailing whitespace from this repo's tracked Markdown and Gradle
# scripts. The JVM-aware runner lives in Common-Java, which is what knows
# .gradle is a text type; this shim only points it at this repo via
# COMMON_JAVA_TARGET_REPO so neither the type set nor the engine path is
# duplicated here. Both Common-Java and Common-Automation are expected as
# sibling checkouts under the same parent directory.

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"
common_java_root="$(cd "${repo_root}/../Common-Java" && pwd)"

COMMON_JAVA_TARGET_REPO="${repo_root}" exec "${common_java_root}/scripts/fix-whitespace.sh"
