# Changelog

All notable changes to KMU are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [0.1.0]

### Added

- Release automation: pushing a version bump to `master` builds, tags, and
  publishes the mod zip through KMLib's reusable `mod-release` pipeline,
  pinned in [release.yml](.github/workflows/release.yml) alongside the
  matching `kmlib` dependency version in `mod_info.json`.
