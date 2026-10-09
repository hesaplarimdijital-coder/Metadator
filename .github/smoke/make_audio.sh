#!/usr/bin/env bash
# Makes the songs the app is tried on, in <out>/Discovery: four tracks of one album, so that they
# can be edited together, each a different answer for the lossless check.
#
#   01  a genuine FLAC: sound up to the top of the band a CD holds
#   02  a FLAC made from an MP3 of 128 kbps: as large as the first, and cut at 16 kHz
#   03  an MP3, which the check has nothing to say about
#   04  a FLAC of 96 kHz made from the first: lossless, with nothing above what a CD holds
#
# The sound is noise. Only its spectrum matters here, and the tags, which are a real album's so
# that the online sources have a cover for it.
set -euo pipefail

out="$1"
work="$out/work"
album="$out/Discovery"
mkdir -p "$work" "$album"

tags=(
  -metadata "artist=Daft Punk"
  -metadata "album=Discovery"
  -metadata "album_artist=Daft Punk"
  -metadata "date=2001"
  # Tags the app has no field for. A save must leave them as they are.
  -metadata "REPLAYGAIN_TRACK_GAIN=-6.50 dB"
  -metadata "MY_NOTE=keep me"
)

ffmpeg -v error -y -f lavfi -i "anoisesrc=d=16:c=pink:r=44100:a=0.6:s=7" \
  -ac 2 -c:a pcm_s16le "$work/source.wav"

ffmpeg -v error -y -i "$work/source.wav" -c:a flac \
  -metadata "title=One More Time" -metadata "track=1" "${tags[@]}" \
  "$album/01 - One More Time.flac"

ffmpeg -v error -y -i "$work/source.wav" -c:a libmp3lame -b:a 128k "$work/lossy.mp3"
ffmpeg -v error -y -i "$work/lossy.mp3" -c:a flac \
  -metadata "title=Aerodynamic" -metadata "track=2" "${tags[@]}" \
  "$album/02 - Aerodynamic.flac"

ffmpeg -v error -y -i "$work/source.wav" -c:a libmp3lame -b:a 320k -id3v2_version 4 \
  -metadata "title=Digital Love" -metadata "track=3" "${tags[@]}" \
  "$album/03 - Digital Love.mp3"

ffmpeg -v error -y -i "$work/source.wav" -ar 96000 -c:a flac \
  -metadata "title=Harder, Better, Faster, Stronger" -metadata "track=4" "${tags[@]}" \
  "$album/04 - Harder, Better, Faster, Stronger.flac"

rm -r "$work"
ls -l "$album"
