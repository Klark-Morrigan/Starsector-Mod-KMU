#!/usr/bin/env bash
# Extracts the changelog section for a given version from CHANGELOG.md and
# writes it to release-notes.md in the current directory.
#
# Usage: extract_changelog.sh <version>
#
# Reads CHANGELOG.md from the current directory. Writes release-notes.md to
# the current directory. Exits 1 if the version section is not found.
set -euo pipefail

VERSION="${1:?version argument required}"

awk "/^## \[$VERSION\]/{found=1; next} \
     found && /^## \[/{exit} \
     found{print}" \
  CHANGELOG.md > release-notes.md

# Fail loudly if the section was missing rather than silently publishing an
# empty release body.
if [ ! -s release-notes.md ]; then
  echo "ERROR: no changelog section found for version $VERSION" >&2
  exit 1
fi
