# Klark Morrigan's Utilities

This is a small Starsector utility and quality of life mod.

## Index

- [Features](#features)
  - [Map layers](#map-layers)
    - [Political map](#political-map)
- [Dependencies](#dependencies)
- [For developers](#for-developers)
  - [Versioning](#versioning)
  - [Build And Release](#build-and-release)
  - [Local linting](#local-linting)
  - [Documentation Status](#documentation-status)

## Features

### Map layers

#### Political map

An on-map overlay that colours the sector by who controls each system. See
[the political map guide](src/main/java/kmu/maplayers/politicalmap/README.md) for the views it
offers and how the overlay is drawn.

## Dependencies

| Mod | Author | Required | Notes |
|-----|--------|----------|-------|
| Klark Morrigan's Library (KMLib) | Klark Morrigan | Required | Shared library; version pinned in `mod_info.json` |
| [LunaLib](https://fractalsoftworks.com/forum/index.php?topic=25658) | Lukas04 | Required | Settings framework backing KMU's configuration tabs |
| [LazyLib](https://fractalsoftworks.com/forum/index.php?topic=5444) | LazyWizard | Required | Utility library |
| [Console Commands](https://fractalsoftworks.com/forum/index.php?topic=4106) | LazyWizard | Optional | Enables `kmu_` developer commands (e.g. `kmu_mcm_open`) |
| [Nexerelin](https://fractalsoftworks.com/forum/index.php?topic=9175) | Histidine | Optional | Adds the alliances view to the political map |

## For developers

### Versioning

KMU follows the consumer-mod rules in
[KMLib's versioning policy](https://github.com/<owner>/KMLib/blob/main/docs/dev/versioning.md).
In short: MAJOR for save-breaking changes, MINOR for save-safe new features,
PATCH for fixes and tweaks. The same policy defines how KMU pins the KMLib
dependency in both `mod_info.json` and `.github/workflows/*.yml`.

### Build And Release

KMU is built with Gradle using the local Starsector install as a compile-only
API source. Production code targets Java 17 to match the bundled runtime.

Local build commands:

```powershell
.\gradlew.bat -PstarsectorRoot=C:\a_Games\Starsector test
.\gradlew.bat -PstarsectorRoot=C:\a_Games\Starsector jar
```

The Gradle wrapper is the supported local and CI build path. Pass the install
root via `STARSECTOR_HOME` or `-PstarsectorRoot=<path>`.

### Local linting

Two delegating CI workflows lint the repo's non-Gradle surface on every pull
request: [ci-yaml.yml](.github/workflows/ci-yaml.yml) calls Common-Automation's
reusable `ci-yaml.yml` (actionlint, action-validator, yamllint, ansible-lint)
and [ci-bash.yml](.github/workflows/ci-bash.yml) calls its reusable `ci-bash.yml`
(shellcheck, check-sh-executable, bats). Each step auto-skips when its surface is
absent. The Gradle build and tests are NOT part of these workflows - they run
through Gradle (see [Build And Release](#build-and-release)); these gates cover
only YAML / Actions / Bash.

KMU's workflows run on its self-hosted runner, labelled `kmu-runner` (provisioned
with `STARSECTOR_HOME` + JDK). [.github/actionlint.yaml](.github/actionlint.yaml)
declares that label so actionlint stops flagging `runs-on` as an unknown runner.

Three sibling shims reproduce that CI surface locally through Git Bash +
Docker, each delegating to Common-Automation's orchestrator:

- [scripts/run-ci-yaml-and-bash.sh](scripts/run-ci-yaml-and-bash.sh) (with the
  [run-ci-yaml-and-bash.bat](scripts/run-ci-yaml-and-bash.bat) launcher for
  `cmd` / PowerShell) is the MAIN entry - it runs BOTH the lint suite AND the
  bats tests in one go, the full local equivalent of ci-yaml.yml + ci-bash.yml.
  This is what most contributors run.
- [scripts/run-lint-yaml-and-bash.sh](scripts/run-lint-yaml-and-bash.sh) (with
  its [.bat](scripts/run-lint-yaml-and-bash.bat) launcher) runs the lint half
  only (shellcheck, actionlint, action-validator, yamllint, ansible-lint); no
  bats.
- [scripts/run-tests-bash.sh](scripts/run-tests-bash.sh) (with its
  [.bat](scripts/run-tests-bash.bat) launcher) runs the bats tests only.

All three are thin shims over Common-Automation's engine, so they require a
Common-Automation checkout as a SIBLING directory (`..\Common-Automation`). The
Gradle build and tests stay separate - they live in Gradle (see
[Build And Release](#build-and-release)); these shims cover only the YAML /
Actions / Bash surface.

[scripts/fix-permissions.sh](scripts/fix-permissions.sh) (and its
[.bat](scripts/fix-permissions.bat)) re-stages the executable bit on tracked
`*.sh` files, which Windows checkouts drop; run it after adding a shell script so
the `check-sh-executable` gate stays green.
[.gitattributes](.gitattributes) pins line endings surgically - `*.sh` and
`gradlew` to LF, `*.bat` and `gradlew.bat` to CRLF - and leaves binary / data
assets to git's own detection.

### Documentation Status

Development docs live under `docs/dev`. Package-level Mermaid diagrams live
beside the Java packages they describe under `src/main/java/kmu`, with shared
diagram conventions in `src/main/java/kmu/diagram-style.md`.
