# Technique study

Sitara's design, logo and implementation are original. No reference source file, utility, font or screenshot was copied or adapted. Values explicitly specified in the design brief are implemented as requirements.

| Reference | Revision | Licence | Study |
|---|---|---|---|
| [BitChord](https://github.com/kushagrasinghx/BitChord) | edc1e6f36febf71a118e649411d2fc21b70ead45 | GPL-3.0 | ArtworkBackdrop: static blur and fallback; GlassNavBar: grouping chrome; palette and player components: restrained chroma and hierarchy |
| [Metrolist](https://github.com/MetrolistGroup/Metrolist) | 76fa5a78b5110674442884b6f21c0cef51cdb782 | GPL-3.0 | Player, home and lyrics: content density, lyrics focus and transport reachability |
| Meld | Quality reference only | GPL-3.0 | No code or assets used |

## Libraries used as dependencies

| Library | Version | Licence | Use |
|---|---|---|---|
| [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) | v0.26.5 | GPL-3.0-or-later | Unmodified dependency of `:source:youtube` for YouTube Music song search and audio stream extraction. Sitara's downloader, stream selection and mapping are original; see ADR 0004. |

Sitara keeps its own geometric icons, fake catalog, palette math, spring sheet and Haze integration. Inter is redistributed separately under OFL; see docs/brand/OFL.txt. If a future utility is adapted, record its exact source, revision and licence here and in NOTICE.
