# Sitara design system

Sitara should feel calm, physical and warm: a night sky with one bright star. This document is the source of truth for motion, colour, type, surfaces, icons and accessibility. Everything here is implemented in `:core:designsystem`; feature modules consume these tokens and never hard-code their own.

## Principles

1. **Physical, not decorative.** Movement follows the finger and settles on springs. Nothing animates without a reason.
2. **The artwork leads.** Colour is borrowed from the music that is playing; the chrome stays quiet so covers and lyrics can be the hero.
3. **Glass for chrome, solid for content.** Bars and sheets float above content on frosted glass; lists and text sit on solid, readable surfaces.
4. **Accessible by default.** Every rule below has a reduced-motion, large-text and screen-reader story.

## Motion

All motion comes from one object, `SitaraMotion`. Springs are described by stiffness and damping ratio; *response* is the approximate period of the undamped spring, `response = 2π / sqrt(stiffness)`, which is a more intuitive way to reason about "how fast it feels".

### Springs

| Token | Stiffness | Damping ratio | Response | Used for |
| --- | ---: | ---: | ---: | --- |
| `snappy` | ~800 | 1.0 | ~0.22 s | Tab pill selection, toggles, chips, press feedback, small position changes |
| `smooth` | ~224 | 1.0 | ~0.42 s | Player sheet open and close, mini player morph, large layout moves |
| `gentle` | ~120 | 0.9 | ~0.57 s | Backdrop colour cross-fades, ambient drift, lyric line focus |

`smooth` is derived from its response: stiffness = (2π / 0.42)² ≈ 224. A damping ratio of 1.0 is *critically damped*: the fastest motion that never overshoots. `gentle` allows a whisper of overshoot (0.9) where a little life helps and precision does not matter.

### Durations

Some changes are not positional and read better as fades.

| Token | Duration | Used for |
| --- | ---: | --- |
| `fadeFast` | 120 ms | Pressed states, icon swaps, tooltips |
| `fadeMedium` | 220 ms | Content swaps, list item appearance, pager page labels |
| `fadeSlow` | 360 ms | Screen-level cross-fades, artwork changes |

Fades use a standard decelerate easing on enter and accelerate on exit.

### Sheet velocity hand-off

The full player is a single sheet whose position is one animatable value from 0 (mini player) to 1 (full player).

1. While dragging, the sheet follows the finger 1:1 with no animation.
2. On release, the drag velocity is measured and converted to the sheet's units.
3. The target is chosen from position *and* velocity: a fast flick wins over a short distance, so a quick upward flick opens the sheet even from 10%.
4. The `smooth` spring starts from the current position **with the release velocity as its initial velocity**. Motion is continuous: no jump, no restart, no overshoot.
5. If the user grabs the sheet mid-animation, the spring is interrupted and dragging continues from wherever it is.

The mini player uses the same approach for its gestures: swipe up opens, swipe down hides, and a horizontal swipe past a threshold (or a fast flick) skips to the next or previous track, with the cover sliding out on `snappy`.

### Frame pacing (120 Hz)

- Animations are driven by Compose frame clocks, so they run at the display refresh rate (up to 120 Hz on supported devices) without hard-coded frame counts.
- Animated values are read in the layout or draw phase (`graphicsLayer`, `offset { }`, `drawBehind`) to avoid recomposition per frame.
- Blur radius and gradient stops are not animated per frame on API < 31; the fallback scrim simply cross-fades.

### Reduced motion

Reduced motion is on when the system "Remove animations" setting is on (animator duration scale 0) **or** the in-app setting is enabled.

- Springs become short fades (`fadeFast`) or instant snaps; positional travel is removed.
- The karaoke fill switches from a sweeping gradient to whole-word highlighting.
- Backdrop colour changes cross-fade instead of drifting; no parallax.
- Gestures still work exactly the same; only their visual flourish changes.

## Colour

### Brand palette: Sitara Night

| Role | Hex | Notes |
| --- | --- | --- |
| Background | `#0B0B14` | Deep indigo night; the default dark surface |
| Star gold (accent) | `#F5C451` | Primary accent, play button, active lyric word, focus rings |
| Violet | `#8B7CFF` | Secondary accent, selection pill tint, links |
| Rose | `#FF6B9A` | Tertiary accent, likes, highlights |
| On background | `#F4F2FF` | Primary text on dark surfaces |
| On background (muted) | `#C9C3EE` | Secondary text |

Text on `#0B0B14`: `#F4F2FF` is about 17.7:1 and `#C9C3EE` about 11.7:1. Star gold on the background is about 12:1, violet about 6:1 and rose about 7.3:1, so all three accents are safe for text; dark text (`#0B0B14`) on star gold is used for filled buttons.

