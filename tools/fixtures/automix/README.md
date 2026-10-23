# Automix check track

`app/src/debug/assets/automix-check.ogg` is a 34-second drum pattern at 126 BPM (kick on 1 and 3, snare on 2 and 4,
hi-hats on eighths, a bass note per bar), synthesised by `make-check-track.py` (numpy) and encoded with
`ffmpeg -i click.wav -c:a libopus -b:a 16k automix-check.ogg`. CC0, no recorded audio.

Device screens runs the debug-only `AutomixCheckActivity` on the API 34 emulator: it decodes the start and the end of
the track with the platform's extractor and decoder, as automix does for real songs, and logs the tempo it finds.
