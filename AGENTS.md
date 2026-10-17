# AGENTS.md — project rules for coding agents (Codex, Claude Code, others)

## Project
- What it is: Sitara, an independent, open-source Android music player with its own original visual identity ("split-star / orbit").
- Stack: Kotlin 2.4, Jetpack Compose, Material 3, Media3; Gradle (version catalog in `gradle/libs.versions.toml`).
- Entry points: `app/` (application id `app.sitara.music`), `core/*` (design system, data, playback), `feature/*` (home, library, player, search, settings).

## Commands
- Build: `gradle :app:assembleDebug` — runs on GitHub Actions only (`.github/workflows/ci.yml`); do not run Gradle locally.
- Test: `gradle test --continue` and `gradle :app:verifyRoborazziDebug` (screenshot baselines), both in CI.
- Device screenshots: `.github/workflows/device-screens.yml` (API 34 emulator), artifact uploaded per run.
- Release: tag `vX.Y.Z` triggers `.github/workflows/release.yml` (signed APK attached to the GitHub Release).

## Rules
- Tests first; CI green before any merge or tag. Never force-push.
- Run a secret scan (gitleaks) before every push. No secrets, tokens or machine paths in the repo.
- Keep changes small and committed: one working step = one commit, so a killed session loses one step at most.
- Original code, artwork and logo only. Other apps (including other GPL/AGPL music clients) are studied for ideas, never copied.
- Licence: GPL-3.0 (see LICENSE and NOTICE).

## Memory
- `docs/PROJECT_BRAIN.md` is this repo's persistent memory. Read it first. Update Status / Next step after each meaningful step, and append a Session log line before you stop. Commit it with your work.
- Decisions that should outlive this repo go to the `memory` MCP tool, named by project.