### Theme modes

Users choose one of three modes in Settings. Each produces a full Material 3 colour scheme.

| Mode | Source | Behaviour |
| --- | --- | --- |
| **Dynamic** | Material You wallpaper colours (Android 12+) | Falls back to Sitara Night on older devices |
| **Artwork** | Palette extracted from the current cover | Updates with each track, cross-fading on `gentle` |
| **AMOLED black** | Sitara Night with pure `#000000` backgrounds | Surfaces are true black to save power on OLED panels; elevation is shown with subtle outlines instead of lighter greys |

### Artwork palette to theme mapping

The cover is sampled into a small set of swatches. The mapping is a pure function (unit tested) that turns those swatches into colour roles:

1. Pick a **seed**: the most populous swatch with enough chroma; near-greys are skipped so monochrome covers fall back to the brand violet.
2. Derive **primary** from the seed, adjusting lightness until it meets the contrast target against the backdrop.
3. Derive **backdrop** gradient stops from the dominant dark swatch, darkened towards the night background so white text always reads.
4. Derive **secondary** and **tertiary** from the next swatches with distinct hue.
5. Compute **on-colours** by contrast, never by guess.

The player backdrop is a vertical gradient from the tinted dark swatch to the background. Between tracks the old and new gradients cross-fade.

### Contrast targets

- Body text and icons that carry meaning: at least **4.5:1** against their actual background.
- Large text (24 sp and up, or 18.5 sp bold) and non-text UI such as the scrubber track: at least **3:1**.
- Contrast is enforced in code by the palette mapper and checked in screenshot tests; artwork-derived colours are adjusted, not trusted.

## Typography

Phase A uses the system sans-serif. An open-licence display face (Inter or Manrope, both under the SIL Open Font License) is planned for headings, with the system face kept for body text.

| Style | Size / line height | Weight | Used for |
| --- | --- | --- | --- |
| Display | 40 / 48 sp | Bold | Large collapsing titles |
| Headline | 28 / 34 sp | SemiBold | Screen titles, player track title |
| Title | 20 / 26 sp | SemiBold | Section headers, shelf titles |
| Body large | 17 / 24 sp | Regular | Lyrics (inactive lines scale from here) |
| Body | 15 / 22 sp | Regular | List rows, descriptions |
| Label | 13 / 18 sp | Medium | Chips, tab labels, buttons |
| Caption | 12 / 16 sp | Regular | Timestamps, metadata |

Rules: sizes are in `sp` so they scale with system font size; tight negative letter-spacing only on Display and Headline; numbers in the scrubber use tabular figures so they do not jitter.

## Glass surfaces

Glass is used for the floating tab bar, the top app bar once content scrolls under it, the mini player and sheets.

| API level | Treatment |
| --- | --- |
| 31 and up | Real background blur with Haze, plus a translucent tint from the current theme and a hairline highlight on the top edge |
| 26 to 30 | A tinted scrim: the same tint at higher opacity, no blur, same hairline |

Guidelines:

- Text on glass must still meet 4.5:1 against the *worst case* content behind it; the tint opacity is chosen for that, not for looks.
- Glass never stacks more than two layers deep.
- In AMOLED mode the tint is near-black so the glass still reads as dark.

The tab bar is a floating pill with a selection pill that slides between tabs on `snappy`. It hides on scroll down and returns on scroll up.

## Iconography

- Sitara ships its **own geometric icon set, drawn in code** as Compose vectors: play, pause, skip, shuffle, repeat, repeat-one, queue, lyrics, search, library, home, settings, heart, timer and more.
- Built on a 24 dp grid with a 2 dp stroke, rounded caps and joins, and a 20 dp live area.
- Filled variants mark the active state (for example the selected tab); outlined is the default.
- The brand mark is a four-point star with concave sides; it appears in the app icon, splash and empty states. See `docs/assets/icon.svg`.

## Accessibility rules

These are requirements, not suggestions; pull requests are reviewed against them.

- **Touch targets:** at least 48 × 48 dp, even when the visible icon is smaller.
- **TalkBack:** every control has a content description that says what it does ("Play", "Next track", "Open player"), state is announced ("Shuffle on"), decorative images are hidden from accessibility.
- **Custom gestures have alternatives:** swipe-to-skip and swipe-to-hide on the mini player are also exposed as accessibility actions.
- **Font scale:** layouts work at 200% font scale with no clipped text; rows grow vertically and long titles wrap or marquee only on focus.
- **Contrast:** 4.5:1 for text, 3:1 for large text and meaningful non-text UI.
- **Motion:** reduced motion is honoured everywhere (see above).
- **Colour is never the only signal:** active states also change shape, weight or icon fill.
