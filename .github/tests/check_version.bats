#!/usr/bin/env bats
# Unit tests for .github/scripts/check_version.sh.
#
# Requires bats-core: https://github.com/bats-core/bats-core
# Run from the repo root: bats .github/tests/check_version.bats
#
# External commands (jq, git) are stubbed by writing minimal shell scripts
# into a temporary directory that is prepended to PATH, so no real
# mod_info.json or git repository is needed.

SCRIPT="$BATS_TEST_DIRNAME/../scripts/check_version.sh"

setup() {
    STUBS_DIR="$(mktemp -d)"
    export GITHUB_OUTPUT
    GITHUB_OUTPUT="$(mktemp)"
    export PATH="$STUBS_DIR:$PATH"
}

teardown() {
    rm -rf "$STUBS_DIR"
    rm -f "$GITHUB_OUTPUT"
}

# Writes a jq stub that ignores its arguments and prints VERSION.
stub_jq() {
    local version="$1"
    printf '#!/bin/sh\necho "%s"\n' "$version" > "$STUBS_DIR/jq"
    chmod +x "$STUBS_DIR/jq"
}

# Writes a git stub. Passing "none" makes it exit 1 to simulate no tags,
# which causes the script's `|| echo "none"` fallback to fire.
stub_git() {
    local tag="$1"
    if [ "$tag" = "none" ]; then
        printf '#!/bin/sh\nexit 1\n' > "$STUBS_DIR/git"
    else
        printf '#!/bin/sh\necho "%s"\n' "$tag" > "$STUBS_DIR/git"
    fi
    chmod +x "$STUBS_DIR/git"
}

@test "version_updated=false when version matches latest tag" {
    stub_jq  "1.2.3"
    stub_git "1.2.3"
    run bash "$SCRIPT"
    [ "$status" -eq 0 ]
    grep -q "version_updated=false" "$GITHUB_OUTPUT"
}

@test "version_updated=true when version differs from latest tag" {
    stub_jq  "1.2.4"
    stub_git "1.2.3"
    run bash "$SCRIPT"
    [ "$status" -eq 0 ]
    grep -q "version_updated=true" "$GITHUB_OUTPUT"
}

@test "version_updated=true when no tags exist" {
    stub_jq  "1.0.0"
    stub_git "none"
    run bash "$SCRIPT"
    [ "$status" -eq 0 ]
    grep -q "version_updated=true" "$GITHUB_OUTPUT"
}

@test "version value is written to GITHUB_OUTPUT" {
    stub_jq  "2.5.0"
    stub_git "2.4.9"
    run bash "$SCRIPT"
    [ "$status" -eq 0 ]
    grep -q "version=2.5.0" "$GITHUB_OUTPUT"
}
