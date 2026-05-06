#!/usr/bin/env bats
# Unit tests for .github/scripts/extract_changelog.sh.
#
# Requires bats-core: https://github.com/bats-core/bats-core
# Run from the repo root: bats .github/tests/extract_changelog.bats

SCRIPT="$BATS_TEST_DIRNAME/../scripts/extract_changelog.sh"

CHANGELOG_CONTENT='## [1.2.0]
First entry line.
Second entry line.

## [1.1.0]
Older entry.

## [1.0.0]
Initial release.
'

setup() {
    WORK_DIR="$(mktemp -d)"
    printf '%s' "$CHANGELOG_CONTENT" > "$WORK_DIR/CHANGELOG.md"
    cd "$WORK_DIR"
}

teardown() {
    rm -rf "$WORK_DIR"
}

@test "extracts body for a middle version" {
    run bash "$SCRIPT" "1.1.0"
    [ "$status" -eq 0 ]
    grep -q "Older entry." release-notes.md
    ! grep -q "## \[" release-notes.md
}

@test "extracts body for the first version" {
    run bash "$SCRIPT" "1.2.0"
    [ "$status" -eq 0 ]
    grep -q "First entry line." release-notes.md
    grep -q "Second entry line." release-notes.md
    ! grep -q "## \[" release-notes.md
}

@test "extracts body for the last version" {
    run bash "$SCRIPT" "1.0.0"
    [ "$status" -eq 0 ]
    grep -q "Initial release." release-notes.md
}

@test "exits 1 when version is not in changelog" {
    run bash "$SCRIPT" "9.9.9"
    [ "$status" -eq 1 ]
    echo "$output" | grep -q "no changelog section found"
}

@test "version argument is required" {
    run bash "$SCRIPT"
    [ "$status" -ne 0 ]
}
