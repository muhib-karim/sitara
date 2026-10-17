# ADR 0001: Modular Compose app with tested pure-Kotlin cores

- Status: accepted (2026-10-17)

## Context
Sitara has to look and move like a premium app while staying easy to fix when the streaming side changes.
The owner wants showcase-grade engineering: tests first, CI-built APKs, generated screenshots.

## Decision
- Multi-module Gradle build: `:core:*` (model, common, data, player, designsystem), `:source:*` (api, fake, lyrics, later youtube/local) and `:feature:*` screens, assembled by `:app`.
- Logic that matters (queue, crossfade, loudness, sleep timer, lyrics parsing, palette, settings codec) is pure Kotlin with no Android types, unit-tested on the JVM.
- Screens are stateless composables that take state and callbacks, so Roborazzi renders them with sample data.
- One `SitaraMotion` object owns every spring and duration.
- Phase A wires dependencies by hand in `SitaraApplication` to keep the first build small; Hilt is adopted in Phase B when Room and real sources add enough graph to justify it.
- Plain per-module build scripts instead of convention plugins until the module count makes duplication hurt.

## Consequences
More modules mean longer cold builds, offset by Gradle caching and parallelism. Hand wiring is a deliberate, temporary trade-off.
