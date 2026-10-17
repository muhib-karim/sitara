# Sitara design spec v2: star and orbit

Status: binding for the original UI rebuild, 2026-10-17. This supersedes docs/DESIGN.md. References set a quality bar, never a template. All Sitara code, marks and artwork are authored for this project; Inter remains under its own OFL licence.

## 0. Identity and intent
Sitara is an independent GPL-3.0 Android music player. Its identity is a split four-point star: two offset rays separated by a clear diagonal channel. The mark suggests a star and forward motion without a music-note cliché. It works as a single silhouette at 48dp and as a monochrome adaptive icon.

The visual language is **star and orbit**: precise Inter lettering, a true-black stage, saffron signals, squared artwork with soft corners, and one continuous glass dock. Rounded controls belong to navigation and transport; content has quiet edges. No violet chrome, generic gradient branding, imitation iOS rows, copied screen compositions, or unimplemented controls.

## 1. Reference study and the quality bar
Study implementation techniques: cached artwork colours, static GPU blur, scrims, gesture continuity and legible density. Do not copy source, assets, layout coordinates or proprietary fonts. See REFERENCES.md for provenance.

### How Sitara aims to beat each reference
These are design targets, not verified comparative claims.

| Screen | BitChord quality target | Metrolist quality target | Meld quality target | Sitara's own decision |
|---|---|---|---|---|
| Home | Atmospheric chrome without visual noise | Fast discovery | Clear hierarchy | Quiet neutral listening shelves, one continuous playback dock; discovery expansion in ui2 |
| Player | Blur with readable controls | Confident artwork scale | Direct transport | Artwork stage, offset glow, a continuous timeline and a white play disc; the same stage opens into lyrics |
| Lyrics | Expressive focus | Easy reading and seeking | Predictable controls | Left-aligned Inter Display, active line at 35% of viewport, crisp white active text, distance-softened context |
| Library | Restrained surfaces | Scannable collection | Fast return to music | Art-first collection, consistent saffron selection and common dock; richer sorting in ui2 |
| Search | Readable contrast | Useful grouping | Clear entry point | Neutral query surface, compact results and persistent playback; category discovery in ui2 |
| Settings | Consistent type | Clear controls | Honest options | Functional grouped controls, no decorative dead ends; row redesign in ui2 |
| Album / artist | Immersive art | Clear primary action | Compact rows | Artwork dissolves into neutral content, white action pills; destination work in ui2 |

## 2. Foundations
### 2.1 Color
- Dark: background #000000, surface #101010, elevated #202020, separator #383838, primary text #FFFFFF, secondary #A6A6A6. Light: background #FFFFFF, surface #F6F6F4, elevated #ECECE8, separator #C8C8C2, primary text #161614, secondary #62625D.
- Brand saffron #FF9F1C; accessible light accent #9A4D00. Use for selection, progress and small signals; never a full-screen fill. White play disc with black glyph on the immersive player in both themes.
- Artwork mode keeps brand chrome stable and colours only the player, lyrics and dock tint (8%). Wallpaper mode changes accent/on-accent only; base stays neutral. AMOLED suppresses artwork washes and glass blur.
- Palette input is the current Artwork's swatches (first = dominant). Prefer it when chromatic; otherwise use the most chromatic swatch. Clamp saturation to 0.25–0.60, keep greys neutral. Cache per immutable artwork value; no network or extraction in composition.
- Player/lyrics are a dark listening stage in both app themes, preserving white active lyrics and contrast. The surrounding app respects light mode. Wash changes crossfade over 400ms; reduced motion snaps.

### 2.2 Type scale
Bundle official Inter and Inter Display static weights with OFL. No system-font substitutions in the authored scale.

| Role | Size / weight / tracking | Use |
|---|---|---|
| display | 34sp / 800 / -0.8sp | Screen titles |
| headline | 28sp / 800 / -0.6sp | Album/artist headers |
| lyricActive | 30sp / 800 / -0.6sp; line height 35sp | Synced lyrics |
| title | 22sp / 700 / -0.4sp | Player title |
| section | 20sp / 700 / -0.4sp | Shelf headings |
| rowTitle | 16sp / 600 / -0.2sp | Rows |
| body | 15sp / 400 | Body |
| meta | 13sp / 500 | Artist, counts, duration |
| label | 12sp / 600 / +0.2sp | Controls |

