# Build And Release Shape

## Index

- [Decision](#decision)
- [Repository Layout](#repository-layout)
- [Runtime Payload](#runtime-payload)
- [Tests](#tests)
- [Dependencies](#dependencies)
- [GitHub Actions](#github-actions)
- [Release Process](#release-process)

## Decision

KMU is developed in-place as a Starsector mod folder, but the repository should
not treat loose runtime scripts and generated output as the source of truth.
Production code should live under `src/main/java`, tests under `src/test/java`,
and the mod should load compiled code from `jars/KMU.jar`.

That keeps the fast local loop of having the repo at `mods/KMU`, while making a
clean boundary between source, tests, generated files, and the installable mod.

## Repository Layout

The intended layout is:

```text
KMU/
  mod_info.json
  README.md
  build.gradle
  settings.gradle
  gradlew
  gradlew.bat
  gradle/
  docs/
  src/
    main/java/
    test/java/
  data/
  graphics/
  sounds/
  jars/
    KMU.jar
  build/
  dist/
```

`data`, `graphics`, `sounds`, and `mod_info.json` are runtime assets and may
stay at the mod root because Starsector expects that shape. Java source should
not be placed in `data/scripts` unless we deliberately choose loose scripts for
a one-off experiment.

Once the first compiled code exists, `mod_info.json` should include
`"jars": ["jars/KMU.jar"]`. Until that jar is produced, leaving the field out
keeps the docs-only scaffold load-safe.

## Runtime Payload

A published zip should contain a top-level `KMU` folder with only the files the
game or player-facing docs need:

- `mod_info.json`
- `README.md`, changelog, license, and other player-facing docs
- `data/`, `graphics/`, `sounds/`, or other runtime asset directories
- `jars/KMU.jar`

It should not include `src/`, `docs/dev/`, tests, `.git`, IDE files, compile
scratch directories, or build-system caches.

## Tests

Tests are first-class repository files and should not be shipped in the release
zip. Unit tests belong in `src/test/java`. Runtime smoke tests should be
documented beside the feature plan, because Starsector UI hooks still need
manual or in-game verification.

The build should compile `src/main/java` against `starfarer.api.jar` and any
declared helper-library jars, then run tests before producing `jars/KMU.jar`.

## Dependencies

Helper libraries are allowed when they reduce implementation risk. External
Starsector library mods should be declared in `mod_info.json` dependencies and
listed in player-facing release notes. Do not bundle third-party mod jars unless
the dependency is designed for bundling and its license permits it.

## GitHub Actions

Release packaging should be automated with GitHub Actions once the build exists.
The workflow should support manual dispatch and version tags.

The action should:

- set up the Java toolchain;
- provide compile-only Starsector API inputs without committing game jars;
- compile `src/main/java` into `jars/KMU.jar`;
- run tests;
- assemble `dist/KMU` from runtime payload files;
- zip the folder as `KMU-<version>.zip`;
- upload the zip as a workflow artifact;
- attach the zip to a GitHub release when triggered by a version tag.

The workflow is allowed to package files from the repository, but it should not
publish source, tests, `docs/dev`, `.git`, IDE metadata, or build caches.

## Release Process

1. Update `mod_info.json` version and dependency metadata.
2. Compile the production jar and run tests.
3. Smoke test the built mod in Starsector.
4. Assemble a clean `dist/KMU` folder from runtime payload files.
5. Zip it as `KMU-<version>.zip`, with `KMU/` as the zip's top-level folder.
6. Install-test the zip by extracting it into a clean Starsector `mods`
   directory.
7. Publish the zip through the chosen channel, such as a forum post or GitHub
   release, with dependencies and tested game version called out.
