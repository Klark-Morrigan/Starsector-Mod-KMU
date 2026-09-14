#!/usr/bin/env bash
# Runs this repo's Gradle build/test locally, mirroring the gradle job of
# ci.yml (which delegates to Common-Java's reusable ci-gradle workflow).
# Thin shim to Common-Java's run-ci-gradle.sh entry, pointed at this repo
# via COMMON_JAVA_TARGET_REPO. Pass tasks as arguments to override the
# default (test jar). Common-Java is expected as a sibling checkout under
# the same parent directory.
#
# The build resolves the Starsector install from STARSECTOR_HOME (or
# -PstarsectorRoot); set STARSECTOR_HOME in your environment first, the
# same way the CI runner does. If it is unset the build fails with a
# message naming the jar it could not find.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"
common_java_root="$(cd "${repo_root}/../Common-Java" && pwd)"

COMMON_JAVA_TARGET_REPO="${repo_root}" exec "${common_java_root}/scripts/run-ci-gradle.sh" "$@"
