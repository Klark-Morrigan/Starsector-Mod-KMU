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

Some development files also intentionally stay at the repository root because
their tools expect that location:

- `.gitignore` for git ignore rules;
- `README.md` for repository and release browsing;
- `build.gradle`, `settings.gradle`, `gradlew`, `gradlew.bat`, and `gradle/`
  for the Gradle root project and wrapper.

`build.gradle` and `settings.gradle` are now present so the repository can grow
into a standard Gradle-based Java build. JUnit 5 and AssertJ are configured
there for dependency-managed tests, and the Gradle wrapper is the primary local
and CI build entry point.

Both build paths should target Java 17, matching the bundled Starsector runtime
for the current installed game version.

`mod_info.json` declares `"jars": ["jars/KMU.jar"]` and
`"modPlugin": "kmu.KMU_ModPlugin"`. During local development, build the jar
before enabling KMU in Starsector.

## Runtime Payload

A published zip should contain a top-level `KMU` folder with only the files the
game or player-facing docs need:

- `mod_info.json`
- `README.md`, changelog, license, and other player-facing docs
- `data/`, `graphics/`, `sounds/`, or other runtime asset directories
- `jars/KMU.jar`

It should not include `src/`, `docs/dev/`, tests, `.git`, IDE files, compile
scratch directories, build-system caches, Gradle wrapper files, or Gradle build
files.

## Tests

Tests are first-class repository files and should not be shipped in the release
zip. Unit tests belong in `src/test/java`. Runtime smoke tests should be
documented beside the feature plan, because Starsector UI hooks still need
manual or in-game verification.

The build should compile `src/main/java` against `starfarer.api.jar` and any
declared helper-library jars, then run tests before producing `jars/KMU.jar`.
Tests are executed by Gradle on JUnit 5. Standard test output and failures
appear in the local terminal and CI job log, and Gradle also writes test
reports under `build/reports/tests/`.

Gradle is configured to resolve JUnit 5 and AssertJ from Maven Central and to
resolve `starfarer.api.jar` from a local Starsector install through
`STARSECTOR_HOME` or `-PstarsectorRoot=<path>`. This keeps the game jars out of
git while still allowing a normal Java test stack.

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
