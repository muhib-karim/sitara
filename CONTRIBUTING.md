# Contributing to Sitara

Thank you for helping make Sitara better. Bug reports, design feedback, documentation, translations and code are all welcome.

By taking part you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Ways to help

- **Report a bug** with the [bug report form](https://github.com/muhib-karim/sitara/issues/new?template=bug_report.yml). Include your device, Android version and Sitara version.
- **Suggest a feature** with the [feature request form](https://github.com/muhib-karim/sitara/issues/new?template=feature_request.yml). Check the [roadmap](README.md#roadmap) first; it may already be planned for a later phase.
- **Ask a question or share an idea** in [Discussions](https://github.com/muhib-karim/sitara/discussions).
- **Report a security issue** privately, as described in [SECURITY.md](SECURITY.md). Please do not open a public issue.

## Development setup

Requirements:

- JDK 21
- Gradle 9.8 on your `PATH` (no wrapper JAR is committed)
- Android SDK with compile SDK 36
- A device or emulator on Android 8.0 (API 26) or newer for manual testing

```sh
git clone https://github.com/muhib-karim/sitara.git
cd sitara
gradle assembleDebug
```

Before opening a pull request, run:

```sh
gradle testDebugUnitTest        # unit tests
gradle verifyRoborazziDebug     # screenshot tests
gradle assembleDebug            # the app builds
```

CI also runs ktlint. If your change alters the UI on purpose, re-record screenshots with `gradle recordRoborazziDebug` and commit the updated images, or ask a maintainer to run the manual screenshots workflow.

## Project layout

Read [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the module map and data flow, and [docs/DESIGN.md](docs/DESIGN.md) for motion, colour, type and accessibility rules. In short:

- Pure logic goes in `:core:model`, `:core:common`, `:source:*` or the pure parts of `:core:player`, with no Android types.
- UI goes in a `:feature:*` module and takes every colour, spring, duration and shape from `:core:designsystem`.
- Feature modules never depend on each other.

## Coding guidelines

- **Kotlin style:** follow ktlint and the official Kotlin conventions. Prefer immutable data and small, pure functions.
- **Compose:** screens are stateless functions of a `UiState` plus event callbacks. Read animated values in layout or draw lambdas to avoid per-frame recomposition. Every interactive element needs a content description and a 48 dp target.
- **Tests first:** new logic comes with unit tests; a bug fix starts with a test that fails without the fix. Use Turbine for flows and MockK only where a fake would be awkward.
- **No secrets:** never commit keys, tokens, keystores or `local.properties`. gitleaks runs in CI.
- **Original work only:** do not copy code, icons, artwork or text from other projects unless the licence is compatible with GPL-3.0 and attribution is included.
- **Dependencies:** add new libraries to the version catalogue and explain why in the pull request. Prefer well-maintained, FOSS-friendly libraries.

## Commits and pull requests

1. Fork the repository and create a branch from `main`, for example `feat/sleep-timer-chip` or `fix/scrubber-rtl`.
2. Keep each pull request focused on one change.
3. Use [Conventional Commits](https://www.conventionalcommits.org/) for messages, for example `feat(player): add end-of-track sleep timer`.
4. Fill in the pull request template, including screenshots or a short screen recording for UI changes.
5. Make sure CI is green. A maintainer will review; please be patient and responsive to feedback.

## Licence

Sitara is licensed under the [GNU General Public License v3.0](LICENSE). By contributing, you agree that your contributions will be licensed under the same terms.
