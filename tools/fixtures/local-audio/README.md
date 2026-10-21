# Local audio fixtures

Tiny MP3 files (2-5 KB) for checking Library → On this phone on the CI emulator. Made for Sitara with ffmpeg from sine tones, so they hold no recorded music; the cover is a gradient drawn for them. Public domain (CC0).

| File | Tags |
| --- | --- |
| `01 Saffron Morning.mp3` | Saffron Morning · Tara Lane · Orbit · track 1/2 · cover |
| `02 Second Light.mp3` | Second Light · Tara Lane · Orbit · track 2/2 · cover |
| `field recording 03.mp3` | none: listed as "field recording 03" by an unknown artist |
| `copies/Saffron Morning (copy).mp3` | the first file again, listed once |

Made with (per file, tags and tone changed):

```
ffmpeg -f lavfi -i "sine=frequency=440:duration=2" -i cover.jpg -map 0 -map 1 -ac 1 -ar 8000 -b:a 8k -c:a libmp3lame -c:v copy \
  -id3v2_version 3 -metadata title="Saffron Morning" -metadata artist="Tara Lane" -metadata album="Orbit" -metadata track="1/2" "01 Saffron Morning.mp3"
```
