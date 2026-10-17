# Sitara design spec v1: from "default Material" to a premium player

Status: binding for the UI rebuild (owner order, Oct 17 2026). Every choice below cites the reference it comes from. Reference images are kept outside this repo (competitor assets are not ours to ship), so cites point to public URLs.

## 0. Why the current UI fails (audit of the CI baselines, `app/src/test/screenshots/*_dark.png`)
- Default Material 3 surfaces: flat navy fill, tonal chips, stock radio buttons and switches, a stock tab-style bottom bar. Nothing reads as a music product.
- Type is too small and too light for its role: titles about 22sp regular, rows at 14/12sp with no weight contrast, so there is no hierarchy.
- Artwork is small and placeholder gradients dominate, so the screen color comes from the theme, not the music.
- The now-playing and lyrics screens sit on a plain teal fill, not the artwork; the lyrics are about 20sp medium grey, not a hero.
- The mini player and nav bar are two separate opaque slabs that stack and eat about 150dp.

## 1. References
| Id | Source | What we take from it |
|---|---|---|
| BC-hero | BitChord README banner, https://github.com/kushagrasinghx/BitChord/blob/main/Banner.png | Lyrics as a hero: huge heavy type, active line pure white, others dimmed and softly blurred, artwork color washed across the whole screen; compact header with artwork, title and circular glass icon buttons |
| BC-theme | BitChord `ui/theme/Theme.kt` | Neutral true-black / iOS-grey palette (bg #000, surface #0D0D0F, surfaceVariant #1C1C1E, secondary text #8E8E93, hairline #2C2C2E; light: #FFF / #F7F7F9 / #F2F2F7 / #6E6E73 / #E5E5EA), one accent; a tight display type scale with negative tracking |
| BC-backdrop | BitChord `ui/components/ArtworkBackdrop.kt` | Artwork blurred about 72dp, overscaled, rasterised once (not animated), then a vertical scrim (dark: 0.40, 0.55, 0.85 alpha; light: 0.08, 0.25, 0.72); flat tint fallback when the user asks for less blur |
| BC-palette | BitChord `ArtworkPalette.kt` | Player tint = the dominant swatch if it has real chroma (max-min channel), else the vibrant one; clamp the saturation and fix the lightness, so the tint is never neon or pink-washed; cache seeds per URL |
| BC-glass | BitChord `GlassNavBar.kt`, `FloatingBottomBar.kt`, `MiniPlayer.kt`, `TopFadeBlur.kt`, `BottomFadeScrim.kt` | Floating pill nav (50% radius) on frosted glass, unselected at 65% alpha; mini player docked into the same floating glass group with a 0.5dp white 10% hairline; content fades under blurred top and bottom edges instead of hard bars |
| ML-home | Metrolist screenshot 1, https://github.com/MetrolistGroup/Metrolist/tree/main/fastlane/metadata/android/en-US/images/screenshots | Mood chips row, "Quick picks" as a 4-row grid of rows that scrolls horizontally, "Keep listening" as big square tiles |
| ML-artist | Metrolist screenshot 2 | Artist header: large art bleeding to the status bar, name in big bold type, primary actions as pills |
| ML-player | Metrolist screenshot 5 | Player: very large artwork (about 85% of the width), title 22sp bold, share/like as circular buttons, a wide pill Play button, secondary actions in a bottom row |
| ML-lyrics | Metrolist screenshot 6 | Centered lyrics option, the active line brighter and larger |

Typography note: BitChord bundles SF Pro, which Apple licenses only for Apple platforms. Sitara uses **Inter** (OFL) with Inter Display optical sizes for headings: same geometry class, legally shippable.

## 2. Foundations
### 2.1 Color
- Base palette from BC-theme (neutral, not navy). Dark default: bg #000000, surface #0D0D0F, elevated #1C1C1E, separator #2C2C2E, text primary #FFFFFF, secondary #8E8E93 (about 60%), tertiary 38%.
- Accent: Sitara saffron #FF9F1C (brand), used only for the active nav item, progress fill, toggles and primary CTAs. Never for large fills.
- Dynamic color has three modes (setting already exists): From artwork (default; BC-palette rules, recomputed per track with a 400ms crossfade), Material You (wallpaper, accent only, the base stays neutral), AMOLED (pure black, no wash).
- The artwork wash appears only on the player, the lyrics, the album/artist headers and the mini player tint (8% alpha). Lists stay neutral so the art stays the hero.

### 2.2 Type scale (Inter / Inter Display, tracking from BC-theme)
| Role | Size / weight / tracking | Use |
|---|---|---|
| display | 34sp W800, -0.8sp | Screen titles (Home, Library, Search) |
| headline | 28sp W800, -0.6sp | Album/artist names in headers |
| lyricActive | 30sp W800, -0.6sp, line height 1.15 | Synced lyrics (BC-hero) |
| title | 22sp W700, -0.4sp | Now-playing title, section headers 20sp W700 |
| rowTitle | 16sp W600, -0.2sp | List rows |
| body | 15sp W400 | Body text |
| meta | 13sp W500, secondary color | Artist, duration, counts |
| label | 12sp W600, +0.2sp | Chips, buttons |
Weight contrast is mandatory: every row has a W600 title and a secondary W500 meta.

### 2.3 Spacing, shape, elevation
- 4dp grid. Screen gutter 20dp. Section gap 28dp. Row height 64dp (artwork 48dp). Tile 160dp (Home), 44% of width (Library grid).
- Radii: artwork in rows 8dp (BC-mini ART_CORNER), tiles 14dp, player artwork 20dp, sheets 28dp, pills 50%.
- No Material shadows on dark. Depth comes from blur, hairlines (0.5dp white 10%) and scrims.

### 2.4 Motion
- Springs: dampingRatio 0.85, stiffness 380 for sheets and the player expand; 250ms emphasized easing for crossfades.
- Player expand: shared artwork transition from mini (48dp) to full, with the background wash fading in over the first 40% of progress.
- Lyrics: the active line scales 1.0 to 1.04 and goes from 50% to 100% alpha over 220ms; the others blur 0, 1, 2dp by distance (API 31+, else alpha only); auto-scroll keeps the active line at 35% of the height.
- Reduced motion (setting exists) swaps every spring for a 0ms snap and disables blur animation.

### 2.5 Blur and glass
- Frosted glass = blurred backdrop (RenderEffect 24dp on API 31+, a 92% opaque elevated fill below) + 0.5dp hairline + 8% white highlight on top. Use it for the floating nav, the mini player and the player header buttons.
- Top and bottom edges of scrolling screens fade content under a 16dp blur band (BC-glass TopFadeBlur/BottomFadeScrim), not a hard app bar.

## 3. Components
| Component | Spec | Ref |
|---|---|---|
| FloatingNav | 64dp pill, 20dp from the edges, floating 12dp above the gesture bar, glass, 4 items icon + label 11sp; selected item = accent icon on a 50% glass indicator | BC-glass |
| MiniPlayer | Docked directly above FloatingNav in the same glass group, 64dp, 12dp radius, artwork 44dp/8dp, title rowTitle, artist meta, play/pause 40dp glyph slot, a 2dp progress hairline along the bottom edge in the accent color; swipe left/right = next/previous; swipe up = expand | BC-glass, BC-mini |
| GlassIconButton | 36dp circle, glass fill, 20dp icon | BC-hero |
| PillButton | Primary: 48dp high, white fill and black label in dark (Play, Shuffle); secondary: glass | ML-player, ML-artist |
| ArtworkTile | Square art with 14dp radius, title 14sp W600, meta 12sp; pressed scale 0.97 | ML-home |
| SongRow | 64dp, art 48dp/8dp, title + meta, trailing overflow; now-playing row shows animated equalizer bars in the accent color | ML-home |
| MoodChip | 32dp, 50% radius, elevated fill, label 13sp W600; selected = white fill, black text | ML-home |
| SectionHeader | title 20sp W700, optional trailing "See all" chip | ML-home |
| ArtworkBackdrop | Implementation of 2.5 + BC-backdrop recipe, as a reusable composable | BC-backdrop |

## 4. Screens
- **Home**: large "Good evening" display title on the bg; mood chips row; Quick picks as a horizontally paged 4-row grid of SongRows; "New for you" and "Keep listening" as ArtworkTile rails (160dp); content scrolls under the TopFadeBlur. (ML-home)
- **Now playing**: full-bleed ArtworkBackdrop; header with a down chevron and a glass segmented control (Playing / Lyrics / Up next); artwork 85% width with a 20dp radius and a soft color glow (the palette tint at 35%, 48dp blur); title 22sp W700 + artist meta, left aligned, with like and overflow GlassIconButtons on the right; a thick 6dp scrubber that grows to 10dp on drag, times in meta; transport: prev / Play 72dp white circle / next, shuffle and repeat flanking at 60% alpha. (ML-player, BC-backdrop)
- **Lyrics**: same backdrop; compact header (art 44dp, title, artist, GlassIconButtons, like BC-hero); lyricActive type, left aligned, active line white and the others at 45% with distance blur; tap a line to seek; plain (unsynced) lyrics render static at 22sp W600. (BC-hero, ML-lyrics)
- **Library**: display title; filter chips scrollable; default view a 2-column ArtworkTile grid (playlists, albums) with a list/grid toggle; Liked songs pinned first as a gradient tile in the accent color. (ML-home)
- **Search**: display title; a 48dp search field with a 50% radius on the elevated fill; empty state = browse-category tiles (2 columns, colored, 100dp); results grouped (Top result card, Songs, Albums, Artists). (ML-home)
- **Settings**: grouped inset sections (16dp radius on the elevated fill, iOS-style rows 56dp with trailing value or switch), not raw radio lists; "Coming soon" items are hidden, not shown disabled. (BC-theme)
- **Album / Artist**: header art bleeding under the status bar with the ArtworkBackdrop fade into bg, a headline name, PillButtons (Play, Shuffle), then rows. (ML-artist)

## 5. Acceptance (checked against the CI screenshots, side by side with the references)
1. No stock Material component is visible unstyled (bars, chips, radios, switches, sliders).
2. The now-playing and lyrics screens take their color from the artwork in every screenshot.
3. Type hierarchy is visible at thumbnail size: display titles, W600 row titles, secondary meta.
4. The nav and mini player read as one floating glass unit, not two slabs.
5. Light and dark both pass WCAG AA for text (4.5:1) and controls (3:1).
6. Screenshot baselines are re-recorded in CI and committed; CI is green.
