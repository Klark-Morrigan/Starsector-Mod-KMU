# Localization Provider

## Index

- [Problem](#problem)
- [Baseline Behavior](#baseline-behavior)
- [Initial Scope](#initial-scope)
- [Decisions](#decisions)
- [Tests](#tests)
- [Open Questions](#open-questions)
- [Risks](#risks)

## Problem

KMU is starting to accumulate player-facing UI text. Hardcoded Java strings make
that text difficult to translate, review, and override. Starsector exposes a
string lookup API through `Global.getSettings().getString(category, key)`, and
the base game has a `localeOverride` setting in
`starsector-core/data/config/settings.json`, but KMU does not yet have a
language-aware string provider.

Feature 002 should turn the current lightweight `KmuStrings` helper into a
proper localization provider that can select strings based on the active
Starsector locale and, later, a KMU-specific LunaLib override.

## Baseline Behavior

- Keep English strings in a default KMU locale file.
- Load and cache the default English locale regardless of the selected locale.
- Keep player-facing KMU UI text behind the `KmuStrings` helper and
  `data/strings/strings.json`.
- Detect the active Starsector locale from `Global.getSettings()`, starting with
  the vanilla `localeOverride` setting.
- Try to load a locale-specific KMU string file when one exists.
- Overlay selected-locale strings on top of the cached default English strings.
- Fall back to cached default English strings when selected-locale entries are
  missing.
- Treat missing default English strings as KMU defects, not as normal runtime
  fallback cases.
- Treat malformed localization files as startup/load errors that should be
  reported visibly to the player if Starsector exposes a safe game-load popup
  or dialog path; otherwise log the error and report it through the safest
  available in-game notification channel.
- Keep UI components dependent only on `KmuStrings`, not on JSON loading,
  Starsector settings plumbing, or LunaLib.

## Initial Scope

Feature 001 introduced the first KMU player-facing string surface. Feature 002
owns the localization requirements for that surface and any later KMU UI text.

Initial strings to keep behind localization keys:

- condition picker title;
- condition picker summary text;
- condition picker empty-state text;
- condition picker dialog button text.

## Decisions

- Use Starsector `localeOverride` as the default language selector.
- Treat runtime language changes as out of scope until evidence shows Starsector
  supports a reliable in-session locale switch event.
- Cache loaded string maps by locale. Always cache default English first, then
  cache the selected locale overlay when one exists. Do not read JSON from disk
  for every displayed string.
- Reloading can be explicit later if a LunaLib setting changes, but the first
  implementation should assume strings are loaded once per game/session.
- Add a later LunaLib setting as an override with values like `auto`, `en_US`,
  `ru_RU`, or `zh_CN`. `auto` should use Starsector `localeOverride`.
- Keep CJK handling separate from text lookup. Starsector's `cjkMode` affects
  font/line wrapping behavior, not which KMU string file to load.
- Do not keep Java literals as the localization fallback mechanism. Java should
  contain string keys, format arguments, and last-resort technical error
  identifiers only.

## Tests

- Unit test that selected-locale entries fall back to cached default English
  entries when missing.
- Unit test that missing default English entries produce explicit provider
  errors.
- Unit test that malformed localization files produce startup/load diagnostics
  instead of silently falling back to Java literals.
- Unit test locale selection from Starsector `localeOverride`.
- Unit test locale-specific file fallback to default English.
- Unit test cached lookup behavior so string reads do not repeatedly parse JSON
  during UI rendering.

## Open Questions

- Does Starsector expose a public event when `localeOverride` changes, or is the
  setting effectively startup-time only?
- Should KMU store translations as merged `data/strings/strings.json` entries,
  or use KMU-owned files such as `data/strings/kmu_strings_ru_RU.json` loaded by
  the provider?
- Should the provider support regional fallback, e.g. `pt_BR` -> `pt` ->
  default English?
- What is the safest Starsector API path for showing a localization load error
  popup during game load?

## Risks

- Starsector itself does not present an obvious in-game language selector in the
  inspected install. Depending on a runtime switch event may be incorrect.
- Loading strings from JSON on every UI render would be unnecessary churn and
  could create avoidable UI stalls. Cache strings instead.
- If the default English locale is missing or malformed, KMU may be unable to
  present polished player-facing error text. In that case, prefer a concise
  technical error identifier over silently using scattered Java fallback copy.
- A LunaLib dependency just for localization is not justified. LunaLib should be
  used only for optional user-facing settings once KMU already needs settings.