### 2.3 Spacing and shape
4dp grid; screen gutter 20dp; section gap 28dp. Rows at least 64dp, 48dp cover. Tiles 160dp. Radii: row cover 8dp, tile 14dp, player art 20dp, dock/sheet 28dp, controls 50%. No dark Material shadows: depth uses scrims and 0.5dp hairlines. Touch targets at least 48dp, even for 36dp visible circles. Content must remain reachable at 200% type and in landscape.

### 2.4 Motion
Sheet spring: damping 0.85, stiffness 380. The dock opens from its own position; the cover travels from the dock to its stage. Wash enters during the first 40% of expansion. Fades: emphasized 250ms, artwork 400ms, lyric focus 220ms. Lyrics scale 1.0–1.04; keep the active line at 35% of viewport height. Reduced motion snaps every transition and disables blur. Never animate blur radius each frame. Read geometry and animation values in layout/draw phases.

### 2.5 Blur and glass
ArtworkBackdrop draws the current cover overscaled, statically blurred 72dp on API 31+, with a contrast-protecting dark scrim (0.72 / 0.78 / 0.90 over decoded artwork; 0.40 / 0.55 / 0.90 over the already-dark palette fallback). Lower APIs and reduced motion use a flat palette tint. Glass uses existing Haze backdrop blur at 24dp, a contrast-protecting tint, 0.5dp hairline and subtle top highlight. Fallback is 92% opaque elevated fill. Blur is confined to artwork/chrome, never whole scrolling content.

## 3. Components
| Component | Contract |
|---|---|
| FloatingNav | 64dp navigation row, Home / Search / Library / Settings; 20dp side gutters, 12dp above gesture bar; selected icon plus label and filled indicator |
| MiniPlayer | 64dp docked directly above nav inside the same glass outline; 44dp cover, title + artist, play/pause, 2dp saffron timeline; horizontal swipe skips, upward swipe expands; equivalent accessible actions |
| GlassSurface | Shared clipped blur, tint and hairline; no independently stacked slabs |
| GlassIconButton | 36dp visible circle, 20dp glyph, 48dp target |
| PillButton | 48dp minimum; white/black primary on stage, glass secondary; descriptive text |
| ArtworkBackdrop | Artwork-driven wash, neutral scrim, API/reduced-motion/AMOLED fallback |
| SongRow / ArtworkTile | Shared hierarchy and corner tokens; pressed feedback; no inert overflow controls |

## 4. Screens
**Now playing:** full-bleed artwork stage. Compact collapse control and glass Playing / Lyrics / Up next selector. Artwork uses about 85% of portrait width (bounded on wide/short screens), 20dp radius and a 35% palette glow at 48dp blur. Left-aligned 22sp title and artist. Functional timer and queue actions, with no pretend like/overflow controls until persistence exists. Custom 6dp timeline grows to 10dp while seeking; 48dp gesture/keyboard/accessibility target. Transport: shuffle / previous / 72dp white play circle / next / repeat, with selected state semantics. In short landscape, artwork and controls share columns; oversized type can scroll.

**Lyrics:** same stage; compact 44dp cover with title and artist above the lyric viewport. Active line white and heavy, inactive lines dimmed with 0/1/2dp distance blur on supported devices. Contrast takes priority over literal 45% opacity: inactive text must remain readable. Tap a timed line to seek; untimed lyrics stay static at 22sp/600. Empty and end-of-track states remain useful. Transport stays available.

**Home, Library, Search and Settings:** inherit type, neutral colors and shared dock in ui1. Their deeper content and layout redesign belongs to ui2; do not claim ui1 rebuilds them fully. Album and artist destinations are ui2.

## 5. Acceptance (checked against the CI screenshots, side by side with the references)
1. No stock Material component is visible unstyled (bars, chips, radios, switches, sliders).
2. The now-playing and lyrics screens take their color from the artwork in every screenshot.
3. Type hierarchy is visible at thumbnail size: display titles, W600 row titles, secondary meta.
4. The nav and mini player read as one floating glass unit, not two slabs.
5. Light and dark both pass WCAG AA for text (4.5:1) and controls (3:1).
6. Screenshot baselines are re-recorded in CI and committed; CI is green.
