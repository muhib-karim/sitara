# ADR 0002: GPL-3.0 licence

- Status: accepted (2026-10-17)

## Context
The practical YouTube extraction library (NewPipeExtractor) is GPL-3.0. Linking it makes the app a derivative work.
The project is meant to be published as an open-source showcase.

## Decision
Sitara is licensed GPL-3.0-only (full text in `LICENSE`). All code is original; no code, names, logos, icons or screenshots
from other clients are used. Third-party libraries are used unmodified with attribution. Sample content is invented and
covers are generated in code.

## Consequences
Anyone distributing Sitara must ship source. Apache/MIT libraries remain compatible. Proprietary SDKs (for example
closed analytics) are excluded, which also keeps the build reproducible and fully FOSS.
