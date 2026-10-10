#!/usr/bin/env bash
# Starts an Android emulator with no window of its own and waits until it has booted.
#
# The device is made to look like what the app is meant for here: a display as wide as a
# monitor (1920 x 1080 at the density of one), with a keyboard that has real keys, and an SD card
# for music to be kept on.
#
# Usage: start_emulator.sh [system image...]
#
# Several images may be named: the first one the SDK repository has is used. A new release's
# images are named differently from one release to the next (android-37.0, a 16 KB page variant),
# and which of them exists is only known by asking.
set -euo pipefail

sdk="${ANDROID_HOME:?the Android SDK is where ANDROID_HOME says}"
tools="$sdk/cmdline-tools/latest/bin"
if [ "$#" -gt 0 ]; then
  candidates=("$@")
else
  candidates=("system-images;android-35;google_apis;x86_64")
fi

# The licenses are accepted on the runner already; sdkmanager asks again all the same.
yes 2>/dev/null | "$tools/sdkmanager" --licenses >/dev/null 2>&1 || true
available="$("$tools/sdkmanager" --list 2>/dev/null | awk '{print $1}' | grep '^system-images;' || true)"
image=""
for candidate in "${candidates[@]}"; do
  if echo "$available" | grep -qxF "$candidate"; then
    image="$candidate"
    break
  fi
done
if [ -z "$image" ]; then
  echo "None of ${candidates[*]} is in the SDK repository. What there is for the newest releases:"
  echo "$available" | grep -E 'android-3[6-9]' | head -n 40
  exit 1
fi
echo "System image: $image"

# A download that arrives broken is reported as a warning, and nothing more: what was asked for
# is looked for, and asked for again when it is not there.
image_dir="$sdk/$(echo "$image" | tr ';' '/')"
for attempt in 1 2 3; do
  "$tools/sdkmanager" --install emulator platform-tools "$image" >/dev/null || true
  if [ -x "$sdk/emulator/emulator" ] && [ -f "$image_dir/system.img" ]; then
    break
  fi
  echo "The emulator or its system image did not arrive (attempt $attempt)."
  if [ "$attempt" = 3 ]; then
    exit 1
  fi
  sleep 10
done

# Where the device is kept is said outright: the tool that makes it and the one that runs it do
# not agree on where that is when left to themselves.
export ANDROID_AVD_HOME="$HOME/.android/avd"
mkdir -p "$ANDROID_AVD_HOME"
# With an SD card, which the tool makes as an image and lists in the device's config; without one
# if it cannot: the steps that need it say they were not tried.
if ! echo no | "$tools/avdmanager" create avd --force --name smoke --package "$image" \
  --sdcard 512M >/dev/null; then
  echo "No SD card could be made: the device has none."
  echo no | "$tools/avdmanager" create avd --force --name smoke --package "$image" >/dev/null
fi

config="$ANDROID_AVD_HOME/smoke.avd/config.ini"
for key in hw.lcd.width hw.lcd.height hw.lcd.density hw.keyboard hw.ramSize hw.cpu.ncore \
  disk.dataPartition.size; do
  sed -i "/^$key *=/d" "$config"
done
cat >> "$config" <<'CONFIG'
hw.lcd.width=1920
hw.lcd.height=1080
hw.lcd.density=160
hw.keyboard=yes
hw.ramSize=3072
hw.cpu.ncore=2
disk.dataPartition.size=3G
CONFIG

nohup "$sdk/emulator/emulator" -avd smoke -no-window -no-audio -no-boot-anim -no-snapshot \
  -gpu swiftshader_indirect -camera-back none -camera-front none >emulator.log 2>&1 &

adb="$sdk/platform-tools/adb"
timeout 300 "$adb" wait-for-device
booted=""
for _ in $(seq 1 240); do
  booted="$("$adb" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  [ "$booted" = "1" ] && break
  sleep 2
done
if [ "$booted" != "1" ]; then
  echo "The emulator did not boot. What it said last:"
  tail -n 60 emulator.log
  exit 1
fi

# The screen stays on and unlocked, and no keyboard is drawn over the app: this device has keys.
"$adb" shell input keyevent 82
"$adb" shell wm dismiss-keyguard
"$adb" shell svc power stayon true
"$adb" shell settings put secure show_ime_with_hard_keyboard 0
# What the first minute after a boot is busy with would otherwise be taken for the app's slowness.
sleep 20
"$adb" shell getprop ro.build.fingerprint
"$adb" shell wm size
"$adb" shell wm density
grep -i sdcard "$config" || true
"$adb" shell sm list-volumes all || true
