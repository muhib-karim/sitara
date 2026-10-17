# ADR 0003: Stream sources behind interfaces with health checks and fallback

- Status: accepted (2026-10-17)

## Context
Every client in this space breaks when YouTube changes clients, ciphers or tokens. Fixes need to be small and fast.

## Decision
`:source:api` defines `MusicSource` (browse/search/library), `StreamResolver` (track to playable URL), `LyricsProvider`
and `HealthCheck`. `FallbackStreamResolver` tries resolvers in order and reports every failure. Phase A ships a
`FakeMusicSource` (offline catalogue + royalty-free demo streams); Phase B adds NewPipeExtractor and InnerTube
resolvers in `:source:youtube`, each with a health probe that feeds a user-visible source-health screen.

## Consequences
A breaking upstream change is a one-module fix. UI, player and tests run without the network. The cost is a small
mapping layer between each library's models and `:core:model`.
