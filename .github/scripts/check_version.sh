#!/usr/bin/env bash
# Compares the version in mod_info.json to the latest git tag and writes
# two outputs to $GITHUB_OUTPUT:
#   version         - the value from mod_info.json
#   version_updated - "true" if version differs from the latest tag,
#                     "false" otherwise (including when no tags exist and
#                     the version happens to equal the "none" sentinel -
#                     an impossible case in practice)
set -euo pipefail

VERSION=$(jq -r .version mod_info.json)
LATEST_TAG=$(git describe --tags --abbrev=0 2>/dev/null || echo "none")

echo "version=$VERSION" >> "$GITHUB_OUTPUT"

if [ "$VERSION" = "$LATEST_TAG" ]; then
  echo "version_updated=false" >> "$GITHUB_OUTPUT"
else
  echo "version_updated=true" >> "$GITHUB_OUTPUT"
fi
