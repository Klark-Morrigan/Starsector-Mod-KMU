#!/usr/bin/env bash
# Regenerates the political map's sector fixture from a save, with no argument needed.
#
#   scripts/extract-sector-fixture-from-save.sh
#   scripts/extract-sector-fixture-from-save.sh "/c/.../saves/save_Name_123/campaign.xml"
#
# Given no path it takes the most recently written campaign.xml under the install's saves
# folder - the one just played - and prints which it picked rather than leaving that
# implicit. Being confidently sure about a sector that was not the one on screen is the
# exact failure this fixture exists to prevent, so the choice is always stated.
#
# Close Starsector first. The extractor refuses a save it cannot pair fully, but a file
# being rewritten underneath it is not worth the argument.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(dirname "${script_dir}")"

resolve_starsector_root() {
    # Same source of truth the build uses, in the same order: STARSECTOR_HOME wins, else
    # gradle.properties' pinned starsectorRoot. Resolving it any other way here would let
    # this script and the build disagree about which install they are talking about.
    if [[ -n "${STARSECTOR_HOME:-}" ]]; then
        printf '%s' "${STARSECTOR_HOME}"
        return
    fi
    local pinned
    pinned="$(sed -n 's/^starsectorRoot=//p' "${repo_dir}/gradle.properties" 2>/dev/null | tr -d '\r')"
    if [[ -z "${pinned}" ]]; then
        echo "Cannot locate Starsector: set STARSECTOR_HOME or pin starsectorRoot in" \
            "gradle.properties." >&2
        exit 2
    fi
    printf '%s' "${pinned}"
}

find_newest_save() {
    local saves="$1/saves"
    [[ -d "${saves}" ]] || { echo "No saves folder at ${saves}" >&2; exit 2; }
    # Newest by modification time. -printf keeps this to one pass rather than stat per file.
    local newest
    newest="$(find "${saves}" -mindepth 2 -maxdepth 2 -name campaign.xml -printf '%T@ %p\n' \
        2>/dev/null | sort -rn | head -1 | cut -d' ' -f2-)"
    [[ -n "${newest}" ]] || { echo "No campaign.xml under ${saves}" >&2; exit 2; }
    printf '%s' "${newest}"
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
    # The only argument is a save path, so a help flag would otherwise be handed to the
    # parser and fail as unreadable XML rather than as a bad flag.
    sed -n '2,13p' "${BASH_SOURCE[0]}"
    exit 0
fi

if [[ $# -gt 0 ]]; then
    save="$1"
else
    # Resolved in its own statement so a failure inside it surfaces as this script's exit
    # status instead of being masked by the surrounding substitution.
    starsector_root="$(resolve_starsector_root)"
    save="$(find_newest_save "${starsector_root}")"
    echo "No save given; using the most recently written one:"
    echo "  ${save}"
    echo
fi

cd "${repo_dir}"
python scripts/extract-sector-fixture-from-save.py "${save}"
