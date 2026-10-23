# Export fixtures

Two-second 440 Hz sine tones made with ffmpeg for the export tests (CC0, no recorded audio):

- `tone.webm`: Opus in WebM, as YouTube serves itag 251.
  `ffmpeg -f lavfi -i "sine=frequency=440:duration=2:sample_rate=48000" -c:a libopus -b:a 24k -ac 1 tone.webm`
- `tone.m4a`: AAC in a plain MP4 with `moov` before `mdat`, so tagging must move its chunk offsets.
  `ffmpeg -f lavfi -i "sine=frequency=440:duration=2:sample_rate=44100" -c:a aac -b:a 32k -ac 1 -movflags +faststart tone.m4a`
- `tone_frag.m4a`: AAC in a fragmented MP4 with a fragment index (`mfra`), as YouTube serves itag 140.
  `ffmpeg -f lavfi -i "sine=frequency=440:duration=2:sample_rate=44100" -c:a aac -b:a 32k -ac 1 -movflags frag_keyframe+empty_moov+default_base_moof -frag_duration 500000 tone_frag.m4a`
