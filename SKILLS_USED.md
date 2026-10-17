# Skills used

How Sitara is built, and which skills were actually invoked. Only invoked skills are listed; each row names its evidence.

**Harnesses and models**
- **Claude Code** on **Claude Opus 5.5** (headless sessions): implementation, CI fixes, tests, README, site.
- **Codex** on **GPT-6 Astra**: planning, cross-model code review, competitor gap analysis.
- Rule: every merge or release gets a cross-model review (the other harness reviews the diff). Nothing is compiled on the dev machine; GitHub Actions builds and tests everything.

**Evidence key.** *CC s1* = Claude Code session 6bfe126d (Phase A build; skill calls made by its design subagent). *CC s2* = Claude Code session 22713459 (CI + review fixes). "line N" = the Skill tool call in that session transcript. *Codex* rows: the skill's SKILL.md was loaded by the Codex session (shown in its session log) and its practice is visible in the output.

| Change / commit | Harness · model | Skills invoked | Evidence |
|---|---|---|---|
| Product plan, docs/plan/PLAN_ASTRA.md | Codex · GPT-6 Astra | using-superpowers, brainstorming, writing-plans, planning-and-task-breakdown, api-and-interface-design, ci-cd-and-automation, agp-9-upgrade, media3-cast-integration, verification-before-completion | Codex plan session log; plan header and task breakdown |
| Phase A build: README banner, app icon, landing page (aabb310, 29e1e20, 914e7a6) | Claude Code · Opus 5.5 | banner-design, impeccable | CC s1 subagent, lines 29 and 165; docs/assets/banner.svg, site/ |
| Cross-model review of Phase A (17 findings, verdict: request changes) | Codex · GPT-6 Astra | code-review-and-quality, security-and-hardening, jetpack-compose-performance, agp-9-upgrade, testing-setup, media3-cast-integration | Codex review session log; docs/review/ |
| Fix plan for the CI failures and review findings (3ddfb2e) | Claude Code · Opus 5.5 | systematic-debugging, writing-plans | CC s2 lines 82 and 97; docs/plan/PHASE_A_FIX_PLAN.md |
| First CI run made to compile: compileSdk 37 + androidx.core (48c10af), ktlint (bd159d1), screenshot tests on debug only (1b78474), CI fails on screenshot mismatch (65a5e43) | Claude Code · Opus 5.5 | ci-cd-and-automation, systematic-debugging | CC s2 line 173 |
| Failing tests first, then fixes: loudness and fade (c4a4acf), unique queue entry ids (df410e1), enhanced LRC (ea463b5), tests for findings 2-14 (3faf584, e0e9735) | Claude Code · Opus 5.5 | test-driven-development (superpowers), test-driven-development (agent-skills) | CC s2 lines 225 and 229 |
| Exported media session hardening: controller allowlist, stream scheme policy (finding 14) | Claude Code · Opus 5.5 | android-permissions-security, security-and-hardening | CC s2 lines 467 and 629 |
| Player sheet and lyrics fixes (findings 2, 12) | Claude Code · Opus 5.5 | jetpack-compose-performance | CC s2 line 534 |
| Self-review of the fix set before the cross-model review | Claude Code · Opus 5.5 | code-review-and-quality | CC s2 line 675 |
| Competitor parity matrix and roadmap ("beat Metrolist, Meld, BitChord, ReVanced") | Codex · GPT-6 Astra | spec-driven-development, planning-and-task-breakdown, ui-ux-pro-max, graphify, incremental-implementation, test-driven-development, context-engineering | Codex gap-analysis session log; docs/plan/ROADMAP.md (next) |

**Gaps (to be closed next session):** playwright (verify the Pages site at mobile and desktop widths once deployed), understand-anything for docs/ARCHITECTURE.md, android-intent-security, and a second Codex GPT-6 Astra cross-review of the fix set before v0.1.0.
