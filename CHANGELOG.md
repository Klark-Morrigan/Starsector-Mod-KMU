# Changelog

All notable changes to KMU are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [0.1.0]

### Added

- Baseline implementation of **Market Condition Manager (MCM)**
  - **MCM** can be opened via the `kmu_mcm_open` console command *(only when there's a valid market present)*. Not available if **Console Commands** mod is not enabled.
  - Basic location info is displayed.
  - Condition status counters are displayed.
  - All loaded market conditions *(vanilla and modded)* can be added. **No checks or guardrails**.
  - Present market conditions render vanilla tooltips.
  - Not present market conditions render Codex tooltips.
  - All market condition tooltips are appended by condition metadata.
