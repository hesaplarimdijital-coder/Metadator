#!/usr/bin/env python3
"""Uses the app on an emulator the way somebody at a desk would, and writes down what happened.

This is not a unit test. It is the check that the APK, as built, opens, lists songs, tells a real
FLAC from one made out of an MP3, finds a cover, writes it without touching anything else, follows
its window as it changes, answers to a keyboard and a mouse, and does all of that for songs on an
SD card too. Everything goes through adb, so the APK tried is the one that is published, untouched.

Usage: smoke.py <apk> <songs dir> <out dir> [<the APK it replaces>]

Each step is tried whatever became of the one before it, and leaves a screenshot and what was on
screen. The report says which held. A step that needs the network, or a device this script has to
make up (the mouse), is reported but does not fail the run: neither is this build's to answer for.

Given the APK the build replaces, the run begins with it: it is installed and lists the songs, then
this one is installed over it, as an update comes to a phone, and has to open what that one left.
"""

import json
import os
import re
import subprocess
import sys
import time
import traceback
import xml.etree.ElementTree as ElementTree

PACKAGE = "com.bobbyesp.metadator.one"
MAIN = "com.bobbyesp.metadator.MainActivity"
EXTERNAL = "com.bobbyesp.metadator.ExternalEditorActivity"
MUSIC = "/sdcard/Music/Discovery"

# Key codes, as android.view.KeyEvent numbers them.
KEY_BACK = 4
KEY_1 = 8
KEY_2 = 9
KEY_4 = 11
KEY_A = 29
KEY_E = 33
KEY_F = 34
KEY_K = 39
KEY_L = 40
KEY_O = 43
KEY_R = 46
KEY_S = 47
KEY_W = 51
KEY_SHIFT = 59
KEY_TAB = 61
KEY_ENTER = 66
KEY_DELETE = 67
KEY_ESCAPE = 111
KEY_CTRL = 113
KEY_MOVE_END = 123
KEY_HOME = 122
KEY_END = 123
KEY_F1 = 131
KEY_F5 = 135

SONGS = {
    "One More Time": "01 - One More Time.flac",
    "Aerodynamic": "02 - Aerodynamic.flac",
    "Digital Love": "03 - Digital Love.mp3",
    "Harder, Better, Faster, Stronger": "04 - Harder, Better, Faster, Stronger.flac",
}

# Two songs of another album, put on the SD card late in the run, when the steps that count the
# songs on screen are done.
CARD_SONGS = {
    "Around the World": "01 - Around the World.flac",
    "Da Funk": "02 - Da Funk.flac",
}
CARD_FOLDER = "Music/Homework"

# Something only the library shows, and always: the last of its places.
LIBRARY = "^Folders$"
# How many songs are selected, as the panel's head says it ("4 songs selected") and the phone's
# ("4 selected").
SELECTED = r"\d+ (songs? )?selected"
SHORTCUTS = "^Keyboard shortcuts$"

VERDICTS = (
    "Looks like real lossless",
    "Lossless, but upscaled",
    "Can't tell whether it is real lossless",
    "Probably made from a lossy file",
    "Made from a lossy file",
)

apk_path, songs_dir, out_dir = sys.argv[1], sys.argv[2], sys.argv[3]
previous_apk = sys.argv[4] if len(sys.argv) > 4 else None
os.makedirs(out_dir, exist_ok=True)
log_file = open(os.path.join(out_dir, "log.txt"), "w", encoding="utf-8")
results = []
shots = 0
started = time.time()


def log(message):
    line = f"[{time.time() - started:7.1f}s] {message}"
    print(line, flush=True)
    log_file.write(line + "\n")
    log_file.flush()


def adb(*args, timeout=120, binary=False):
    try:
        done = subprocess.run(["adb", *args], capture_output=True, timeout=timeout)
    except subprocess.TimeoutExpired:
        log(f"adb {' '.join(args)[:80]}: no answer in {timeout} s")
        return b"" if binary else ""
    if binary:
        return done.stdout
    return (done.stdout + done.stderr).decode("utf-8", "replace")


def shell(command, timeout=120):
    return adb("shell", command, timeout=timeout)


class Failed(Exception):
    """A step did not hold."""


class Skipped(Exception):
    """A step could not be tried, for a reason that is not the app's."""


class NotBegun(Skipped):
    """A step that could not begin (no time left for it, no device it needs): nothing was touched,
    so there is nothing to tidy up after it."""


class Node:
    """One thing on the screen. What it says is often said by what is inside it: a row's title
    is a text within the row, and it is the row that is clicked and that has the focus."""

    def __init__(self, element, parent=None):
        get = element.attrib.get
        self.parent = parent
        self.children = []
        self.text = get("text", "")
        self.description = get("content-desc", "")
        self.kind = get("class", "")
        self.package = get("package", "")
        self.clickable = get("clickable") == "true"
        self.focused = get("focused") == "true"
        self.selected = get("selected") == "true"
        self.checked = get("checked") == "true"
        self.enabled = get("enabled") != "false"
        # A song's row, while songs are being selected, is a check box, and one that is checked
        # says it takes no click: it is still what a click on it lands on.
        self.takes_click = self.clickable or get("checkable") == "true"
        numbers = [int(n) for n in re.findall(r"-?\d+", get("bounds", ""))]
        self.left, self.top, self.right, self.bottom = (numbers + [0, 0, 0, 0])[:4]

    @property
    def center(self):
        return (self.left + self.right) // 2, (self.top + self.bottom) // 2

    @property
    def is_field(self):
        return self.kind.endswith("EditText")

    def says(self, pattern):
        return bool(re.search(pattern, self.text) or re.search(pattern, self.description))

    def within(self):
        """This and everything inside it."""
        yield self
        for child in self.children:
            yield from child.within()

    def holds(self, pattern):
        return any(node.says(pattern) for node in self.within())

    def pressed_as(self):
        """What a click on this lands on: itself, or the nearest thing around it that takes one."""
        node = self
        while node is not None and not node.takes_click:
            node = node.parent
        return node or self

    def __repr__(self):
        said = self.text or self.description
        return f"<{said!r} at {self.left},{self.top}-{self.right},{self.bottom}>"


last_dump = ""


# The system's "isn't responding" dialogs that were waited out, for the report: on a busy emulator
# its own processes can be slow to answer, and the dialog would stand in front of the app.
waited_out = []


def screen(attempts=4):
    """What the window in front shows, as the accessibility tree has it."""
    global last_dump
    for _ in range(attempts + 3):
        said = shell("uiautomator dump /sdcard/window_dump.xml", timeout=60)
        if "dumped to" in said:
            raw = adb("exec-out", "cat", "/sdcard/window_dump.xml", binary=True)
            try:
                root = ElementTree.fromstring(raw.decode("utf-8", "replace"))
            except ElementTree.ParseError:
                time.sleep(0.5)
                continue
            last_dump = raw.decode("utf-8", "replace")
            nodes = []

            def read(element, parent):
                node = Node(element, parent)
                nodes.append(node)
                if parent is not None:
                    parent.children.append(node)
                for child in element.findall("node"):
                    read(child, node)

            for top in root.findall("node"):
                read(top, None)
            stuck = [node for node in nodes if node.says("isn.t responding")]
            wait = [node for node in nodes if node.says("^Wait$")]
            if stuck and wait and len(waited_out) < 10:
                waited_out.append(stuck[0].text or stuck[0].description)
                log(f"    the system says {waited_out[-1]!r}: waiting")
                tap(wait[0])
                time.sleep(3)
                continue
            return nodes
        time.sleep(1)
    return []


def said_on(nodes):
    """Everything written on the screen, for a report to quote."""
    seen = []
    for node in nodes:
        for words in (node.text, node.description):
            if words and words not in seen:
                seen.append(words)
    return seen


def find(pattern, nodes=None):
    nodes = screen() if nodes is None else nodes
    return [node for node in nodes if node.says(pattern)]


def expect(pattern, timeout=30, absent=None):
    """Waits until something on screen says [pattern] (and nothing says [absent])."""
    deadline = time.time() + timeout
    nodes = []
    while True:
        nodes = screen()
        found = [node for node in nodes if node.says(pattern)]
        if found and not (absent and any(node.says(absent) for node in nodes)):
            return found[0]
        if time.time() > deadline:
            raise Failed(f"nothing says /{pattern}/ after {timeout} s. On screen: {said_on(nodes)[:40]}")
        time.sleep(1)


def expect_gone(pattern, timeout=20):
    deadline = time.time() + timeout
    while True:
        nodes = screen()
        if nodes and not any(node.says(pattern) for node in nodes):
            return nodes
        if time.time() > deadline:
            raise Failed(f"/{pattern}/ is still there after {timeout} s")
        time.sleep(1)


def shortcuts_open(nodes=None, title=SHORTCUTS):
    """Whether the list of shortcuts is in front. Its title alone does not say so: the library's
    sidebar has a place of that name, from which a click opens the list. The list's own title is
    the one with nothing around it that takes a click."""
    nodes = screen() if nodes is None else nodes
    return any(not node.pressed_as().takes_click for node in nodes if node.says(title))


def expect_shortcuts(shown=True, timeout=15, title=SHORTCUTS):
    """Waits until the list of shortcuts is in front, or gone from it."""
    deadline = time.time() + timeout
    while True:
        nodes = screen()
        if nodes and shortcuts_open(nodes, title) == shown:
            return nodes
        if time.time() > deadline:
            state = "in front" if shown else "gone"
            raise Failed(f"the list of shortcuts is not {state} after {timeout} s: {said_on(nodes)[:30]}")
        time.sleep(1)


def tap(node):
    x, y = node.center
    shell(f"input tap {x} {y}")
    time.sleep(0.6)


def key(*codes):
    """One key, or several held together in the order given: key(KEY_CTRL, KEY_S)."""
    if len(codes) == 1:
        shell(f"input keyevent {codes[0]}")
    else:
        shell("input keycombination " + " ".join(str(code) for code in codes))
    time.sleep(0.8)


def type_text(text):
    """Types into the field that has the focus, and sees to it that this is what it holds.

    On an emulator this slow the first key into a field can take so long to be dealt with that the
    system repeats it, as it does for a key held down. A person's key press is far too short for
    that, so a doubled letter here is the test's, and is typed again.
    """
    for _ in range(3):
        shell(f"input text {text}")
        time.sleep(0.8)
        fields = [node for node in screen() if node.is_field and node.focused]
        if not fields:
            raise Failed(f"no field has the focus to type {text!r} into")
        if fields[0].text == text:
            return
        log(f"    typed {text!r}, the field holds {fields[0].text!r}: again")
        key(KEY_CTRL, KEY_A)
        key(KEY_DELETE)
    raise Failed(f"the field never held {text!r}")


def screenshot(name):
    global shots
    shots += 1
    stem = os.path.join(out_dir, f"{shots:02d}-{name}")
    picture = adb("exec-out", "screencap", "-p", binary=True, timeout=60)
    if picture.startswith(b"\x89PNG"):
        with open(stem + ".png", "wb") as file:
            file.write(picture)
    if last_dump:
        with open(stem + ".xml", "w", encoding="utf-8") as file:
            file.write(last_dump)


def slug(name):
    return re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")[:48]


def step(name, action, required=True):
    """Tries [action], and writes down how it went and what the screen showed after it."""
    log(f"--- {name}")
    began = time.time()
    running_new[0] = None
    outcome, detail, tidy = "ok", "", True
    try:
        detail = action() or ""
    except NotBegun as reason:
        outcome, detail, tidy = "not tried", str(reason), False
    except Skipped as reason:
        outcome, detail = "not tried", str(reason)
    except Exception as error:  # Whatever went wrong, the next step is still worth trying.
        outcome = "FAILED" if required else "did not hold (not required)"
        detail = f"{type(error).__name__}: {error}"
        log(traceback.format_exc())
    log(f"    {outcome} {detail}")
    results.append((name, outcome, str(detail)))
    if not tidy:
        running_new[0] = None
        return False
    try:
        if outcome != "ok":
            screen()
        screenshot(slug(name))
        if outcome != "ok" and restore_after_failure:
            # What it left open would fail every step after it for no fault of theirs.
            log(f"    back to the library: {home()}")
    except Exception as error:
        log(f"    could not tidy up: {error}")
    took = time.time() - began
    step_times[name] = took
    if running_new[0]:
        new_steps_run.append((running_new[0], took))
        running_new[0] = None
    log(f"    took {took:.0f} s")
    return outcome == "ok"


restore_after_failure = False


def home():
    """Puts the library in front, with every song listed and none selected."""
    for _ in range(10):
        nodes = screen()
        if not nodes:
            continue
        if not any(node.package == PACKAGE for node in nodes):
            # A dialog of the system's, or another app: back to this one.
            if any("documentsui" in node.package for node in nodes):
                # The system's file picker, opened with Ctrl+O: it is left with Back.
                key(KEY_BACK)
            elif find(r"^(Deny|Don.t allow|Cancel)$", nodes):
                tap(find(r"^(Deny|Don.t allow|Cancel)$", nodes)[0])
            else:
                shell(f"am start -n {PACKAGE}/{MAIN}")
                time.sleep(3)
        elif find("^Close$", nodes) and find("^Up next$", nodes):
            # The full-screen player, over the library.
            tap(find("^Close$", nodes)[0])
        elif (
            shortcuts_open(nodes)
            or find("^Find a cover$", nodes)
            or find(r"^Find the album.s metadata$", nodes)
        ):
            key(KEY_ESCAPE)
        elif find("^Open in a new window$", nodes) or (
            find("^Open$", nodes) and find("^Play$", nodes)
        ):
            # A menu of the second mouse button, left open. It is a window of its own, and the dump
            # of the window in front holds the menu alone: it is known by its items.
            key(KEY_ESCAPE)
        elif find("^Discard$", nodes):
            tap(find("^Discard$", nodes)[0])
        elif find("^Finish$", nodes):
            tap(find("^Finish$", nodes)[0])
        elif find(r"^Edit \d+ songs$", nodes):
            key(KEY_CTRL, KEY_W)
        elif not find(LIBRARY, nodes) and find("^Edit tags$", nodes):
            # The full-screen player: Back closes it.
            key(KEY_BACK)
        elif not find(LIBRARY, nodes) and find(BACK_ARROW, nodes):
            # An album's screen, or a page of the settings: the arrow in its toolbar.
            tap(find(BACK_ARROW, nodes)[0])
        elif not find(LIBRARY, nodes):
            # The editor alone on a narrow window, or a second window: closed, the library is back.
            key(KEY_CTRL, KEY_W)
        elif find("Stop selecting", nodes):
            tap(find("Stop selecting", nodes)[0])
        elif find("^Clear search$", nodes):
            tap(find("^Clear search$", nodes)[0])
        elif find("^Clear filters$", nodes):
            tap(find("^Clear filters$", nodes)[0])
        elif [chip for chip in filter_chips(nodes) if chip.checked]:
            tap([chip for chip in filter_chips(nodes) if chip.checked][0])
        elif place_shown(nodes) not in (None, "Songs", "Şarkılar"):
            # Albums, a smart list, a place of the storage: the Songs are one key away.
            key(KEY_CTRL, KEY_1)
        elif all(find(re.escape(title), nodes) for title in SONGS):
            return "the library, with its four songs"
        elif (number_in(sidebar_count("Songs", nodes)) or 0) >= LONG_LIBRARY and find(
            "^Aerodynamic$", nodes
        ):
            # Once the long library is there the four songs are not all on one screen.
            return "the library, with its long list"
        else:
            key(KEY_CTRL, KEY_1)
        time.sleep(1.2)
    return f"not restored: {said_on(screen())[:25]}"


# The arrow in the toolbar of an album's screen and of the settings' pages.
BACK_ARROW = r"^(Back|Geri)$"


def filter_chips(nodes):
    """The chips over the list that narrow it to the songs without a cover or with garbled text."""
    return [
        node.pressed_as()
        for node in nodes
        if node.top < 100
        and re.fullmatch(r"(No cover|Missing covers|Garbled text|Kapaksız|Kapağı eksik|Bozuk karakterler) \(\d+\)", node.text)
    ]


def place_shown(nodes):
    """The place of the sidebar that is chosen: the one item of it that is selected."""
    for node in nodes:
        if node.selected and node.right <= 240 and node.children:
            return node.children[0].text
    return None


# --- The songs, on the device and as files ----------------------------------------------------


def probe(path):
    """A file's tags, whether it has a picture and how large, and a checksum of its sound."""
    described = subprocess.run(
        ["ffprobe", "-v", "error", "-show_format", "-show_streams", "-of", "json", path],
        capture_output=True,
        text=True,
    )
    data = json.loads(described.stdout or "{}")
    tags = {
        name.lower(): value
        for name, value in data.get("format", {}).get("tags", {}).items()
        if name.lower() != "encoder"
    }
    pictures = [
        (stream.get("codec_name"), stream.get("width"), stream.get("height"))
        for stream in data.get("streams", [])
        if stream.get("codec_type") == "video"
    ]
    sound = subprocess.run(
        ["ffmpeg", "-v", "error", "-i", path, "-map", "0:a", "-f", "md5", "-"],
        capture_output=True,
        text=True,
    ).stdout.strip()
    return {"tags": tags, "pictures": pictures, "sound": sound}


def pull(file_name, folder=MUSIC):
    target = os.path.join(out_dir, "pulled")
    os.makedirs(target, exist_ok=True)
    local = os.path.join(target, file_name)
    if os.path.exists(local):
        os.remove(local)
    adb("pull", f"{folder}/{file_name}", local, timeout=120)
    return local


def cover_shown(title):
    """The cover at the start of the song's row, as pixels: a square out of the middle of the
    picture just before the title, which the row keeps at its height's middle and a little way off
    the title in either of its layouts. The picture is not a thing the screen's tree knows; its
    place beside the title is."""
    nodes = screen()
    titles = [node for node in find(re.escape(title), nodes) if not node.is_field]
    if not titles:
        raise Failed(f"no row says {title!r}")
    label = min(titles, key=lambda node: (node.left, node.top))
    line = label.pressed_as()
    height = line.bottom - line.top
    side = max(12, min(20, height // 2))
    left = label.left - 14 - side
    top = line.top + (height - side) // 2
    if left < line.left or height <= 0:
        raise Failed(f"no room for a cover before {title!r}: {label} in {line}")
    picture = adb("exec-out", "screencap", "-p", binary=True, timeout=60)
    cropped = subprocess.run(
        ["ffmpeg", "-v", "error", "-i", "pipe:0", "-vf", f"crop={side}:{side}:{left}:{top}",
         "-f", "rawvideo", "-pix_fmt", "gray", "pipe:1"],
        input=picture,
        capture_output=True,
        check=True,
    ).stdout
    return cropped


def pictures_differ(before, after):
    """How much two covers differ, on a scale of 0 to 255; None when they cannot be compared."""
    if not before or len(before) != len(after):
        return None
    return sum(abs(a - b) for a, b in zip(before, after)) / len(before)


def rows_show_new_covers(titles, shown_before, timeout=20):
    """Waits for each song's row to show a cover other than the one it showed before the save."""
    deadline = time.time() + timeout
    changes = {}
    while time.time() < deadline:
        for title in titles:
            if title in changes:
                continue
            difference = pictures_differ(shown_before[title], cover_shown(title))
            if difference is not None and difference >= NEW_PICTURE:
                changes[title] = difference
        if len(changes) == len(titles):
            break
        time.sleep(2)
    still = [title for title in titles if title not in changes]
    if still:
        raise Failed(f"the rows of {still} still show the cover they had before the save")
    return ", ".join(f"{title}: {difference:.0f}" for title, difference in changes.items())


# Rows of a list are small and a cover is most of one; a different picture moves the mean of its
# pixels by far more than the ripple of a tap or the badge of a verdict does.
NEW_PICTURE = 8


def listed_songs():
    """The songs MediaStore has indexed: [(id, title, path)]."""
    rows = shell(
        "content query --uri content://media/external/audio/media "
        "--projection _id:title:_data"
    )
    songs = []
    for row in rows.splitlines():
        match = re.search(r"_id=(\d+), title=(.*), _data=(.*)$", row.strip())
        if match and "/Music/Discovery/" in match.group(3):
            songs.append((match.group(1), match.group(2), match.group(3)))
    return songs


def activity_instance(activity=MAIN):
    """Which object the activity is: another one after the system has recreated it."""
    dumped = shell(f"dumpsys activity {PACKAGE}", timeout=60)
    for block in re.split(r"\n\s*ACTIVITY ", dumped):
        if activity.rsplit(".", 1)[-1] in block.split("\n", 1)[0]:
            match = re.search(r"Local Activity ([0-9a-f]+) State", block)
            if match:
                return match.group(1)
    return None


def allow_system_dialog(timeout=30):
    """Says yes to the system: the permission to list music, or to change a file. Pressed again
    while it is still asked: on a slow emulator a press as the dialog comes in is lost, and a run
    once waited a minute on a dialog it had pressed."""
    button = expect(r"^(Allow|ALLOW)$", timeout)
    for _ in range(4):
        tap(button)
        time.sleep(1.5)
        still = find(r"^(Allow|ALLOW)$")
        if not still:
            return
        button = still[0]


def row(title, nodes=None):
    """The song's row in the list, not the editor's field that says the same, nor the player's bar
    while that song plays: the bar's title starts further left than the rows', and a tap on it
    opened the player over every step after it. The bar is at the foot of the window, in the band
    of its Play or Pause button."""
    if nodes is None:
        nodes = screen()
    foot = max((node.bottom for node in nodes), default=0) * 0.7
    bars = [node for node in nodes if node.top > foot and node.says(r"^(Play|Pause)$")]

    def in_bar(node):
        middle = (node.top + node.bottom) // 2
        return any(bar.top - 48 <= middle <= bar.bottom + 48 for bar in bars)

    titles = [
        node for node in find(re.escape(title), nodes) if not node.is_field and not in_bar(node)
    ]
    if not titles:
        raise Failed(f"no row says {title!r}")
    return min(titles, key=lambda node: (node.left, node.top)).pressed_as()


# --- Setting up --------------------------------------------------------------------------------

before = {}


# Right after boot, the package service can still turn an install down for reasons of its own:
# those are tried again, once it answers. A failure of the APK is not.
NOT_READY = re.compile(r"Broken pipe|Can't find service|device offline|Failure calling service")


def install_apk(path):
    """Installs [path] over what is installed, keeping its data, as an update does."""
    for attempt in range(4):
        said = adb("install", "-r", path, timeout=300)
        if "Success" in said:
            return
        if attempt == 3 or not NOT_READY.search(said):
            raise Failed(said.strip()[-300:])
        log(f"    the package service is not ready: {said.strip().splitlines()[-1]}")
        deadline = time.time() + 60
        while time.time() < deadline and "package:" not in shell("pm path android"):
            time.sleep(3)
        time.sleep(5)


def updates_the_build_before():
    """The build this one replaces lists the songs, as on the owner's phone; then this one is
    installed over it. Its database, made by the build before, is this one's to open: a migration
    that does not hold closes the app at its first query, and an APK signed otherwise is refused."""
    try:
        install_apk(previous_apk)
        shell(f"am start -W -n {PACKAGE}/{MAIN}", timeout=90)
        tap(expect("Allow access", 60))
        allow_system_dialog()
        try:
            expect("One More Time", 45)
        except Failed:
            key(KEY_F5)
            expect("One More Time", 60)
    except Failed as reason:
        raise Skipped(f"the build before did not list the songs: {reason}")
    try:
        # What the library read of each song is written to its database as it lists them.
        time.sleep(10)
        shell(f"am force-stop {PACKAGE}")
        shell("logcat -b crash -c")
        install_apk(apk_path)
        dumped = shell(f"dumpsys package {PACKAGE}")
        installed = re.search(r"firstInstallTime=([^\n]+)", dumped)
        updated = re.search(r"lastUpdateTime=([^\n]+)", dumped)
        shell(f"am start -W -n {PACKAGE}/{MAIN}", timeout=90)
        # Still allowed to see the music: the songs, with no question first.
        expect("One More Time", 60)
        for place in PLACES:
            open_place(place, "the app just updated")
        crashes = adb("logcat", "-d", "-b", "crash", timeout=60)
        with open(os.path.join(out_dir, "logcat-crash-update.txt"), "w", encoding="utf-8") as file:
            file.write(crashes)
        if any(PACKAGE in line or "metadator" in line for line in crashes.splitlines()):
            raise Failed(f"the app just updated crashed: {crash_report()}")
        if not shell(f"pidof {PACKAGE}").strip():
            raise Failed("the app just updated is not running")
        screen()
        screenshot("updated")
        return (
            f"installed {installed.group(1).strip() if installed else '?'}, updated "
            f"{updated.group(1).strip() if updated else '?'}: it lists the songs it was allowed to "
            f"see, and {', '.join(PLACES)} open"
        )
    finally:
        # The steps after this begin as a phone does with a new app: asked to see the music.
        adb("uninstall", PACKAGE, timeout=120)


def install():
    install_apk(apk_path)
    # A device with a keyboard plugged in draws none on its screen. The emulator draws one all the
    # same, over half of every picture, so the ones it has are turned off: keys still type.
    for method in shell("ime list -s").split():
        shell(f"ime disable {method}")
    version = re.search(r"versionName=(\S+)", shell(f"dumpsys package {PACKAGE}"))
    release = shell("getprop ro.build.version.release").strip()
    sdk = shell("getprop ro.build.version.sdk").strip()
    size = shell("wm size").strip().replace("\n", ", ")
    density = shell("wm density").strip().replace("\n", ", ")
    return (
        f"version {version.group(1) if version else '?'} on Android {release} (API {sdk}); "
        f"{size}; {density}"
    )


def copy_songs():
    shell(f"mkdir -p {MUSIC}")
    for file_name in SONGS.values():
        local = os.path.join(songs_dir, "Discovery", file_name)
        before[file_name] = probe(local)
        adb("push", local, f"{MUSIC}/{file_name}", timeout=120)

    def ask_for_a_scan():
        for file_name in SONGS.values():
            shell(
                "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE "
                f"-d 'file://{MUSIC}/{file_name}'"
            )

    ask_for_a_scan()
    started_at = time.time()
    asked_again = False
    while time.time() < started_at + 180:
        songs = listed_songs()
        if len(songs) >= len(SONGS) and all(title in SONGS for _, title, _ in songs):
            return f"MediaStore lists {len(songs)} songs, in {time.time() - started_at:.0f} s"
        # A media store that was starting again when it was asked heard nothing: ask once more.
        if not asked_again and time.time() > started_at + 60:
            ask_for_a_scan()
            asked_again = True
        time.sleep(3)
    raise Failed(f"MediaStore lists {listed_songs()}")


# Where the SD card is: its root as apps see it, and where the system mounts it underneath that.
card = None


def public_volumes():
    """The volumes mounted besides the device's own storage: [(id, file system uuid)]."""
    found = []
    for line in shell("sm list-volumes public").splitlines():
        parts = line.split()
        if len(parts) >= 3 and parts[0].startswith("public:") and parts[1] == "mounted":
            if parts[2] != "null":
                found.append((parts[0], parts[2]))
    return found


def prepare_card():
    """The emulator's SD card, or, when it has none, one the system makes out of a file."""
    global card
    # The emulator's own card can be mounted a while after the system says it has booted.
    volumes = public_volumes()
    deadline = time.time() + 45
    while not volumes and time.time() < deadline:
        time.sleep(3)
        volumes = public_volumes()
    made = ""
    if not volumes:
        shell("sm set-virtual-disk true")
        disk = None
        deadline = time.time() + 60
        while disk is None and time.time() < deadline:
            time.sleep(2)
            disks = [line.strip() for line in shell("sm list-disks").splitlines()]
            disk = next((line for line in disks if line.startswith("disk:")), None)
        if disk is None:
            listed = shell("sm list-volumes all").strip()
            raise Skipped(f"the device has no SD card, and could make none: {listed[:200]}")
        shell(f"sm partition {disk} public", timeout=180)
        deadline = time.time() + 90
        while not volumes and time.time() < deadline:
            time.sleep(3)
            volumes = public_volumes()
        made = "made by the system out of a file, "
    if not volumes:
        raise Skipped(f"no SD card was mounted: {shell('sm list-volumes all').strip()[:200]}")
    volume, uuid = volumes[0]
    card = {"root": f"/storage/{uuid}", "underneath": f"/mnt/media_rw/{uuid}"}
    return f"an SD card {made}mounted at {card['root']} ({volume})"


# --- The steps ---------------------------------------------------------------------------------


def opens_and_asks():
    shell("logcat -c")
    shell(f"am start -W -n {PACKAGE}/{MAIN}", timeout=90)
    expect("Allow access", 60)
    return "the first screen asks to see the music"


def lists_songs():
    tap(expect("Allow access", 10))
    allow_system_dialog()
    try:
        expect("One More Time", 45)
    except Failed:
        # The library was read before the scanner was done with the files just copied.
        key(KEY_F5)
        expect("One More Time", 60)
    nodes = screen()
    missing = [title for title in SONGS if not find(re.escape(title), nodes)]
    if missing:
        raise Failed(f"not listed: {missing}")
    return "the four songs are listed"


def two_panes():
    expect("Pick a song", 10)
    return "the editor's place is beside the library"


def shortcut_list():
    key(KEY_F1)
    nodes = expect_shortcuts()
    # The screen in front comes first in the list, so the library's are in sight.
    for wanted in ("^Library$", "Search the library", "^Ctrl$", "^Close$"):
        if not find(wanted, nodes):
            raise Failed(f"the list does not say /{wanted}/: {said_on(nodes)[:60]}")
    screenshot("the-shortcuts")
    listed = said_on(nodes)
    # The list is longer than the window. Its end is reached with a key, and its start again.
    last = "Select every song up to that one"
    if find(last, nodes):
        scrolled = "all of it fits"
    else:
        key(KEY_END)
        expect(last, 15)
        screenshot("the-end-of-the-shortcuts")
        key(KEY_HOME)
        expect("Search the library", 15)
        scrolled = "End and Home move through it"
    key(KEY_ESCAPE)
    expect_shortcuts(shown=False)
    expect("One More Time", 15)
    return f"F1 shows them, {scrolled}, Esc closes them. The list starts: {listed[:24]}"


def search():
    key(KEY_CTRL, KEY_F)
    type_text("aero")
    expect("Aerodynamic", 20, absent="One More Time")
    screenshot("searching")
    tap(expect("Clear search", 10))
    expect("One More Time", 20)
    return "Ctrl+F goes to the search field, and typing there filters the list"


def keyboard_alone():
    """Tab until a song has the focus, then Enter: the keys reach what a tap reaches. The sidebar,
    the toolbar and the table's header come first."""
    for _ in range(12):
        key(KEY_TAB)
    focused = None
    for _ in range(36):
        nodes = screen()
        focused = [node for node in nodes if node.focused]
        titles = [title for title in SONGS if focused and focused[0].holds(re.escape(title))]
        if titles:
            screenshot("a-song-has-the-focus")
            key(KEY_ENTER)
            title = titles[0]
            deadline = time.time() + 40
            while time.time() < deadline:
                if [node for node in find(re.escape(title)) if node.is_field]:
                    key(KEY_CTRL, KEY_W)
                    expect("Pick a song", 20)
                    return f"Tab reached '{title}', Enter opened it, Ctrl+W closed it"
                time.sleep(1)
            raise Failed(f"Enter on '{title}' did not open it")
        key(KEY_TAB)
    raise Failed(f"Tab never reached a song. The focus is on {focused}")


def verdict_of(title, wanted):
    def action():
        tap(row(title))
        asked = time.time()
        deadline = asked + 120
        seen, nodes, steady = None, [], 0
        while time.time() < deadline:
            nodes = screen()
            # The editor of the song before may still be leaving: the verdict only counts once
            # the title field is this song's, and when it reads the same twice.
            opened = [node for node in find(re.escape(title), nodes) if node.is_field]
            now = [verdict for verdict in VERDICTS if find(re.escape(verdict), nodes)]
            steady = steady + 1 if opened and now and now == seen else 0
            seen = now if opened else None
            if steady:
                break
            time.sleep(1.5)
        if not seen:
            raise Failed(f"no verdict in two minutes. On screen: {said_on(nodes)[:50]}")
        if seen[0] not in wanted:
            raise Failed(f"the verdict is {seen[0]!r}, not one of {wanted}")
        details = [
            words
            for words in said_on(nodes)
            if re.search(r"kHz|kbps|-bit|lossy|lossless", words) and len(words) < 220
        ]
        return f"{seen[0]} (within {time.time() - asked:.0f} s): {details[:6]}"

    return action


def no_verdict_for_mp3():
    tap(row("Digital Love"))
    time.sleep(8)
    nodes = screen()
    if not [node for node in find("Digital Love", nodes) if node.is_field]:
        raise Failed(f"the editor did not open it: {said_on(nodes)[:40]}")
    said = [verdict for verdict in VERDICTS if find(re.escape(verdict), nodes)]
    if said or find("Checking whether", nodes):
        raise Failed(f"an MP3 was given a verdict: {said}")
    return "an MP3 is opened and nothing is said about it"


cover_search_worked = False


def read_cover_sheet(timeout=120):
    """Waits for the sheet to say what it found. Returns the button to press, or raises."""
    global cover_search_worked
    expect("^Find a cover$", 20)
    deadline = time.time() + timeout
    while time.time() < deadline:
        nodes = screen()
        use = find(r"^(Use this cover|Use for )", nodes)
        if use:
            cover_search_worked = True
            reasons = [
                words
                for words in said_on(nodes)
                if re.search(
                    r"match|sources have|picture|cover of the release|Large enough|album|px",
                    words,
                )
                and len(words) < 160
            ]
            return use[0], reasons
        for trouble in (
            "No covers found",
            "offline",
            "The search failed",
            "sources are busy",
            "Every source is turned off",
        ):
            if find(trouble, nodes):
                raise Skipped(f"the sources answered: {trouble}")
        time.sleep(2)
    raise Failed(f"the sheet said nothing in {timeout} s: {said_on(screen())[:40]}")


def find_cover_for_song():
    tap(row("One More Time"))
    expect("Looks like real lossless", 120)
    key(KEY_CTRL, KEY_K)
    use, reasons = read_cover_sheet()
    screenshot("covers-for-a-song")
    # Esc closes it, as a dialog closes; opened again, it shows what it found.
    key(KEY_ESCAPE)
    expect_gone("^Find a cover$", 15)
    key(KEY_CTRL, KEY_K)
    use, _ = read_cover_sheet(timeout=20)
    tap(use)
    expect_gone("Other covers|Use this cover", 20)
    # The cover arrives a moment later: the editor says how large it is once it has it.
    expect(r"\d+ × \d+ px", 90)
    return f"Esc closed the sheet, and the best cover is in the editor. It said: {reasons[:10]}"


# What each song's row showed as its cover before a save, to see it change after.
covers_before = {}


def save_song():
    if not cover_search_worked:
        raise Skipped("no cover was found to save")
    covers_before["One More Time"] = cover_shown("One More Time")
    key(KEY_CTRL, KEY_S)
    allow_system_dialog(40)
    # "Saved" shows for four seconds, which a screen read every two may miss; the foot saying
    # nothing is changed any more says the same, and stays.
    expect("^Saved|^No changes$", 60)
    return check_written(["01 - One More Time.flac"])


def library_shows_saved_cover():
    """The row of the song just saved shows the cover written to it, not the one it had: the
    picture a list caches under a song is the picture the file had when it was read."""
    if "One More Time" not in covers_before:
        raise Skipped("nothing was saved to show")
    return rows_show_new_covers(["One More Time"], covers_before)


def same_cover_again():
    """The cover finder puts the file's own picture first. Chosen, it is no change; and whatever a
    source gives, a save leaves the foot saying nothing is changed, never a change it can't write."""
    if not cover_search_worked:
        raise Skipped("no cover was found to save")
    expect("^No changes$", 30)
    key(KEY_CTRL, KEY_K)
    use, _ = read_cover_sheet(timeout=60)
    tap(use)
    expect_gone("Other covers|Use this cover", 20)
    # The picture is downloaded before the draft has it: wait for the foot to settle.
    time.sleep(6)
    nodes = screen()
    screenshot("the-same-cover-again")
    if find("^No changes$", nodes):
        return "the picture the file already has is no change"
    if not find(r"^\d+ changes?$", nodes):
        raise Failed(f"the foot says neither: {said_on(nodes)[:30]}")
    key(KEY_CTRL, KEY_S)
    # The system asks once for each file whether the app may change it, and it was asked for
    # this one at the save before: it may not ask again.
    deadline = time.time() + 60
    while time.time() < deadline:
        nodes = screen()
        if not any(node.package == PACKAGE for node in nodes) and find(r"^(Allow|ALLOW)$", nodes):
            tap(find(r"^(Allow|ALLOW)$", nodes)[0])
            continue
        if find("^No changes$", nodes):
            return "the source gave another copy of the picture; saving it left nothing changed"
        time.sleep(1)
    raise Failed(f"a minute after Ctrl+S the foot does not say No changes: {said_on(screen())[-12:]}")


def check_written(file_names, folder=MUSIC):
    """Each file has a cover now, and everything else it had is as it was."""
    notes = []
    for file_name in file_names:
        after = probe(pull(file_name, folder))
        was = before[file_name]
        if not after["pictures"]:
            raise Failed(f"{file_name} has no picture after the save")
        if after["sound"] != was["sound"] or not after["sound"]:
            raise Failed(f"the sound of {file_name} changed: {was['sound']} -> {after['sound']}")
        if after["tags"] != was["tags"]:
            raise Failed(f"the tags of {file_name} changed: {was['tags']} -> {after['tags']}")
        codec, width, height = after["pictures"][0]
        notes.append(f"{file_name}: {codec} {width}x{height}, tags and sound untouched")
    return "; ".join(notes)


def choosing_replaces():
    """On a wide window each song chosen takes the place of the last: one Ctrl+W, and it is gone."""
    tap(row("Aerodynamic"))
    expect("Made from a lossy file|Probably made from a lossy file", 120)
    tap(row("Digital Love"))
    deadline = time.time() + 40
    while not [node for node in find("Digital Love") if node.is_field]:
        if time.time() > deadline:
            raise Failed("Digital Love did not open")
        time.sleep(1)
    key(KEY_CTRL, KEY_W)
    expect("Pick a song", 20)
    return "three songs chosen one after another, and one Ctrl+W leaves none open"


def unsaved_stays():
    """A song with unsaved changes is not replaced: the next opens over it, and Back finds it."""
    tap(row("One More Time"))
    title = None
    deadline = time.time() + 40
    while title is None:
        fields = [node for node in screen() if node.is_field and node.text == "One More Time"]
        if fields:
            title = fields[0]
        elif time.time() > deadline:
            raise Failed("One More Time did not open")
        else:
            time.sleep(1)
    tap(title)
    key(KEY_MOVE_END)
    shell("input text _x")
    time.sleep(1.5)
    edited = next(
        (node.text for node in screen() if node.is_field and node.text.startswith("One More Time")),
        None,
    )
    if not edited or edited == "One More Time":
        raise Failed(f"the title could not be changed: {edited!r}")
    tap(row("Aerodynamic"))
    expect("Made from a lossy file|Probably made from a lossy file", 120)
    key(KEY_CTRL, KEY_W)
    deadline = time.time() + 30
    while not [node for node in screen() if node.is_field and node.text == edited]:
        if time.time() > deadline:
            raise Failed(f"the song with unsaved changes was not under the next: {said_on(screen())[:30]}")
        time.sleep(1)
    screenshot("the-unsaved-song-is-back")
    key(KEY_CTRL, KEY_W)
    tap(expect("^Discard$", 15))
    expect("Pick a song", 20)
    return f"'{edited}' waited under the next song, and closing it asked first"


def edit_together():
    # Out of the editor first: Ctrl+A selects the songs only while the library has the keys.
    if not find("Pick a song"):
        key(KEY_CTRL, KEY_W)
        discard = find("^Discard$")
        if discard:
            # A step before left a change unsaved, and the editor rightly asks.
            tap(discard[0])
    expect("Pick a song", 20)
    key(KEY_CTRL, KEY_A)
    expect(r"^4 (songs )?selected$", 20)
    screenshot("all-selected")
    key(KEY_CTRL, KEY_E)
    expect("Edit 4 songs", 30)
    expect("Find a cover online", 30)
    return "Ctrl+A selects them, Ctrl+E opens them together"


def find_cover_for_album():
    key(KEY_CTRL, KEY_K)
    use, reasons = read_cover_sheet()
    screenshot("covers-for-an-album")
    if not use.says("Use for all 4 songs"):
        raise Failed(f"the button says {use.text!r}")
    tap(use)
    expect("Every song will get this cover", 90)
    return f"the cover is set for the four. The sheet said: {reasons[:10]}"


# The songs of the batch that had no cover before it.
BATCH_NEWLY_COVERED = ["Aerodynamic", "Digital Love", "Harder, Better, Faster, Stronger"]


# The first song's picture before the batch, by its size: the batch may give it another.
first_picture_before = []


def apply_to_all():
    if not cover_search_worked:
        raise Skipped("no cover was found to apply")
    for title in BATCH_NEWLY_COVERED + ["One More Time"]:
        covers_before[title] = cover_shown(title)
    first_picture_before[:] = probe(pull("01 - One More Time.flac"))["pictures"]
    key(KEY_CTRL, KEY_S)
    allow_system_dialog(40)
    expect("^Done$", 180)
    nodes = screen()
    screenshot("all-saved")
    # A song that has this very cover already has nothing to save, and is left as it is: the
    # song given a cover on its own, when the sources chose the same picture for both.
    saved = next((node.text for node in nodes if re.search(r"songs? saved", node.text)), "")
    if find("couldn.t be saved", nodes) or not saved:
        raise Failed(f"the batch did not save every song: {said_on(nodes)}")
    tap(expect("^Finish$", 10))
    expect("One More Time", 30)
    return f"{saved}. " + check_written(list(SONGS.values()))


def library_shows_batch_covers():
    """Every song the batch gave a cover shows it in its row, where it showed none."""
    if not all(title in covers_before for title in BATCH_NEWLY_COVERED):
        raise Skipped("the batch was not applied")
    return rows_show_new_covers(BATCH_NEWLY_COVERED, covers_before)


def library_shows_replaced_cover():
    """The song that had a cover before the batch shows the one the batch gave it instead, when
    that is another picture: the thumbnail MediaStore keeps of a file is renewed when the file is
    written, and the list asks for it afresh."""
    if "One More Time" not in covers_before:
        raise Skipped("the batch was not applied")
    after = probe(pull("01 - One More Time.flac"))["pictures"]
    if after == first_picture_before:
        raise Skipped(f"the batch gave the first song the picture it had: {after}")
    return (
        f"{first_picture_before} became {after}; "
        + rows_show_new_covers(["One More Time"], covers_before)
    )


def leave_batch():
    """Back to the library, whatever the steps before left open."""
    for _ in range(6):
        nodes = screen()
        if find("^Find a cover$", nodes):
            key(KEY_ESCAPE)
        elif find("^Finish$", nodes):
            tap(find("^Finish$", nodes)[0])
        elif find("^Edit 4 songs$", nodes):
            key(KEY_CTRL, KEY_W)
        elif find(LIBRARY, nodes):
            if find(SELECTED, nodes):
                tap(expect("Stop selecting", 10))
            return "the library is in front"
        time.sleep(1.5)
    raise Failed(f"could not get back to the library: {said_on(screen())[:30]}")


def follows_the_display():
    """A window resized, another display, another density, night: the activity stays itself."""
    # Which object the activity is, when the system says. What was open staying open says the
    # same: the list of shortcuts belongs to the activity, and a new one would start without it.
    instance = activity_instance()
    key(KEY_F1)
    expect_shortcuts()
    notes = []
    changes = [
        ("a smaller window", ["wm size 1280x800"]),
        ("another density", ["wm density 213"]),
        ("a phone held upright", ["wm size 1080x1920", "wm density 420"]),
        ("night", ["cmd uimode night yes"]),
        ("the display it started on", ["wm size reset", "wm density reset"]),
        ("day", ["cmd uimode night no"]),
    ]
    try:
        for name, commands in changes:
            for command in commands:
                shell(command)
            time.sleep(4)
            now = activity_instance()
            if instance and now and now != instance:
                raise Failed(f"{name}: the activity was recreated ({instance} -> {now})")
            try:
                expect_shortcuts()
            except Failed as closed:
                raise Failed(f"{name}: what was open is gone. {closed}")
            screenshot("display-" + slug(name))
            notes.append(name)
    finally:
        shell("wm size reset")
        shell("wm density reset")
        shell("cmd uimode night no")
        time.sleep(2)
    key(KEY_ESCAPE)
    expect_shortcuts(shown=False)
    return f"the same activity ({instance}), with its list still open, through: {', '.join(notes)}"


def compact_layout():
    """On a window as narrow as a phone, one pane at a time."""
    shell("wm size 1080x1920")
    shell("wm density 420")
    try:
        time.sleep(4)
        nodes = screen()
        if find("Pick a song", nodes):
            raise Failed("the two panes are still side by side on a narrow window")
        expect("One More Time", 15)
        tap(row("Aerodynamic"))
        expect("Made from a lossy file|Probably made from a lossy file", 120)
        screenshot("narrow-editor")
        key(KEY_CTRL, KEY_W)
        expect(LIBRARY, 20)
    finally:
        shell("wm size reset")
        shell("wm density reset")
        time.sleep(3)
    return "the library alone, then the editor alone, and Ctrl+W back"


class Gesture:
    """What a mouse, and the keys held with it, do in one go, sent as one file to the system's
    uinput command: the two devices are plugged in, used, and taken out again when it is done.

    A uinput process kept running for the whole test was found gone between two steps, with
    nothing said. One per gesture has nothing to outlive. Each device keeps its own clock (its
    delays add up from when it was plugged in), so every event is put at a time counted from
    then, and each device is given the delays that bring it there.
    """

    EV_SYN, EV_KEY, EV_REL = 0, 1, 2
    REL_X, REL_Y = 0, 1
    BTN_LEFT, BTN_RIGHT = 272, 273
    KEY_ESC, KEY_LEFTCTRL, KEY_LEFTSHIFT = 1, 29, 42
    MOUSE, KEYS = 1, 2

    # The system takes a moment to set a new device up; what is sent before is lost.
    SETTLE_MS = 1500

    def __init__(self):
        self.commands = [
            {
                "id": self.MOUSE,
                "command": "register",
                "name": "Smoke test mouse",
                "vid": 6353,
                "pid": 20001,
                "bus": "usb",
                "configuration": [
                    {"type": 100, "data": [self.EV_KEY, self.EV_REL]},
                    {"type": 101, "data": [self.BTN_LEFT, self.BTN_RIGHT]},
                    {"type": 102, "data": [self.REL_X, self.REL_Y]},
                ],
            },
            {
                "id": self.KEYS,
                "command": "register",
                "name": "Smoke test keys",
                "vid": 6353,
                "pid": 20002,
                "bus": "usb",
                "configuration": [
                    {"type": 100, "data": [self.EV_KEY]},
                    {"type": 101, "data": [self.KEY_ESC, self.KEY_LEFTCTRL, self.KEY_LEFTSHIFT]},
                ],
            },
        ]
        self.now = self.SETTLE_MS
        self.clock = {self.MOUSE: 0, self.KEYS: 0}

    def _send(self, device, events):
        behind = self.now - self.clock[device]
        if behind > 0:
            self.commands.append({"id": device, "command": "delay", "duration": behind})
            self.clock[device] = self.now
        self.commands.append({"id": device, "command": "inject", "events": events})

    def wait(self, milliseconds):
        self.now += milliseconds
        return self

    def to_corner(self):
        """Top left, wherever the pointer was: the one place it can be sent without seeing it."""
        for _ in range(40):
            self._send(self.MOUSE, [self.EV_REL, self.REL_X, -200, self.EV_REL, self.REL_Y, -200, 0, 0, 0])
            self.wait(4)
        return self.wait(300)

    def move(self, dx, dy):
        """Slowly, a count at a time: a mouse moved fast is sped up further by the system, by how
        much depending on how the events bunch up on the way, and that changes from run to run."""
        steps = max(abs(dx), abs(dy), 1)
        done_x = done_y = 0
        for index in range(1, steps + 1):
            x, y = dx * index // steps, dy * index // steps
            self._send(
                self.MOUSE,
                [self.EV_REL, self.REL_X, x - done_x, self.EV_REL, self.REL_Y, y - done_y, 0, 0, 0],
            )
            self.wait(8)
            done_x, done_y = x, y
        return self.wait(300)

    def click(self, button, after=400):
        self._send(self.MOUSE, [self.EV_KEY, button, 1, 0, 0, 0])
        self.wait(90)
        self._send(self.MOUSE, [self.EV_KEY, button, 0, 0, 0, 0])
        return self.wait(after)

    def hold(self, key_code, down):
        self._send(self.KEYS, [self.EV_KEY, key_code, 1 if down else 0, 0, 0, 0])
        return self.wait(150)

    def _file(self, linger):
        """The commands, with both devices kept until the last event and [linger] after it."""
        self.wait(linger)
        commands = list(self.commands)
        for device in (self.MOUSE, self.KEYS):
            behind = self.now - self.clock[device]
            if behind > 0:
                commands.append({"id": device, "command": "delay", "duration": behind})
        path = os.path.join(out_dir, "gesture.json")
        with open(path, "w", encoding="utf-8") as file:
            file.write("\n".join(json.dumps(command) for command in commands) + "\n")
        adb("push", path, "/data/local/tmp/gesture.json", timeout=60)
        return self.now

    def run(self, linger=1000):
        """Does it all, and returns once the devices are gone again."""
        total = self._file(linger)
        said = adb("shell", "uinput /data/local/tmp/gesture.json", timeout=total / 1000 + 60)
        if said.strip():
            log(f"    uinput: {said.strip()[:300]}")
        time.sleep(0.5)

    def start(self, linger):
        """Does it all in the background: the devices stay for [linger] after the last event."""
        self._file(linger)
        return subprocess.Popen(
            ["adb", "shell", "uinput /data/local/tmp/gesture.json"],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )


class LiveMouse:
    """A mouse kept plugged in for the length of one step, moved a little at a time and looked at
    in between, as a hand moves a mouse watching the screen.

    How far the pointer goes for a count changes from one move to the next, by a quarter and
    more: the system speeds a mouse up by how fast its events come, and on a busy emulator they
    come in bursts. Aimed in one go, a click misses a row as often as not. So the pointer is
    moved, where it got to is read from the input system, and the rest of the way is moved
    again, until it is there.
    """

    def __init__(self):
        self.process = subprocess.Popen(
            ["adb", "shell", "uinput -"],
            stdin=subprocess.PIPE,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        self.since = time.time()
        for command in Gesture().commands:
            self._write(command)
        time.sleep(Gesture.SETTLE_MS / 1000 + 0.5)
        self.gain = [3.0, 3.0]

    def __enter__(self):
        return self

    def __exit__(self, *_):
        self.close()

    def close(self):
        try:
            self.process.stdin.close()
            self.process.wait(timeout=15)
        except Exception:  # Gone already, or stuck: either way it is not wanted any more.
            self.process.kill()
        time.sleep(0.5)

    def _write(self, command):
        if self.process.poll() is not None:
            raise Skipped("the made-up mouse was taken out by the system")
        self.process.stdin.write((json.dumps(command) + "\n").encode())
        self.process.stdin.flush()

    def _inject(self, device, events, then_ms=0):
        self._write({"id": device, "command": "inject", "events": events})
        if then_ms:
            self._write({"id": device, "command": "delay", "duration": then_ms})

    def move(self, dx, dy, pace_ms=8):
        """By (dx, dy) counts, one count at a time, and back once the system has had them."""
        steps = max(abs(dx), abs(dy), 1)
        done_x = done_y = 0
        for index in range(1, steps + 1):
            x, y = dx * index // steps, dy * index // steps
            self._inject(
                Gesture.MOUSE,
                [Gesture.EV_REL, Gesture.REL_X, x - done_x, Gesture.EV_REL, Gesture.REL_Y,
                 y - done_y, 0, 0, 0],
                pace_ms,
            )
            done_x, done_y = x, y
        time.sleep(steps * pace_ms / 1000 + 0.8)

    def go_to(self, x, y, tolerance=6):
        """Brings the pointer to within [tolerance] of (x, y). Says where it got to."""
        at = pointer_position(self.since)
        if at is None:
            # It has not moved since it was plugged in, and is wherever the system put it: the
            # top left corner is a place it can be sent without seeing it.
            for _ in range(12):
                self._inject(
                    Gesture.MOUSE,
                    [Gesture.EV_REL, Gesture.REL_X, -200, Gesture.EV_REL, Gesture.REL_Y, -200,
                     0, 0, 0],
                    4,
                )
            time.sleep(1)
            self.move(4, 4)
            at = pointer_position(self.since)
            if at is None:
                raise Skipped("where the pointer is cannot be read from the input system")
        for _ in range(10):
            off_x, off_y = x - at[0], y - at[1]
            if abs(off_x) <= tolerance and abs(off_y) <= tolerance:
                return at
            sent_x, sent_y = round(off_x / self.gain[0]), round(off_y / self.gain[1])
            sent_x = sent_x or (1 if off_x > tolerance else -1 if off_x < -tolerance else 0)
            sent_y = sent_y or (1 if off_y > tolerance else -1 if off_y < -tolerance else 0)
            self.move(sent_x, sent_y)
            now = pointer_position(self.since) or at
            # What it went for each count this time, to aim the rest by: within sense, since a
            # short move says little, and one stopped by the edge of the screen less.
            for axis, sent in ((0, sent_x), (1, sent_y)):
                went = now[axis] - at[axis]
                if abs(sent) >= 3 and went * sent > 0:
                    self.gain[axis] = min(8.0, max(0.5, went / sent))
            at = now
        return at

    def hold(self, key_code, down):
        self._inject(Gesture.KEYS, [Gesture.EV_KEY, key_code, 1 if down else 0, 0, 0, 0])
        time.sleep(0.15)

    def click(self, button, held=None):
        """A click where the pointer is, with the key [held] down for no longer than it takes:
        a key held longer repeats."""
        if held:
            self.hold(held, True)
        self._inject(Gesture.MOUSE, [Gesture.EV_KEY, button, 1, 0, 0, 0], 90)
        self._inject(Gesture.MOUSE, [Gesture.EV_KEY, button, 0, 0, 0, 0])
        time.sleep(0.2)
        if held:
            self.hold(held, False)
        time.sleep(0.6)

    def press(self, button):
        """The button goes down where the pointer is, and stays down until [release]: with
        [go_to] in between, it is a drag."""
        self._inject(Gesture.MOUSE, [Gesture.EV_KEY, button, 1, 0, 0, 0], 90)
        time.sleep(0.4)

    def release(self, button):
        self._inject(Gesture.MOUSE, [Gesture.EV_KEY, button, 0, 0, 0, 0])
        time.sleep(0.8)


def mouse_events(newer_than_ms=20000):
    """The last mouse events the system handed to a window, oldest first: [(action, x, y)].

    The input system keeps the last few events it dispatched, with where the pointer was, in the
    display's own coordinates. That is the one way to see the pointer: the screen dump leaves it
    out, and a picture of the screen does not say where its tip is.
    """
    lines = shell("dumpsys input", timeout=60).splitlines()
    start = next((i for i, line in enumerate(lines) if line.strip().startswith("RecentQueue")), None)
    if start is None:
        return []
    events, depth = [], None
    for line in lines[start + 1 :]:
        indent = len(line) - len(line.lstrip())
        depth = indent if depth is None else depth
        if not line.strip() or indent < depth:
            break
        if "source=MOUSE" not in line:
            continue
        action = re.search(r"action=(\w+)", line)
        point = re.search(r"pointers=\[0: \((-?[\d.]+), (-?[\d.]+)\)", line)
        age = re.search(r"age=([\d.]+)ms", line)
        if action and point and (not age or float(age.group(1)) < newer_than_ms):
            events.append((action.group(1), float(point.group(1)), float(point.group(2))))
    return events


def pointer_position(since):
    """Where the made-up mouse's pointer is: the place of its last event since [since], when the
    mouse was plugged in. Another mouse's, taken out before, is somewhere else."""
    events = mouse_events(newer_than_ms=(time.time() - since) * 1000)
    return (round(events[-1][1]), round(events[-1][2])) if events else None


def landed_row(point, listed):
    """Which of the rows [listed] a point is on, if any."""
    if point is None:
        return None
    x, y = point
    return next(
        (
            index
            for index, node in enumerate(listed)
            if node.left <= x < node.right and node.top <= y < node.bottom
        ),
        None,
    )


mouse_works = False
# The second button opened a song's menu, which the next step chooses from.
menu_opened = False


def plug_mouse():
    """A mouse and keys plugged in, with the app open: the activity stays the same object."""
    global mouse_works
    instance = activity_instance()
    gesture = Gesture().start(linger=8000)
    try:
        time.sleep(4)
        devices = shell("dumpsys input", timeout=60)
        with open(os.path.join(out_dir, "input-devices.txt"), "w", encoding="utf-8") as file:
            file.write(devices)
        if "Smoke test mouse" not in devices:
            raise Skipped("the system did not take the made-up mouse")
        now = activity_instance()
        if instance and now and now != instance:
            raise Failed(f"plugging a mouse in recreated the activity ({instance} -> {now})")
        expect("One More Time", 15)
    finally:
        gesture.wait(timeout=60)
    mouse_works = True
    return "a mouse and keys were plugged in and taken out, and the activity stayed as it was"


def rows():
    """The songs' rows as they are on screen, from the top one down."""
    nodes = screen()
    return sorted((row(title, nodes) for title in SONGS), key=lambda node: node.top)


def aim(node):
    """Where in a row to click: its middle, left of the button at its end."""
    return node.left + (node.right - node.left) // 2, (node.top + node.bottom) // 2


def right_click_menu():
    """The second button opens the song's menu, and does not open the song."""
    global menu_opened
    if not mouse_works:
        raise Skipped("no mouse")
    listed = rows()
    target = aim(listed[1])
    with LiveMouse() as mouse:
        at = mouse.go_to(*target)
        if landed_row(at, listed) != 1:
            raise Skipped(f"the pointer could not be brought to a song: it is at {at}")
        mouse.click(Gesture.BTN_RIGHT)
    time.sleep(1)
    nodes = screen()
    titles = "|".join(re.escape(title) for title in SONGS)
    if [node for node in nodes if node.is_field and node.says(titles)]:
        raise Failed("the second button opened the song instead of its menu")
    if find(SELECTED, nodes):
        raise Failed("the second button selected the song instead of opening its menu")
    if not find("Open in a new window", nodes):
        raise Failed(f"a right click on a song, at {at}, opened no menu: {said_on(nodes)[:30]}")
    wanted = ["Edit tags", "Play", "Select", "Open in a new window"]
    missing = [item for item in wanted if not find("^" + item + "$", nodes)]
    if missing:
        raise Failed(f"the menu lacks {missing}: {said_on(nodes)}")
    menu_opened = True
    screenshot("right-click-menu")
    return f"a right click at {at}, on the second song, opened its menu: {said_on(nodes)}"


def open_in_new_window():
    if not menu_opened:
        raise Skipped("no menu to choose from")
    tap(expect("^Open in a new window$", 10))
    time.sleep(4)
    tasks = shell(f"dumpsys activity {PACKAGE}", timeout=60)
    if "ExternalEditorActivity" not in tasks:
        raise Failed("no second editor was started")
    nodes = screen()
    fields = [node for node in nodes if node.is_field]
    if not fields:
        raise Failed(f"the new window shows no editor: {said_on(nodes)[:30]}")
    screenshot("a-song-in-its-own-window")
    key(KEY_CTRL, KEY_W)
    time.sleep(2)
    expect(LIBRARY, 20)
    return "the song opened in an activity of its own, and Ctrl+W closed it"


def selected_songs(nodes):
    """The songs whose rows are checked, from the top one down."""
    checked = [node for node in nodes if node.checked]
    found = []
    for title in SONGS:
        shown = [node for node in find(re.escape(title), nodes) if not node.is_field]
        if shown and any(node.holds(re.escape(title)) for node in checked):
            found.append((min(node.top for node in shown), title))
    return [title for _, title in sorted(found)]


def ctrl_click_selects():
    if not mouse_works:
        raise Skipped("no mouse")
    order = [next(title for title in SONGS if node.holds(re.escape(title))) for node in rows()]
    with LiveMouse() as mouse:
        # Ctrl+click on the first song: it is selected, and it alone.
        listed = rows()
        at = mouse.go_to(*aim(listed[0]))
        if landed_row(at, listed) != 0:
            raise Skipped(f"the pointer could not be brought to the first song: it is at {at}")
        mouse.click(Gesture.BTN_LEFT, held=Gesture.KEY_LEFTCTRL)
        nodes = screen()
        chosen = selected_songs(nodes)
        if chosen != [order[0]] or not find(r"^1 (song )?selected$", nodes):
            raise Failed(f"a Ctrl+click on {order[0]!r} selected {chosen}: {said_on(nodes)[:24]}")
        # Shift+click on the third: the three from the first to it, and no other. The rows have
        # moved, under the bar of the selection.
        listed = rows()
        at = mouse.go_to(*aim(listed[2]))
        if landed_row(at, listed) != 2:
            raise Skipped(f"the pointer could not be brought to the third song: it is at {at}")
        mouse.click(Gesture.BTN_LEFT, held=Gesture.KEY_LEFTSHIFT)
        nodes = screen()
        now = selected_songs(nodes)
    if now != order[:3] or not find(r"^3 (songs )?selected$", nodes):
        raise Failed(f"a Shift+click on {order[2]!r} left {now} selected: {said_on(nodes)[:24]}")
    screenshot("ctrl-and-shift-click")
    tap(expect("Stop selecting", 10))
    expect_gone(SELECTED, 15)
    return (
        f"Ctrl+click selected {order[0]!r} alone; Shift+click on {order[2]!r} selected the three "
        f"from one to the other"
    )


def tooltip_on_hover():
    if not mouse_works:
        raise Skipped("no mouse")
    icon = find("^More options$")
    if not icon:
        raise Skipped("no icon button to rest on")
    button = icon[0].pressed_as()
    with LiveMouse() as mouse:
        at = mouse.go_to(*aim(button))
        if landed_row(at, [button]) is None:
            raise Skipped(f"the pointer could not be brought to the button: it is at {at}")
        # Resting there a little longer than a tooltip takes to show.
        time.sleep(2.5)
        picture = shots + 1
        screenshot("pointer-resting-on-a-button")
    # A tooltip is a window of its own, which the dump of the window in front does not show: the
    # picture is what says whether it came.
    return f"the pointer rested at {at}, on the button: see picture {picture}"


# --- What the round of changes after the first run on a device is tried with -------------------
#
# The steps from here to the SD card's were written from the code of the screens, before any run on
# a device that has what they try: none is required, and the first runs say which hold. The run is
# long already. Each step says here what it costs and how much it matters, and a run that is late
# leaves out the ones that matter least, and says so in the report.

# The seconds a run may take, as far as these steps can tell, before some are left out. The job
# gives the whole run an hour, the emulator's start included; forty minutes is the aim. A run that
# should try them all, once, can be given more: SMOKE_TIME_LIMIT=3300.
TIME_LIMIT = float(os.environ.get("SMOKE_TIME_LIMIT", "2400"))

# The first run on a device took this many seconds in all, and this many of them over installing the
# build before this one, which waits on the network more than the machine decides.
FIRST_RUN = 1629
FIRST_UPDATE = 102
UPDATE_STEP = "Installed over the build before it, it opens what that one left"

# Where these steps stand in the run: the seconds the first run had taken when it got there.
STANDS = {
    "search": 347,
    "batch": 631,
    "mouse": 823,
    "card": 924,
    "turkish": 951,
    "glass": 1246,
    "performance": 1627,
}

# Each step: where it stands, what it costs (the seconds it should take on the machine of the first
# run, its picture taken after it included), how much it matters (1 the most), in the order main()
# calls them. The costs are counted, not measured: smoke.log has the steps' real times once a run
# has been, and these are to be set from it.
NEW_STEPS = {
    "esc in the search": ("search", 20, 3),
    "about": ("search", 27, 2),
    "metadata": ("batch", 45, 3),
    "back arrow": ("mouse", 38, 1),
    "back at the start": ("mouse", 30, 2),
    "editor follows": ("mouse", 35, 1),
    "boxes": ("mouse", 56, 1),
    "rectangle": ("mouse", 45, 2),
    "cover leaves": ("card", 45, 2),
    "album artist": ("card", 52, 1),
    "fake boxes": ("card", 30, 3),
    "turkish about": ("turkish", 27, 3),
    "player": ("glass", 30, 3),
    "late songs": ("glass", 30, 1),
    "no cover": ("glass", 52, 2),
    "garbled list": ("glass", 24, 1),
    "editor repair": ("glass", 44, 1),
    "batch repair": ("glass", 50, 1),
    "album menu": ("glass", 52, 2),
    "merge": ("glass", 70, 1),
    "long album": ("glass", 60, 4),
    "by hand": ("glass", 78, 4),
    "big selection": ("performance", 45, 3),
}

# How long each step took, whole, by its name; and the steps of this part that were tried, with
# the seconds they took, which the costs above are corrected by.
step_times = {}
new_steps_run = []
running_new = [None]


def machine_speed(stands):
    """How much slower than the first run's machine this one is (or faster), by the steps that were
    there before these ones: the time they have taken so far over the time they took then, leaving
    out the step of the update, which depends on the network. Kept within what a machine can
    be."""
    elapsed = time.time() - started
    mine = elapsed - sum(took for _, took in new_steps_run) - step_times.get(UPDATE_STEP, 0.0)
    theirs = STANDS[stands] - FIRST_UPDATE
    return min(2.0, max(0.7, mine / max(theirs, 60.0)))


def time_short(key):
    """Why the step [key] is left out, when the run is too late for it; None when it has the time.
    What the run has left, once the steps that were there before these have taken what they will,
    is spent on the steps still to come that matter most, in the order they stand, and this one is
    left out when it is not among those that fit."""
    stands, _, _ = NEW_STEPS[key]
    speed = machine_speed(stands)
    elapsed = time.time() - started
    left = TIME_LIMIT - elapsed - speed * (FIRST_RUN - STANDS[stands])
    if new_steps_run:
        counted = sum(NEW_STEPS[name][1] for name, _ in new_steps_run)
        scale = min(2.5, max(0.6, sum(took for _, took in new_steps_run) / counted))
    else:
        scale = speed
    names = list(NEW_STEPS)
    ahead = names[names.index(key) :]
    for name in sorted(ahead, key=lambda each: (NEW_STEPS[each][2], names.index(each))):
        cost = NEW_STEPS[name][1] * scale
        if cost <= left:
            left -= cost
            if name == key:
                return None
        elif name == key:
            return (
                f"left out, the run is late: {elapsed:.0f} s in, this machine about {speed:.2f} "
                f"times as slow as the first run's, and the steps of this kind that matter more "
                f"leave no room for this one under {TIME_LIMIT:.0f} s"
            )
    return None


def within(key, action):
    """[action], when the run has the time for it."""

    def tried():
        why = time_short(key)
        if why:
            raise NotBegun(why)
        running_new[0] = key
        return action()

    return tried


class Parts:
    """The checks one step makes, each tried by itself: one that fails does not hide the others,
    and the report says which held and which did not."""

    def __init__(self):
        self.held, self.broken, self.left_out = [], [], []

    def check(self, what, action):
        """Tries [action]; what it returns is what held. True when it held."""
        try:
            said = action()
        except Skipped as why:
            self.left_out.append(f"{what}: {why}")
            return False
        except Exception as error:  # Failed, or something the step did not foresee
            log(traceback.format_exc())
            reason = str(error) if isinstance(error, Failed) else f"{type(error).__name__}: {error}"
            self.broken.append(f"{what}: {reason}")
            return False
        self.held.append(f"{what}: {said}" if said else what)
        return True

    def report(self):
        held = "; ".join(self.held)
        if self.broken:
            raise Failed(
                "; ".join(self.broken) + (f". What held: {held}" if held else "")
            )
        if not self.held:
            raise Skipped("; ".join(self.left_out) or "nothing was tried")
        return held + (f". Not tried: {'; '.join(self.left_out)}" if self.left_out else "")


def launch_app():
    """The app brought to the front the way every step here starts it: the task it has is shown
    again."""
    shell(f"am start -W -n {PACKAGE}/{MAIN}", timeout=90)
    time.sleep(1.5)


def front_package():
    """The app in front, as the system says it: the package of the activity that is resumed. The
    screen's tree is asked when the system's words are not the ones expected."""
    said = shell("dumpsys activity activities", timeout=60)
    found = re.search(
        r"(?:[tT]opResumedActivity|mResumedActivity|ResumedActivity)[=:]\s*"
        r"ActivityRecord\{\S+ u\d+ ([\w.]+)/",
        said,
    )
    if found:
        return found.group(1)
    said = shell("dumpsys window", timeout=60)
    found = re.search(r"mCurrentFocus=Window\{\S+ u\d+ ([\w.]+)/", said)
    if found:
        return found.group(1)
    packages = [node.package for node in screen() if node.package]
    return PACKAGE if PACKAGE in packages else (packages[0] if packages else None)


def wait_for(check, timeout=20, what="the screen"):
    """Reads the screen until [check] holds of what it shows, and returns what was read then."""
    deadline = time.time() + timeout
    nodes = []
    while True:
        nodes = screen()
        if nodes and check(nodes):
            return nodes
        if time.time() > deadline:
            raise Failed(f"{what} did not come in {timeout} s. On screen: {said_on(nodes)[:30]}")
        time.sleep(0.5)


def saved_when(done, timeout=90):
    """Waits until the app says [done], and answers the system if it asks to change a file: it asks
    once for each file, and not again for one that was allowed earlier in the run."""
    deadline = time.time() + timeout
    nodes = []
    while time.time() < deadline:
        nodes = screen()
        if not any(node.package == PACKAGE for node in nodes) and find(r"^(Allow|ALLOW)$", nodes):
            tap(find(r"^(Allow|ALLOW)$", nodes)[0])
            continue
        if find(done, nodes):
            return nodes
        time.sleep(0.5)
    raise Failed(f"nothing says /{done}/ after {timeout} s: {said_on(nodes)[:30]}")


# --- What the steps look for on the wide window's screens ---------------------------------------
# The panel of the list ends where the editor beside it begins, and the player's bar starts under it.
PANEL_RIGHT = 1530
BAR_TOP = 1000
SONGS_PLACE = ("Songs", "Şarkılar")


def list_row(title, nodes):
    """What a click on the row or the album's tile that names [title] lands on, in the panel of the
    list: not the editor's field that says the same, nor the player's bar, which names the song that
    plays."""
    found = [
        node
        for node in nodes
        if node.text == title
        and not node.is_field
        and node.right <= PANEL_RIGHT
        and node.top < BAR_TOP
    ]
    if not found:
        raise Failed(f"no row says {title!r}: {said_on(nodes)[:30]}")
    return min(found, key=lambda node: (node.left, node.top)).pressed_as()


def boxes(nodes):
    """The check boxes of the rows of a list, from the top one down. The head of a table has one
    too, for all of them; it is not in a row. Nor are the chips over the list, which hold one
    each: a row is far wider than a chip."""
    return sorted(
        (
            node
            for node in nodes
            if node.kind.endswith("CheckBox")
            and node.parent is not None
            and node.parent.takes_click
            and node.parent.right - node.parent.left > 300
            and node.right <= PANEL_RIGHT
            and 100 <= node.top < BAR_TOP
        ),
        key=lambda node: node.top,
    )


def row_title(box):
    """The title of the song whose row has [box]: the first text in the row."""
    texts = [
        node.text for node in box.parent.within() if node.kind.endswith("TextView") and node.text
    ]
    return texts[0] if texts else ""


def list_titles(nodes):
    """The songs the list shows, from the top one down."""
    return [row_title(box) for box in boxes(nodes)]


def checked_titles(nodes):
    """The songs whose boxes are checked, from the top one down."""
    return [row_title(box) for box in boxes(nodes) if box.checked]


def selected_count(nodes):
    """How many the head says are selected ("3 songs selected", "2 albums selected"); 0 when it
    says none."""
    for node in nodes:
        found = re.fullmatch(r"(\d+) (?:songs? |albums? |artists? |folders? )?selected", node.text)
        if found:
            return int(found.group(1))
    return 0


def labeled_field(label, nodes):
    """The text field of the editor that has [label] over its text."""
    for node in nodes:
        if node.is_field and any(child.text == label for child in node.children):
            return node
    return None


def title_field(nodes):
    """The title the editor beside the library holds; None while there is no editor of a song."""
    field = labeled_field("Title", nodes)
    return field.text if field is not None else None


def sidebar_item(label, nodes):
    """The place of the sidebar that has [label]."""
    for node in nodes:
        if node.text == label and node.right <= 240 and node.parent is not None:
            return node.parent
    return None


def sidebar_count(label, nodes):
    """What the sidebar says is in its place [label]: "4", or "4 · 20%" while it is counting."""
    item = sidebar_item(label, nodes)
    if item is None:
        return None
    others = [child.text for child in item.children if child.text and child.text != label]
    return others[0] if others else None


def number_in(text):
    found = re.search(r"\d[\d,]*", text or "")
    return int(found.group().replace(",", "")) if found else None


def settled_count(label, nodes):
    """The number the sidebar counts in [label] once it is not in the middle of a check ("4 · 20%",
    "4 · waiting"): None until then."""
    said = sidebar_count(label, nodes)
    if said is None or re.search(r"[·%]|waiting", said):
        return None
    return number_in(said)


def chip_said(pattern, nodes):
    """The words of a chip over the list ("No cover (3)") and the chip, when one says [pattern]."""
    for node in nodes:
        if node.top < 100 and re.fullmatch(pattern, node.text):
            return node.text, node.pressed_as()
    return None, None


def menu_labels(nodes, anchor):
    """The words of the items of the menu that has an item saying [anchor]: a column of rows that
    take a click, each with its words first and sometimes a hint after them."""
    for node in nodes:
        if re.fullmatch(anchor, node.text):
            item = node.pressed_as()
            if item.parent is None or not item.takes_click:
                continue
            column = sorted(
                (other for other in item.parent.children if other.takes_click),
                key=lambda other: other.top,
            )
            return [
                next((n.text for n in other.within() if n.kind.endswith("TextView") and n.text), "")
                for other in column
            ]
    return []


def the_albums(nodes):
    """Whether the library is in front, with no album open over it."""
    return bool(find(LIBRARY, nodes)) and not find("^Edit all$", nodes)


def an_album(nodes):
    """Whether an album's own screen is in front: its buttons are there, and the library's places
    are not."""
    return bool(find("^Edit all$", nodes)) and not find(LIBRARY, nodes)


def albums_place(tile="Discovery"):
    """The Albums place, once the tile named [tile] is there."""
    key(KEY_CTRL, KEY_2)
    return wait_for(
        lambda found: bool(find(f"^{re.escape(tile)}$", found)) and the_albums(found),
        20,
        "the Albums place",
    )


def open_album(title, nodes):
    """The album's own screen: a tap on its tile."""
    tap(list_row(title, nodes))
    return wait_for(an_album, 20, f"the screen of {title!r}")


def click_on(mouse, node, held=None, button=None):
    """The pointer is brought to the middle of [node], and clicks."""
    at = mouse.go_to(*aim(node))
    if landed_row(at, [node]) is None:
        raise Skipped(f"the pointer could not be brought to {node}: it is at {at}")
    mouse.click(button or Gesture.BTN_LEFT, held=held)


def type_into(label, words):
    """What a field of the editor holds is replaced by [words], typed as a person types."""
    for _ in range(3):
        field = labeled_field(label, screen())
        if field is None:
            raise Failed(f"no field is labeled {label!r}")
        tap(field)
        key(KEY_CTRL, KEY_A)
        shell("input text " + words.replace(" ", "%s"))
        time.sleep(1)
        now = labeled_field(label, screen())
        if now is not None and now.text == words:
            return
        log(f"    typed {words!r} into {label!r}: it holds {now.text if now else None!r}, again")
    raise Failed(f"the field {label!r} never held {words!r}")


def tags_but(tags, *names):
    """A file's tags without the named ones (and the other names an album artist goes by)."""
    return {
        name: value
        for name, value in tags.items()
        if name not in names
        and not ("album_artist" in names and name in ("albumartist", "album artist"))
    }


def album_artist_of(tags):
    return next(
        (tags[name] for name in ("album_artist", "albumartist", "album artist") if name in tags),
        None,
    )


def same_but(after, was, *names):
    """Whether a file written by the app has what it had, except [names]: its other tags, its
    pictures and its sound."""
    if tags_but(after["tags"], *names) != tags_but(was["tags"], *names):
        raise Failed(f"other tags changed: {tags_but(was['tags'], *names)} -> {tags_but(after['tags'], *names)}")
    if after["sound"] != was["sound"] or not after["sound"]:
        raise Failed("the sound changed")
    if after["pictures"] != was["pictures"]:
        raise Failed(f"the picture changed: {was['pictures']} -> {after['pictures']}")


def stop_selecting(nodes=None):
    """The selection ended with its own button, whatever kind it is."""
    nodes = screen() if nodes is None else nodes
    button = find("Stop selecting", nodes)
    if button:
        tap(button[0])
    return button


def library_has_the_keys():
    """Out of every field first: Ctrl+A selects the songs only while the library has the keys, and
    in a field it selects the field's text. Esc empties a search and leaves it, as a person does."""
    nodes = screen()
    for _ in range(3):
        if not any(node.is_field and node.focused for node in nodes):
            break
        key(KEY_ESCAPE)
        nodes = screen()
    return nodes


def apply_all(wait=20):
    """The batch editor's button that applies, pressed once it can be: it is dimmed until something
    is changed, and a press on it before that does nothing."""

    def ready(found):
        return any(node.pressed_as().enabled for node in find(r"^Apply to all$", found))

    nodes = wait_for(ready, wait, "the button that applies, enabled")
    tap(next(node for node in find(r"^Apply to all$", nodes) if node.pressed_as().enabled))
    return nodes


# --- The songs that come late ------------------------------------------------------------------
#
# Made here, put in a folder of their own once the steps that count the songs on screen are done,
# and found by a rescan. Every one has a cover but those the cover tests are about, so that the
# count of songs without one is the number of those and the few the run had.

LATE = "/sdcard/Music/Late"
LONG_ALBUM = 30
PINK = 0xE91E63
GREY = 0x607D8B

# The garbled text, as the owner's songs had it: Turkish read as Latin-1 ("Mo?ollar" with the
# byte 0xF0), and UTF-8 read as Windows-1252 ("MoÄŸollar").
GOOD = "Moğollar"
UTF8_AS_1252 = "MoÄŸollar"
LATIN1_X = "MoXollar"


def late_specs():
    """The songs that come late: (key, file name, tags, the cover's colour or None, text to put in
    place of other text in the file). Their titles sort after the four songs' but those the garbled
    text tests are about, so that the phone's short list still shows them."""

    def song(key_name, file_name, color=GREY, patch=None, **tags):
        return (key_name, file_name, tags, color, patch)

    songs = [
        song(
            "garbled title", "garbled-title.mp3", title=UTF8_AS_1252, artist=GOOD,
            album="1968 - 2000", date="1968", track="1", genre="Rock",
        ),
        # Written as the owner's files were: Turkish in Windows-1254 where a reader takes Latin-1,
        # so that the letter is the byte 0xF0 ("Moðollar"). The tag is written with a plain letter
        # and the byte put in its place afterwards.
        song(
            "garbled b", "garbled-b.mp3", patch=(LATIN1_X, "Moðollar"), title="Zz Turkish B",
            artist="Zz Turkish", album="Zz Turkish", album_artist=LATIN1_X, date="1970", track="1",
        ),
        song(
            "garbled c", "garbled-c.mp3", title="Zz Turkish C", artist="Zz Turkish",
            album="Zz Turkish", album_artist=UTF8_AS_1252, date="1970", track="2",
        ),
        song(
            "control", "control.mp3", title="Dauðalogn", artist="Sigur Rós", album="Zz Control",
            date="2002", track="1",
        ),
        song(
            "bare", "zz-bare.mp3", color=None, title="Zz Bare", artist="Zz Cover Test",
            album="Zz Bare Album", date="2021", track="1",
        ),
        song(
            "pictured 1", "zz-pictured-1.mp3", color=None, title="Zz Pictured 1",
            artist="Zz Cover Test", album="Zz Pictured", date="2021", track="1",
        ),
        song(
            "pictured 2", "zz-pictured-2.mp3", color=PINK, title="Zz Pictured 2",
            artist="Zz Cover Test", album="Zz Pictured", date="2021", track="2",
        ),
        song(
            "merge one", "zz-merge-one.mp3", title="Zz Merge One", artist="Zz Merge Artist",
            album="A Merge One", date="2022", track="1",
        ),
        song(
            "merge two", "zz-merge-two.mp3", title="Zz Merge Two", artist="Zz Merge Artist",
            album="A Merge Two", date="2022", track="1",
        ),
    ]
    for number in range(1, LONG_ALBUM + 1):
        songs.append(
            song(
                f"long {number:02d}", f"zz-long-{number:02d}.mp3", title=f"Zz Long {number:02d}",
                artist="Zz Long Artist", album="Zz Long", date="2023", track=str(number),
            )
        )
    return songs


def make_late_songs(folder, work):
    """The late songs as files in [folder] (the work they take, pictures and tag files, in [work]),
    each as what FFprobe says of it: {key: {"file": name, "title": title, "probe": what it says}}."""
    os.makedirs(folder, exist_ok=True)
    os.makedirs(work, exist_ok=True)
    pictures = {}
    made = {}
    for key_name, file_name, tags, color, patch in late_specs():
        cover = None
        if color is not None:
            if color not in pictures:
                pictures[color] = os.path.join(work, f"cover-{color:06x}.jpg")
                subprocess.run(
                    ["ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-i",
                     f"color=c=0x{color:06x}:s=600x600", "-frames:v", "1", pictures[color]],
                    check=True,
                )
            cover = pictures[color]
        meta = os.path.join(work, file_name + ".txt")
        with open(meta, "w", encoding="utf-8") as file:
            file.write(";FFMETADATA1\n" + "".join(f"{name}={value}\n" for name, value in tags.items()))
        path = os.path.join(folder, file_name)
        command = ["ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-i", "sine=frequency=330:duration=2"]
        if cover:
            command += ["-i", cover, "-i", meta, "-map", "0:a", "-map", "1:v", "-map_metadata", "2",
                        "-c:v", "copy", "-metadata:s:v", "title=Album cover",
                        "-metadata:s:v", "comment=Cover (front)", "-disposition:v", "attached_pic"]
        else:
            command += ["-i", meta, "-map", "0:a", "-map_metadata", "1"]
        # Version 3 of the tag format: the text that is plain ASCII is stored as Latin-1 there, which
        # is what the song patched below needs, and the rest as UTF-16.
        command += ["-c:a", "libmp3lame", "-b:a", "64k", "-id3v2_version", "3", path]
        subprocess.run(command, check=True)
        if patch:
            with open(path, "rb") as file:
                data = file.read()
            was, now = patch[0].encode("latin-1"), patch[1].encode("latin-1")
            if was not in data:
                raise Failed(f"{file_name} does not hold {patch[0]!r} to be put right")
            with open(path, "wb") as file:
                file.write(data.replace(was, now))
        made[key_name] = {"file": file_name, "title": tags["title"], "probe": probe(path)}
    return made


# What the late songs were, once they have come: None until they are on the device and listed.
late_state = None


# --- Esc in the search field, and who made the app ---------------------------------------------


def search_field(nodes):
    """The search field over the list."""
    fields = [node for node in nodes if node.is_field and node.top < 100]
    return fields[0] if fields else None


def esc_in_the_search_field():
    """Esc in the search field is the field's. The first takes what was typed out of it, and the
    caret stays; the second gives the focus up. Neither is Back: the library stays on the place it
    was on (it used to go to the place before)."""
    if place_shown(screen()) not in SONGS_PLACE:
        home()
    place = "Songs"
    key(KEY_CTRL, KEY_F)
    type_text("aero")
    key(KEY_ESCAPE)
    nodes = screen()
    field = search_field(nodes)
    if field is None:
        raise Failed(f"the search field is gone after the first Esc: {said_on(nodes)[:20]}")
    if field.text:
        raise Failed(f"the first Esc left {field.text!r} in the field")
    if not field.focused:
        raise Failed("the first Esc took the focus out of the field along with the text")
    if not find("^One More Time$", nodes):
        raise Failed("the list is still narrowed after the first Esc")
    key(KEY_ESCAPE)
    nodes = screen()
    field = search_field(nodes)
    if field is not None and field.focused:
        raise Failed("the field still has the focus after the second Esc")
    if not find(LIBRARY, nodes) or place_shown(nodes) not in SONGS_PLACE:
        raise Failed(
            f"the second Esc took the library from {place} to {place_shown(nodes)}: "
            f"{said_on(nodes)[:12]}"
        )
    return "the first Esc cleared the field and kept the caret in it, the second left it, and the library stayed on Songs"


def about_page(settings_label, about_label, wanted, back_label):
    """The Settings page from the sidebar, then its About page, which says who made the app; and
    back out of both."""
    nodes = screen()
    entry = [node for node in find(f"^{settings_label}$", nodes) if node.right <= 240]
    if not entry:
        raise Failed(f"the sidebar has no {settings_label!r}: {said_on(nodes)[:30]}")
    try:
        tap(entry[0].pressed_as())
        nodes = wait_for(lambda found: bool(find(f"^{about_label}$", found)), 15, "the Settings")
        tap(find(f"^{about_label}$", nodes)[0].pressed_as())
        try:
            nodes = wait_for(lambda found: bool(find(f"^{wanted}$", found)), 8, "the About page")
        except Failed:
            # Below the fold of a short window.
            shell("input swipe 960 800 960 300 300")
            nodes = wait_for(lambda found: bool(find(f"^{wanted}$", found)), 10, "the About page")
        screenshot("about-" + slug(wanted)[:20])
        return find(f"^{wanted}$", nodes)[0].text
    finally:
        for _ in range(4):
            back = find(back_label)
            if not back:
                break
            tap(back[0])
            time.sleep(0.8)


def about_says_who():
    """Settings, About: the page names who developed the app."""
    words = about_page("Settings", "About", "Developed by Project Ark", BACK_ARROW)
    return f"the About page says {words!r}"


# What the About page said in Turkish, from the step that speaks Turkish: None until it is tried,
# then (whether it held, or None when it was left out, and what it said).
turkish_about = None


def about_in_turkish():
    """Called in the Turkish step, where the app speaks Turkish already. What happens is kept for the
    step that reports it: this never fails the step it is called in."""
    global turkish_about
    why = time_short("turkish about")
    if why:
        turkish_about = (None, why)
        return
    began_at = time.time()
    try:
        words = about_page("Ayarlar", "Hakkında", "Project Ark tarafından geliştirildi", r"^Geri$")
        turkish_about = (True, f"the About page says {words!r}")
    except Exception as error:  # Not the Turkish step's to fail.
        turkish_about = (False, f"{type(error).__name__}: {error}")
        log(traceback.format_exc())
    new_steps_run.append(("turkish about", time.time() - began_at))


def turkish_about_reported():
    if turkish_about is None:
        raise NotBegun("the Turkish step did not get as far as the About page")
    held, words = turkish_about
    if held is None:
        raise NotBegun(words)
    if not held:
        raise Failed(words)
    return words


# --- Find metadata for the album (online) -------------------------------------------------------


def find_metadata_for_album():
    """Ctrl+L in the batch editor of the four songs opens the sheet that looks the album up; it
    lists what the sources found, and Esc closes it with nothing filled in."""
    library_has_the_keys()
    key(KEY_CTRL, KEY_A)
    key(KEY_CTRL, KEY_E)
    wait_for(
        lambda found: bool(find(r"^Edit 4 songs$", found)), 30, "the batch editor of the four songs"
    )
    key(KEY_CTRL, KEY_L)
    sheet = r"^Find the album.s metadata$"
    troubles = (
        "offline",
        "No albums found",
        "The search failed",
        "sources are busy",
        "Every source is turned off",
    )
    deadline = time.time() + 45
    while True:
        nodes = screen()
        if find(sheet, nodes):
            for trouble in troubles:
                if find(trouble, nodes):
                    home()
                    raise Skipped(f"the sources answered: {trouble}")
            if find("^Albums found$", nodes):
                break
        if time.time() > deadline:
            raise Failed(f"the sheet said nothing in 45 s: {said_on(nodes)[:30]}")
        time.sleep(1)
    found = [
        words
        for words in said_on(nodes)
        if re.search(r"match$|^Has \d+ of 4|^\d+ tracks?$|^Looked for|^Choosing one", words)
        and len(words) < 220
    ]
    screenshot("album-metadata-found")
    key(KEY_ESCAPE)
    nodes = wait_for(lambda found_: not find(sheet, found_), 15, "the sheet closed")
    if find(r"^Filled in from", nodes) or find("^Will be written to", nodes):
        raise Failed("closing the sheet with Esc filled something in")
    home()
    return f"the sheet listed what the sources found ({found[:6]}); Esc closed it with nothing filled in"


# --- Back, and what the editor beside the library shows ----------------------------------------


def pressing(how):
    """What a person presses to leave an album: the arrow in its toolbar, Esc or the Back key."""

    def press(nodes):
        if how == "the arrow":
            arrow = find(BACK_ARROW, nodes)
            if arrow:
                tap(arrow[0])
        elif how == "Esc":
            key(KEY_ESCAPE)
        else:
            key(KEY_BACK)

    return press


def album_back_arrow():
    """Leaving an album takes one press: of the arrow in its toolbar, with a song of the album open
    beside it (the owner had to press twice), and of Esc and the Back key. The albums are listed
    again, and the editor beside them is gone."""
    nodes = albums_place()
    slow, notes = [], []
    for how in ("the arrow", "Esc", "the Back key"):
        nodes = open_album("Discovery", nodes)
        beside = ""
        if how == "the arrow":
            opened = list_titles(nodes)[0]
            tap(list_row(opened, nodes))
            nodes = wait_for(
                lambda found, wanted=opened: title_field(found) == wanted,
                25,
                f"the editor of {opened!r}",
            )
            beside = f", with {opened!r} open beside it"
            screenshot("an-album-and-a-song-beside-it")
        press = pressing(how)
        press(nodes)
        deadline = time.time() + 6
        while True:
            nodes = screen()
            if the_albums(nodes) or time.time() > deadline:
                break
            time.sleep(0.5)
        if not the_albums(nodes):
            slow.append(how)
            press(nodes)
            nodes = wait_for(the_albums, 15, "the albums after a second press")
        elif beside and not find("Pick a song", nodes):
            slow.append(f"{how}: the editor stayed beside the albums")
        notes.append(f"{how}{beside}")
    key(KEY_CTRL, KEY_1)
    if slow:
        raise Failed(f"one press did not leave the album: {slow}")
    return "one press left the album: " + "; ".join(notes)


def back_at_the_start():
    """Back at the start of the app does not take it away on a desktop: the window counts as one
    while the last pointer was a mouse. A finger's touch makes it a phone's again, and then Back
    takes the app to the background. The mouse stays plugged in while Back is pressed, and the
    Back presses are enough to go through every place the library remembers (ten) and one more."""
    if not mouse_works:
        raise NotBegun("no mouse")
    with LiveMouse() as mouse:
        # The pointer moves over the app: that is all it takes for the window to feel a mouse.
        mouse.move(3, 3)
        mouse.move(-3, -3)
        shell("for i in 1 2 3 4 5 6 7 8 9 10 11 12; do input keyevent 4; sleep 0.4; done", timeout=60)
        time.sleep(1)
        stays = front_package()
    nodes = screen()
    if stays != PACKAGE or not find(LIBRARY, nodes):
        launch_app()
        raise Failed(
            f"Back at the start, with a mouse the last pointer, left the app: {stays} was in front, "
            f"and the screen said {said_on(nodes)[:12]}"
        )
    screenshot("back-at-the-start-with-a-mouse")
    # The contrast: a finger touched the screen, and Back takes the app away, as on a phone.
    shell("input tap 700 130")
    time.sleep(0.5)
    key(KEY_BACK)
    time.sleep(1)
    left = front_package()
    if left == PACKAGE:
        return (
            f"with a mouse the last pointer, Back left {stays} in front; after a tap it did too, "
            f"which the emulator's kind of window may explain: there is no contrast to show"
        )
    launch_app()
    wait_for(lambda found: bool(find(LIBRARY, found)), 25, "the library after the app came back")
    return (
        f"with a mouse the last pointer, Back left {stays} in front; after a tap, {left} was: "
        f"as on a phone"
    )


def editor_follows_the_selection():
    """The editor beside the library shows what is chosen in it: the song clicked last, not the one
    before; a song selected with Ctrl; the two selected together; and a plain mouse click ends the
    selection and shows the song clicked."""
    if not mouse_works:
        raise NotBegun("no mouse")
    nodes = screen()
    order = list_titles(nodes)
    if len(order) < 4:
        raise Failed(f"the list shows {order}")
    first, second, third, fourth = order[:4]
    # A plain click opens a song, whether a finger or a mouse made it; then the next one.
    tap(list_row(first, nodes))
    nodes = wait_for(lambda found: title_field(found) == first, 25, f"the editor of {first!r}")
    tap(list_row(second, nodes))
    nodes = wait_for(lambda found: title_field(found) == second, 25, f"the editor of {second!r}")
    if any(node.is_field and node.text == first for node in nodes):
        raise Failed(f"the editor of {first!r} is still beside the library")
    seen = [f"{second!r} replaced {first!r}"]
    with LiveMouse() as mouse:
        click_on(mouse, list_row(third, nodes), held=Gesture.KEY_LEFTCTRL)
        nodes = wait_for(
            lambda found: selected_count(found) >= 1
            and (title_field(found) == third or find(r"^Edit \d+ songs$", found)),
            25,
            f"the editor of {third!r} selected",
        )
        together = bool(find(r"^Edit \d+ songs$", nodes))
        seen.append(
            f"Ctrl+click on {third!r} selected {selected_count(nodes)} and the editor showed "
            + ("both together" if together else "it alone")
        )
        click_on(mouse, list_row(fourth, nodes), held=Gesture.KEY_LEFTCTRL)
        nodes = wait_for(
            lambda found: selected_count(found) >= 2 and find(r"^Edit \d+ songs$", found),
            25,
            "the songs selected together",
        )
        screenshot("songs-together-in-the-editor")
        seen.append(f"a second Ctrl+click: {selected_count(nodes)} selected, edited together")
        click_on(mouse, list_row(first, nodes))
        nodes = wait_for(
            lambda found: selected_count(found) == 0
            and title_field(found) == first
            and not find(r"^Edit \d+ songs$", found),
            25,
            f"the selection ended and the editor of {first!r}",
        )
        seen.append(f"a plain click on {first!r} ended the selection and opened it")
    return "; ".join(seen)


def shift_on_boxes(mouse, nodes, where, rows_wanted=3):
    """The box of the first row is clicked: that song is selected; Shift on the box of the third
    (the last, in a list of two) selects them all, from the first. Ends the selection."""
    boxed = boxes(nodes)
    count = min(rows_wanted, len(boxed))
    if count < 2:
        raise Failed(f"{where} shows {len(boxed)} check boxes: {said_on(nodes)[:30]}")
    wanted = [row_title(box) for box in boxed[:count]]
    tap(boxed[0])
    nodes = wait_for(lambda found: selected_count(found) == 1, 15, f"one song selected in {where}")
    if checked_titles(nodes) != wanted[:1]:
        raise Failed(f"in {where} the box of {wanted[0]!r} checked {checked_titles(nodes)}")
    # The rows moved up when the selection's head came: the boxes are read again.
    click_on(mouse, boxes(nodes)[count - 1], held=Gesture.KEY_LEFTSHIFT)
    nodes = wait_for(
        lambda found: selected_count(found) == count, 15, f"{count} songs selected in {where}"
    )
    if checked_titles(nodes) != wanted:
        raise Failed(
            f"in {where} Shift on the box of {wanted[-1]!r} left {checked_titles(nodes)} checked, "
            f"not {wanted}"
        )
    screenshot("shift-on-a-box-" + slug(where))
    stop_selecting(nodes)
    wait_for(
        lambda found: selected_count(found) == 0 and len(boxes(found)) >= count,
        15,
        "the selection ended",
    )
    return f"a box selected {wanted[0]!r} and Shift on the box of {wanted[-1]!r} selected the {count}"


def boxes_and_shift():
    """In an album's screen, as in the library's Songs: a click on the box of a row selects the song,
    and Shift on the box of the third selects the three from the first."""
    if not mouse_works:
        raise NotBegun("no mouse")
    parts = Parts()
    nodes = albums_place()
    nodes = open_album("Discovery", nodes)
    with LiveMouse() as mouse:
        parts.check("in an album's screen", lambda: shift_on_boxes(mouse, nodes, "an album's screen"))
        arrow = find(BACK_ARROW)
        if arrow:
            tap(arrow[0])
        wait_for(the_albums, 15, "the albums")
        key(KEY_CTRL, KEY_1)
        nodes = wait_for(lambda found: len(boxes(found)) >= 4, 15, "the Songs")
        parts.check("in the Songs", lambda: shift_on_boxes(mouse, nodes, "the Songs"))
    return parts.report()


def drag_from(mouse, listed, index, y_offset=None):
    """The pointer is brought onto the row [index] of [listed] with the button pressed there: at
    the row's middle, or [y_offset] from its top."""
    node = listed[index]
    x = node.left + (node.right - node.left) // 2
    y = node.top + y_offset if y_offset is not None else (node.top + node.bottom) // 2
    at = mouse.go_to(x, y)
    if landed_row(at, [node]) is None:
        raise Skipped(f"the pointer could not be brought to row {index + 1}: it is at {at}")
    mouse.press(Gesture.BTN_LEFT)
    return x


def drag_to_select():
    """A rectangle dragged with the mouse over the rows selects the ones it touches and opens none;
    with Ctrl held where it begins it adds to the selection; without, it replaces it; and Esc in the
    middle of it ends the rectangle and nothing else."""
    if not mouse_works:
        raise NotBegun("no mouse")
    nodes = screen()
    order = list_titles(nodes)
    if len(order) < 4:
        raise Failed(f"the list shows {order}")
    parts = Parts()
    state = {"nodes": nodes}

    def listed():
        return [box.parent for box in boxes(state["nodes"])]

    with LiveMouse() as mouse:

        def rectangle():
            rows_ = listed()
            x = drag_from(mouse, rows_, 0)
            try:
                mouse.go_to(x, (rows_[2].top + rows_[2].bottom) // 2)
                screenshot("a-rectangle-being-dragged")
            finally:
                mouse.release(Gesture.BTN_LEFT)
            state["nodes"] = wait_for(
                lambda found: selected_count(found) == 3 and find(r"^Edit 3 songs$", found),
                25,
                "three songs selected by the rectangle",
            )
            if checked_titles(state["nodes"]) != order[:3]:
                raise Failed(f"it selected {checked_titles(state['nodes'])}, not {order[:3]}")
            return "dragged over three rows it selected them, and opened none"

        def with_ctrl():
            rows_ = listed()
            mouse.hold(Gesture.KEY_LEFTCTRL, True)
            try:
                x = drag_from(mouse, rows_, 3, y_offset=8)
                try:
                    mouse.go_to(x, rows_[3].top + 30)
                finally:
                    mouse.release(Gesture.BTN_LEFT)
            finally:
                mouse.hold(Gesture.KEY_LEFTCTRL, False)
            state["nodes"] = wait_for(
                lambda found: selected_count(found) == 4 and find(r"^Edit 4 songs$", found),
                25,
                "four songs selected, the fourth added by a rectangle with Ctrl",
            )
            return "with Ctrl it added the fourth to the three"

        def without_ctrl():
            rows_ = listed()
            x = drag_from(mouse, rows_, 3, y_offset=8)
            try:
                mouse.go_to(x, rows_[3].top + 30)
            finally:
                mouse.release(Gesture.BTN_LEFT)
            state["nodes"] = wait_for(
                lambda found: selected_count(found) == 1 and title_field(found) == order[3],
                25,
                f"only {order[3]!r} selected, and open",
            )
            return "without it, only the row dragged on stayed, and its editor opened"

        def esc_in_the_middle():
            rows_ = listed()
            x = drag_from(mouse, rows_, 0)
            try:
                mouse.go_to(x, (rows_[1].top + rows_[1].bottom) // 2)
                mouse.hold(Gesture.KEY_ESC, True)
                mouse.hold(Gesture.KEY_ESC, False)
                mouse.go_to(x, (rows_[3].top + rows_[3].bottom) // 2)
            finally:
                mouse.release(Gesture.BTN_LEFT)
            state["nodes"] = wait_for(lambda found: selected_count(found) >= 1, 15, "a selection")
            chosen = checked_titles(state["nodes"])
            if chosen != order[:2]:
                raise Failed(f"it left {chosen} selected, not {order[:2]}")
            return "Esc ended the rectangle at the second row: the two it had touched stayed"

        parts.check("a rectangle", rectangle)
        parts.check("Ctrl", with_ctrl)
        parts.check("a plain drag", without_ctrl)
        parts.check("Esc in the middle", esc_in_the_middle)
    stop_selecting()
    return parts.report()


# --- A cover saved to a song takes it off the list of those without one (online) ---------------


def cover_leaves_the_list():
    """The songs without a cover are listed, and a song given one stops being: within seconds of the
    save, with no rescan and no reopening. The song is the SD card's second, which has none."""
    if not card_listed:
        raise NotBegun("no songs on an SD card")
    if not cover_search_worked:
        raise NotBegun("no cover was found to save")
    key(KEY_CTRL, KEY_1)
    nodes = wait_for(lambda found: bool(sidebar_item("No cover", found)), 15, "the sidebar")
    before_count = settled_count("No cover", nodes)
    tap(sidebar_item("No cover", nodes))
    # The check of the covers may not have reached the card's songs yet: the list fills as it goes.
    nodes = wait_for(
        lambda found: place_shown(found) == "No cover" and "Da Funk" in list_titles(found),
        60,
        "Da Funk on the list of songs without a cover",
    )
    tap(list_row("Da Funk", nodes))
    wait_for(lambda found: title_field(found) == "Da Funk", 40, "the editor of Da Funk")
    key(KEY_CTRL, KEY_K)
    use, reasons = read_cover_sheet()
    tap(use)
    expect_gone("Other covers|Use this cover", 20)
    expect(r"\d+ × \d+ px", 90)
    key(KEY_CTRL, KEY_S)
    saved_at = time.time()
    nodes = saved_when(r"^(Saved|No changes)$", 90)
    saved_after = time.time() - saved_at
    deadline = time.time() + 40
    while True:
        nodes = screen()
        if "Da Funk" not in list_titles(nodes):
            break
        if time.time() > deadline:
            raise Failed("Da Funk is still on the list of songs without a cover 40 s after the save")
        time.sleep(1)
    gone_after = time.time() - saved_at
    screenshot("the-song-left-the-no-cover-list")
    now = settled_count("No cover", nodes)
    key(KEY_CTRL, KEY_W)
    key(KEY_CTRL, KEY_1)
    return (
        f"Da Funk, saved in {saved_after:.0f} s, was off the list {gone_after:.0f} s after the tap "
        f"on Ctrl+S; the sidebar counted {before_count} and then {now}"
    )


def album_artist_written():
    """The songs of an album edited together are written with what was set for all of them: the
    album artist of the SD card's album, read back from the files, with everything else as it was."""
    if not card_listed:
        raise NotBegun("no songs on an SD card")
    folder = f"{card['root']}/{CARD_FOLDER}"
    names = list(CARD_SONGS.values())
    was = {name: probe(pull(name, folder)) for name in names}
    nodes = albums_place("Homework")
    nodes = open_album("Homework", nodes)
    tap(find("^Edit all$", nodes)[0])
    wait_for(
        lambda found: bool(find(r"^Edit 2 songs$", found))
        and labeled_field("Album artists", found) is not None,
        30,
        "the batch editor of Homework",
    )
    new = "Daft Punk and Friends"
    type_into("Album artists", new)
    apply_all()
    nodes = saved_when(r"^Done$", 180)
    saved = next((node.text for node in nodes if re.search(r"songs? saved", node.text)), "")
    if find(r"couldn.t be saved", nodes) or not saved:
        raise Failed(f"the batch did not save both songs: {said_on(nodes)}")
    screenshot("homework-saved")
    tap(find("^Finish$", nodes)[0])
    home()
    notes = []
    for name in names:
        after = probe(pull(name, folder))
        wrote = album_artist_of(after["tags"])
        if wrote != new:
            raise Failed(f"{name}: the album artist is {wrote!r} in the file, not {new!r}")
        try:
            same_but(after, was[name], "album_artist")
        except Failed as reason:
            raise Failed(f"{name}: {reason}")
        notes.append(f"{name}: album artist {album_artist_of(was[name]['tags'])!r} -> {wrote!r}")
    return f"{saved}. " + "; ".join(notes) + ". Every other tag, the picture and the sound as they were"


def boxes_in_the_fake_list():
    """A box and Shift on a box in a smart list too: the Fake FLAC list holds the two fake songs
    once the card's are there; a box selects the first, Shift on the box of the second takes both."""
    if not mouse_works:
        raise NotBegun("no mouse")
    if not card_listed:
        raise NotBegun("no songs on an SD card: the list holds one fake song, no range to take")
    home()
    nodes = open_place_of("Fake FLAC")
    if len(boxes(nodes)) < 2:
        raise Skipped(f"the Fake FLAC list holds {list_titles(nodes)}, no range to take")
    with LiveMouse() as mouse:
        done = shift_on_boxes(mouse, nodes, "the Fake FLAC list", rows_wanted=2)
    # The step after this one counts on the Songs, not on this list.
    key(KEY_CTRL, KEY_1)
    return done


# --- The full-screen player ---------------------------------------------------------------------


def full_player():
    """The player's bar opens the full-screen player: on the wide window and on a phone's layout it
    shows the song and Edit tags; Back closes it and leaves the library where it was."""
    nodes = screen()
    opener = find("^Open the player$", nodes)
    if not opener:
        play = find("^Play all$", nodes)
        if not play:
            raise Skipped("no player bar, and nothing to play")
        tap(play[0])
        nodes = wait_for(
            lambda found: bool(find("^Open the player$", found)), 20, "the player's bar"
        )
        opener = find("^Open the player$", nodes)
    try:
        tap(opener[0])
        wait_for(
            lambda found: bool(find("^Edit tags$", found)) and not find(LIBRARY, found),
            20,
            "the full-screen player",
        )
        screenshot("the-full-player-wide")
        shell("wm size 1080x1920")
        shell("wm density 420")
        try:
            time.sleep(4)
            wait_for(
                lambda found: bool(find("^Edit tags$", found)), 15, "the full-screen player on a phone"
            )
            screenshot("the-full-player-on-a-phone")
        finally:
            shell("wm size reset")
            shell("wm density reset")
            time.sleep(3)
        key(KEY_BACK)
        wait_for(
            lambda found: bool(find(LIBRARY, found)) and not find("^Edit tags$", found),
            15,
            "the library",
        )
    finally:
        shell("input keyevent 127")  # Pause: music through the steps after would be noise.
    return "the player's bar opened the full-screen player, wide and on a phone's layout; Back closed it"


# --- The late songs come: what holds no cover, what is garbled ----------------------------------


def late_ready():
    """The late songs, once they have come: a step that needs them does not begin without."""
    if late_state is None:
        raise NotBegun("the late songs did not come")
    return late_state


def push_late(folder):
    """The folder of the late songs onto the device: how many files it holds."""
    shell(f"mkdir -p {LATE}")
    adb("push", folder + "/.", LATE + "/", timeout=300)
    listed = shell(f"ls {LATE}")
    names = [name for name in sorted(os.listdir(folder)) if name.endswith(".mp3")]
    for name in names:
        if name not in listed:  # An adb that takes no folder: one by one.
            adb("push", os.path.join(folder, name), f"{LATE}/{name}", timeout=120)
    return len(names)


def late_songs_arrive():
    """The late songs are made, put in a folder of their own on the device and found by a rescan.
    What the sidebar counted before is kept, to tell what they add."""
    global late_state
    home()
    nodes = screen()
    baseline = settled_count("No cover", nodes)
    albums_before = number_in(sidebar_count("Albums", nodes))
    before = listed_count()
    folder = os.path.join(out_dir, "late")
    made = make_late_songs(folder, os.path.join(out_dir, "late-work"))
    pushed = push_late(folder)
    target = before + len(made)
    began = time.time()
    key(KEY_CTRL, KEY_R)
    asked_again = False
    note = ""
    while True:
        now = listed_count()
        if now >= target:
            break
        if time.time() > began + 90 and now >= target - 2:
            note = f" ({target - now} of them were not listed after 90 s)"
            break
        if time.time() > began + 150:
            raise Failed(f"{now} songs are listed {time.time() - began:.0f} s after the rescan, not {target}")
        # A media store that was still busy when it was asked heard nothing: ask once more.
        if not asked_again and time.time() > began + 60:
            key(KEY_F5)
            asked_again = True
        time.sleep(3)
    late_state = {
        "songs": made,
        "baseline": baseline,
        "albums_before": albums_before,
        "listed": now,
    }
    screenshot("late-songs-listed")
    return (
        f"{len(made)} songs made and {pushed} files pushed to {LATE}; listed {now - before} new "
        f"songs {time.time() - began:.0f} s after the rescan{note}. The sidebar counted "
        f"{baseline if baseline is not None else 'no settled number of'} songs without a cover "
        f"before and {albums_before} albums"
    )


GARBLED_TITLE = re.compile(r"^Mo\S{1,3}ollar$")
CJK = re.compile("[぀-ヿ㐀-鿿가-힯]")
WITH_COVER = ("Zz Pictured 2", "Dauðalogn", "Zz Long 01", "Zz Merge One", "Zz Merge Two")


def open_place_of(label, empty=None, timeout=20):
    """A place of the sidebar chosen, and its list in front: the screen then. [empty] is what an
    empty list says, when it may be empty."""
    nodes = screen()
    item = sidebar_item(label, nodes)
    if item is None:
        raise Failed(f"the sidebar has no {label!r}: {said_on(nodes)[:30]}")
    tap(item)
    return wait_for(
        lambda found: place_shown(found) == label
        and (bool(boxes(found)) or bool(empty and find(empty, found))),
        timeout,
        f"the list {label!r}",
    )


def no_cover_lists():
    """The songs whose files hold no cover are counted by the sidebar ("· N%" says it is still
    counting) and listed: by its "No cover" place and by the chip over the Songs; and the albums
    with such a song by the chip over the Albums. A song with a cover is in none of them."""
    state = late_ready()
    parts = Parts()
    key(KEY_CTRL, KEY_1)
    seen = []
    result = {}

    def counted():
        deadline = time.time() + 90
        lower = 2 if state["baseline"] is None else state["baseline"] + 2
        while True:
            nodes = screen()
            said = sidebar_count("No cover", nodes)
            if said is not None and said not in seen:
                seen.append(said)
            settled = settled_count("No cover", nodes)
            if settled is not None and settled >= lower:
                break
            if time.time() > deadline:
                raise Failed(
                    f"the sidebar still says {said!r} for the songs without a cover after 90 s; "
                    f"it should count {lower} or more. It said {seen}"
                )
            time.sleep(2)
        result["count"] = settled
        if state["baseline"] is not None and settled != state["baseline"] + 2:
            raise Failed(
                f"the sidebar counts {settled} songs without a cover, not {state['baseline']} and "
                f"the 2 late ones: {seen}"
            )
        return f"it counted {settled} (it said {seen})"

    parts.check("the sidebar counts them", counted)

    def place():
        nodes = open_place_of("No cover")
        titles = list_titles(nodes)
        result["titles"] = titles
        missing = [title for title in ("Zz Bare", "Zz Pictured 1") if title not in titles]
        wrong = [title for title in WITH_COVER if title in titles]
        if missing or wrong:
            raise Failed(f"the list holds {titles[:12]}; it lacks {missing} and has {wrong}")
        count = result.get("count")
        if count is not None and len(titles) != count and count <= 14:
            raise Failed(f"the list holds {len(titles)} songs, the sidebar counted {count}: {titles}")
        screenshot("the-songs-without-a-cover")
        return f"it lists {len(titles)}: {titles[:8]}"

    parts.check("the place lists them", place)

    def songs_chip():
        key(KEY_CTRL, KEY_1)
        nodes = wait_for(
            lambda found: place_shown(found) in SONGS_PLACE and bool(boxes(found)), 15, "the Songs"
        )
        words, chip = chip_said(r"No cover \(\d+\)", nodes)
        if chip is None:
            raise Failed(f"the Songs have no chip for the songs without a cover: {said_on(nodes)[:20]}")
        count = number_in(words)
        if result.get("count") is not None and count != result["count"]:
            raise Failed(f"the chip says {words!r}, the sidebar counted {result['count']}")
        tap(chip)
        nodes = wait_for(
            lambda found: "Zz Pictured 1" in list_titles(found), 20, "the Songs narrowed by the chip"
        )
        titles = list_titles(nodes)
        wrong = [title for title in WITH_COVER if title in titles]
        if wrong:
            raise Failed(f"the chip's list has songs with a cover: {wrong}")
        _, chip = chip_said(r"No cover \(\d+\)", nodes)
        if chip is not None:
            tap(chip)
        return f"the chip said {words!r} and narrowed the list to {len(titles)} songs"

    parts.check("the chip over the Songs lists them", songs_chip)

    def albums_chip():
        key(KEY_CTRL, KEY_2)
        nodes = wait_for(
            lambda found: bool(find("^Zz Pictured$", found)) and the_albums(found), 20, "the Albums"
        )
        words, chip = chip_said(r"Missing covers \(\d+\)", nodes)
        if chip is None:
            raise Failed(f"the Albums have no chip for the albums that miss a cover: {said_on(nodes)[:20]}")
        count = number_in(words)
        tap(chip)
        nodes = wait_for(
            lambda found: not find("^Zz Long$", found) and bool(find("^Zz Pictured$", found)),
            20,
            "the Albums narrowed by the chip",
        )
        tiles = album_titles(nodes)
        missing = [title for title in ("Zz Bare Album", "Zz Pictured") if title not in tiles]
        wrong = [title for title in ("Zz Long", "Zz Control", "A Merge One") if title in tiles]
        if missing or wrong or (count <= 14 and len(tiles) != count):
            raise Failed(f"the chip said {words!r} and the grid shows {tiles}")
        screenshot("the-albums-that-miss-a-cover")
        _, chip = chip_said(r"Missing covers \(\d+\)", nodes)
        if chip is not None:
            tap(chip)
        return f"the chip said {words!r} and the grid showed {tiles}"

    parts.check("the chip over the Albums lists the albums with one", albums_chip)
    key(KEY_CTRL, KEY_1)
    return parts.report()


def garbled_lists():
    """The songs whose text looks garbled are counted by the sidebar and listed apart: the song
    whose title is UTF-8 read as Windows-1252, and the two that have it in their album artist; the
    control, an Icelandic title that is right as it is, is not in the list. None of the albums
    shows letters of another script (the library once read the text of one as Chinese). The chip
    for this list is there only where there is no sidebar: not tried here."""
    state = late_ready()
    parts = Parts()
    key(KEY_CTRL, KEY_1)

    def counted():
        deadline = time.time() + 90
        while True:
            nodes = screen()
            said = sidebar_count("Garbled text", nodes)
            count = number_in(said)
            if count is not None and count >= 3:
                state["garbled"] = count
                return f"the sidebar counts {said}"
            if time.time() > deadline:
                raise Failed(f"the sidebar counts {said!r} songs with garbled text after 90 s, not 3")
            time.sleep(2)

    parts.check("the sidebar counts them", counted)

    def listed():
        nodes = open_place_of("Garbled text")
        titles = list_titles(nodes)
        screenshot("the-garbled-text-list")
        mine = [title for title in titles if GARBLED_TITLE.match(title)]
        missing = [title for title in ("Zz Turkish B", "Zz Turkish C") if title not in titles]
        if not mine:
            missing.append("the song with the garbled title")
        if "Dauðalogn" in titles:
            raise Failed(f"the control, an Icelandic title that is right, is listed: {titles}")
        if missing:
            raise Failed(f"the list holds {titles}; it lacks {missing}")
        if any(CJK.search(words) for words in said_on(nodes)):
            raise Failed(f"letters of another script are on the list: {said_on(nodes)[:30]}")
        return f"it lists {titles}"

    parts.check("the place lists them, and not the control", listed)

    def control_is_a_song():
        key(KEY_CTRL, KEY_1)
        nodes = wait_for(
            lambda found: place_shown(found) in SONGS_PLACE and bool(boxes(found)), 15, "the Songs"
        )
        if "Dauðalogn" not in list_titles(nodes):
            raise Failed(f"the control is not among the songs: {list_titles(nodes)}")
        return "the control is a song among the others"

    parts.check("the control is a song like the others", control_is_a_song)

    def albums_read_right():
        key(KEY_CTRL, KEY_2)
        nodes = wait_for(
            lambda found: bool(find("^Zz Control$", found)) and the_albums(found), 20, "the Albums"
        )
        tiles = album_titles(nodes)
        state["turkish_tiles"] = tiles.count("Zz Turkish")
        if any(CJK.search(words) for words in said_on(nodes)):
            raise Failed(f"letters of another script are in the albums: {said_on(nodes)}")
        key(KEY_CTRL, KEY_1)
        return f"the albums read right ({tiles.count('Zz Turkish')} of them are called Zz Turkish)"

    parts.check("no album reads as another script", albums_read_right)
    return parts.report()


def open_garbled_title():
    """The song whose title is garbled, opened in the editor from the list of the garbled ones:
    its title as the list shows it."""
    key(KEY_CTRL, KEY_1)
    nodes = open_place_of("Garbled text")
    titles = [title for title in list_titles(nodes) if GARBLED_TITLE.match(title)]
    if not titles:
        raise Failed(f"the list of garbled text lacks the garbled title: {list_titles(nodes)}")
    tap(list_row(titles[0], nodes))
    wait_for(lambda found: title_field(found) == titles[0], 40, f"the editor of {titles[0]!r}")
    return titles[0]


NOTICE = r"^(This looks garbled|These look garbled)$"


def editor_repair():
    """The editor of the song whose title is garbled says so and offers to put it right; that is a
    change to save, and what is written is "Moğollar" with every other tag as it was."""
    state = late_ready()
    song = state["songs"]["garbled title"]
    parts = Parts()
    shown = open_garbled_title()
    nodes = screen()
    holds = {}

    def offered():
        if not find(NOTICE, nodes):
            raise Failed(f"the editor of {shown!r} does not say that it looks garbled: {said_on(nodes)[:30]}")
        lines = [node.text for node in nodes if re.fullmatch(rf"Title: {re.escape(GOOD)}\?", node.text)]
        if not lines:
            raise Failed(f"the notice does not say what the title should be: {said_on(nodes)[:30]}")
        if not find("^Repair$", nodes):
            raise Failed("the notice has no Repair button")
        screenshot("the-editor-offers-to-repair")
        return f"{shown!r} is said to look garbled; the notice says {lines[0]!r}"

    holds["offered"] = parts.check("the editor offers the repair", offered)
    if not holds["offered"]:
        key(KEY_CTRL, KEY_W)
        return parts.report()

    def repaired():
        tap(find("^Repair$", nodes)[0].pressed_as())
        after = wait_for(lambda found: title_field(found) == GOOD, 15, "the title put right")
        gone = wait_for(lambda found: not find(NOTICE, found), 10, "the notice gone")
        return f"the title field holds {title_field(gone) or title_field(after)!r}"

    holds["repaired"] = parts.check("Repair puts the title right in the editor", repaired)
    if holds["repaired"]:

        def saved():
            key(KEY_CTRL, KEY_S)
            saved_when(r"^(Saved|No changes)$", 90)
            after = probe(pull(song["file"], LATE))
            wrote = after["tags"].get("title")
            if wrote != GOOD:
                raise Failed(f"the title in the file is {wrote!r}, not {GOOD!r}")
            try:
                same_but(after, song["probe"], "title")
            except Failed as reason:
                raise Failed(f"{song['file']}: {reason}")
            return f"the file says {wrote!r} (it said {song['probe']['tags'].get('title')!r}); the other tags, the picture and the sound as they were"

        holds["saved"] = parts.check("the file is written with the right title", saved)

        def list_falls():
            before = state.get("garbled", 3)
            deadline = time.time() + 45
            while True:
                said = sidebar_count("Garbled text", screen())
                if number_in(said) is not None and number_in(said) < before:
                    return f"the sidebar went from {before} to {said} garbled songs"
                if time.time() > deadline:
                    raise Failed(f"the sidebar still counts {said} garbled songs 45 s after the save")
                time.sleep(2)

        if holds.get("saved"):
            parts.check("the saved song leaves the list", list_falls)
    key(KEY_CTRL, KEY_W)
    nodes = screen()
    if find("^Discard$", nodes):
        tap(find("^Discard$", nodes)[0])
    return parts.report()


def batch_repair():
    """The garbled list selected whole is edited together: the batch editor offers to put the text
    of its songs right, with the switch off; switched on and applied, the files say "Moğollar" in
    the album artist, with every other tag as it was, and the list is empty."""
    state = late_ready()
    songs = state["songs"]
    parts = Parts()
    key(KEY_CTRL, KEY_1)
    nodes = open_place_of("Garbled text")
    listed = list_titles(nodes)
    if len(listed) < 2:
        raise Failed(f"the list of garbled text holds {listed}")
    library_has_the_keys()
    key(KEY_CTRL, KEY_A)
    nodes = wait_for(
        lambda found: selected_count(found) >= 2 and bool(find(r"^Edit \d+ songs$", found)),
        45,
        "the batch editor beside the list",
    )
    count = selected_count(nodes)
    nodes = wait_for(lambda found: bool(find("^Repair garbled text$", found)), 45, "the repair card")
    switch = find("^Repair garbled text$", nodes)[0].pressed_as()
    previews = [node.text for node in nodes if "→" in node.text and node.left >= PANEL_RIGHT]
    screenshot("the-repair-card")

    def card():
        if switch.checked:
            raise Failed("the repair is on before anyone asked for it")
        if not previews:
            raise Failed(f"the card shows nothing of what it would change: {said_on(nodes)[:30]}")
        if not any(words.endswith(GOOD) for words in previews):
            raise Failed(f"the card does not show the right text: {previews}")
        return f"off at first, it shows {previews[:3]}"

    parts.check("the card is there, off, and shows what it would do", card)

    def applied():
        tap(switch)
        def switched_on(found):
            cards = find("^Repair garbled text$", found)
            return bool(cards) and cards[0].pressed_as().checked

        wait_for(switched_on, 10, "the repair switched on")
        apply_all()
        done = saved_when(r"^Done$", 180)
        saved = next((node.text for node in done if re.search(r"songs? saved", node.text)), "")
        if find(r"couldn.t be saved", done) or not saved:
            raise Failed(f"the batch did not save every song: {said_on(done)}")
        screenshot("garbled-text-repaired")
        tap(find("^Finish$", done)[0])
        return saved

    if not parts.check("switched on and applied it saves", applied):
        return parts.report()

    def files():
        notes = []
        wanted = [("garbled b", "album_artist"), ("garbled c", "album_artist")]
        if any(GARBLED_TITLE.match(title) for title in listed):
            wanted.append(("garbled title", "title"))
        for name, field in wanted:
            song = songs[name]
            after = probe(pull(song["file"], LATE))
            wrote = album_artist_of(after["tags"]) if field == "album_artist" else after["tags"].get(field)
            if wrote != GOOD:
                raise Failed(f"{song['file']}: the {field} in the file is {wrote!r}, not {GOOD!r}")
            try:
                same_but(after, song["probe"], field)
            except Failed as reason:
                raise Failed(f"{song['file']}: {reason}")
            notes.append(f"{song['file']}: {field} {GOOD!r}")
        return "; ".join(notes) + ". Every other tag, the picture and the sound as they were"

    parts.check("the files say the right text and nothing else changed", files)

    def empty():
        deadline = time.time() + 60
        while True:
            nodes_ = screen()
            said = sidebar_count("Garbled text", nodes_)
            if number_in(said) == 0 and find("^No garbled text$", nodes_):
                return f"the list says 'No garbled text' and the sidebar counts {said}"
            if time.time() > deadline:
                raise Failed(f"the garbled list is not empty 60 s after the save: sidebar {said}, {said_on(nodes_)[:20]}")
            time.sleep(2)

    parts.check("the list is empty", empty)

    def one_album():
        key(KEY_CTRL, KEY_2)
        nodes_ = wait_for(
            lambda found: bool(find("^Zz Control$", found)) and the_albums(found), 20, "the Albums"
        )
        now = album_titles(nodes_).count("Zz Turkish")
        before = state.get("turkish_tiles")
        was = "an unknown number" if before is None else before
        if now != 1:
            raise Failed(f"{now} albums are called Zz Turkish (there were {was}), not one")
        return f"{was} albums called Zz Turkish became {now}"

    parts.check("the two albums are one", one_album)
    key(KEY_CTRL, KEY_1)
    return f"{count} songs selected; " + parts.report()


# --- Albums: the second button, a cover, merging ------------------------------------------------


def album_titles(nodes):
    """The albums the grid shows, by their tiles, from the top left down."""
    tiles = [
        node
        for node in nodes
        if node.takes_click
        and node.kind.endswith("View")
        and 150 < node.right - node.left < 260
        and node.bottom - node.top > node.right - node.left
        and node.right <= PANEL_RIGHT
        and node.top < BAR_TOP
    ]
    tiles.sort(key=lambda node: (node.top // 10, node.left))
    return [
        next((each.text for each in node.within() if each.kind.endswith("TextView") and each.text), "")
        for node in tiles
    ]


def region_colour(left, top, right, bottom):
    """The average colour of a part of the screen, as red, green and blue out of 255."""
    picture = adb("exec-out", "screencap", "-p", binary=True, timeout=60)
    cropped = subprocess.run(
        ["ffmpeg", "-v", "error", "-i", "pipe:0", "-vf",
         f"crop={right - left}:{bottom - top}:{left}:{top},scale=1:1", "-frames:v", "1",
         "-f", "rawvideo", "-pix_fmt", "rgb24", "pipe:1"],
        input=picture,
        capture_output=True,
        check=True,
    ).stdout
    if len(cropped) < 3:
        raise Failed("the screen could not be read as a picture")
    return tuple(cropped[:3])


def tile_colour(tile):
    """The colour of the middle of an album's cover in the grid: the cover is the square at the
    top of its tile, and its edges (rounded) are left out."""
    side = tile.right - tile.left
    inset = side // 5
    return region_colour(tile.left + inset, tile.top + inset, tile.right - inset, tile.top + side - inset)


def colour_distance(seen, wanted):
    """How far apart two colours are, by the channel that differs most."""
    target = ((wanted >> 16) & 255, (wanted >> 8) & 255, wanted & 255)
    return max(abs(a - b) for a, b in zip(seen, target))


def select_albums(titles, mouse=None):
    """The albums chosen with Ctrl and a click each, one after the other: the screen at the end.
    Given a mouse, the one that is plugged in already (two of the same name would not do)."""
    if mouse is None:
        with LiveMouse() as own:
            return select_albums(titles, own)
    nodes = screen()
    if selected_count(nodes):
        stop_selecting(nodes)
        nodes = wait_for(
            lambda found: selected_count(found) == 0 and the_albums(found), 15, "the selection ended"
        )
    for number, title in enumerate(titles, 1):
        click_on(mouse, list_row(title, nodes), held=Gesture.KEY_LEFTCTRL)
        nodes = wait_for(
            lambda found, wanted=number: selected_count(found) == wanted,
            15,
            f"{number} albums selected",
        )
    return nodes


def album_menu():
    """An album shows the cover of the first of its songs that has one. The mouse's second button
    on an album opens its menu (Open, Play, Shuffle, Edit all, Select) and does not open it; on one
    of two selected albums the menu also has Edit together and Merge albums; Esc closes it."""
    if not mouse_works:
        raise NotBegun("no mouse")
    late_ready()
    parts = Parts()
    nodes = albums_place("Discovery")
    nodes = wait_for(
        lambda found: bool(find("^Zz Pictured$", found)) and bool(find("^Zz Bare Album$", found)),
        30,
        "the late albums",
    )

    def cover():
        pictured = list_row("Zz Pictured", nodes)
        bare = list_row("Zz Bare Album", nodes)
        deadline = time.time() + 25
        while True:
            got = tile_colour(pictured)
            off = colour_distance(got, PINK)
            if off <= 45:
                break
            if time.time() > deadline:
                raise Failed(
                    f"the album whose first song has no cover and whose second has a pink one shows "
                    f"{got}, not the pink {(PINK >> 16, (PINK >> 8) & 255, PINK & 255)}"
                )
            time.sleep(2)
        other = tile_colour(bare)
        screenshot("album-covers-in-the-grid")
        if colour_distance(other, PINK) <= 45:
            raise Failed(f"the album with no cover at all shows the pink too: {other}")
        return f"the tile of Zz Pictured shows {got}, the one of Zz Bare Album {other}"

    parts.check("an album shows the cover of the first of its songs that has one", cover)
    with LiveMouse() as mouse:

        def alone():
            tile = list_row("Discovery", nodes)
            click_on(mouse, tile, button=Gesture.BTN_RIGHT)
            # A menu is a window of its own: the dump of the window in front holds the menu alone.
            try:
                shown = wait_for(lambda found: bool(find(r"^Open$", found)), 10, "the album's menu")
            except Failed:
                if an_album(screen()):
                    raise Failed("the second button opened the album instead of its menu")
                raise
            labels = menu_labels(shown, r"^Open$")
            screenshot("an-albums-menu")
            wanted = ["Open", "Play", "Shuffle", "Edit all", "Select"]
            if sorted(labels) != sorted(wanted):
                raise Failed(f"the menu holds {labels}, not {wanted}")
            key(KEY_ESCAPE)
            closed = wait_for(lambda found: not find(r"^Open$", found), 10, "the menu closed")
            if selected_count(closed) or not the_albums(closed):
                raise Failed("Esc did more than close the menu")
            return f"the menu held {labels}; Esc closed it"

        parts.check("the second button opens an album's menu", alone)

        def several():
            chosen = select_albums(("A Merge One", "A Merge Two"), mouse)
            click_on(mouse, list_row("A Merge Two", chosen), button=Gesture.BTN_RIGHT)
            shown = wait_for(lambda found: bool(find(r"^Open$", found)), 10, "the menu of a selected album")
            labels = menu_labels(shown, r"^Open$")
            screenshot("the-menu-of-two-selected-albums")
            missing = [label for label in ("Edit together", "Merge albums") if label not in labels]
            if missing or "Select" in labels:
                raise Failed(f"the menu of one of two selected albums holds {labels}; it lacks {missing}")
            key(KEY_ESCAPE)
            closed = wait_for(lambda found: not find(r"^Open$", found), 10, "the menu closed")
            if selected_count(closed) != 2:
                raise Failed(f"Esc left {selected_count(closed)} albums selected, not the two")
            return f"on one of two selected albums the menu held {labels}; Esc closed it and kept the two"

        parts.check("the menu of one of two selected albums", several)
    return parts.report()


def merge_albums():
    """Two albums selected are merged: the batch editor opens with the card for it; one title is
    typed, the merge taken, and applied. The files of both songs say one album and one album artist
    with every other tag as it was, and the library lists one album where there were two."""
    state = late_ready()
    one, two = state["songs"]["merge one"], state["songs"]["merge two"]
    nodes = albums_place("A Merge One")
    before_albums = number_in(sidebar_count("Albums", nodes))
    if selected_count(nodes) != 2:
        nodes = select_albums(("A Merge One", "A Merge Two"))
    head = [node for node in find("^Merge albums$", nodes) if node.top < 200]
    if not head:
        raise Failed(f"the head of the selected albums has no Merge albums: {said_on(nodes)[:20]}")
    if not head[0].pressed_as().enabled:
        raise Failed("Merge albums is not enabled with two albums selected")
    tap(head[0].pressed_as())
    nodes = wait_for(
        lambda found: bool(find(r"^These songs are in 2 albums$", found)), 60, "the merge card"
    )
    lines = {node.text for node in nodes if node.left >= PANEL_RIGHT}
    if not {"A Merge One", "A Merge Two"} <= lines:
        raise Failed(f"the card names {sorted(lines)[:12]}, not the two albums")
    screenshot("the-merge-card")
    tap(find("^Merge into one album$", nodes)[0].pressed_as())
    wait_for(lambda found: bool(find(r"^Merge$", found)), 15, "the choices of the merge")
    type_into("Album", "A Merged")
    nodes = screen()
    tap(find(r"^Merge$", nodes)[0].pressed_as())
    nodes = wait_for(
        lambda found: bool(find("They will be one album once you apply", found)), 15, "the merge taken"
    )
    screenshot("the-merge-taken")
    apply_all()
    done = saved_when(r"^Done$", 180)
    saved = next((node.text for node in done if re.search(r"songs? saved", node.text)), "")
    if find(r"couldn.t be saved", done) or not saved:
        raise Failed(f"the batch did not save both songs: {said_on(done)}")
    tap(find("^Finish$", done)[0])
    parts = Parts()

    def files():
        notes = []
        for song in (one, two):
            after = probe(pull(song["file"], LATE))
            album, artist = after["tags"].get("album"), album_artist_of(after["tags"])
            if album != "A Merged" or artist != "Zz Merge Artist":
                raise Failed(f"{song['file']} says album {album!r}, album artist {artist!r}")
            try:
                same_but(after, song["probe"], "album", "album_artist")
            except Failed as reason:
                raise Failed(f"{song['file']}: {reason}")
            notes.append(f"{song['file']}: {album!r} by {artist!r}")
        return "; ".join(notes) + ". Every other tag, the picture and the sound as they were"

    parts.check("the files say one album and one album artist", files)

    def library():
        nodes_ = wait_for(
            lambda found: bool(find("^A Merged$", found))
            and not find("^A Merge One$", found)
            and not find("^A Merge Two$", found)
            and the_albums(found),
            45,
            "the albums after the merge",
        )
        now = number_in(sidebar_count("Albums", nodes_))
        screenshot("two-albums-became-one")
        return f"the grid lists 'A Merged' and neither of the two; the sidebar counted {before_albums} albums and then {now}"

    parts.check("the library lists one album where there were two", library)
    return f"{saved}. " + parts.report()


# --- A collection longer than the window ---------------------------------------------------------


def long_album():
    """In an album longer than the window: End goes to its last song and Home to its first; a drag
    that begins on its cover or its name selects nothing; a drag from a song to below the list's
    edge keeps selecting as the list scrolls under it, and back up it selects fewer."""
    if not mouse_works:
        raise NotBegun("no mouse")
    late_ready()
    parts = Parts()
    nodes = albums_place("Zz Long")
    nodes = open_album("Zz Long", nodes)
    nodes = wait_for(lambda found: len(boxes(found)) >= 4, 20, "the songs of the album")
    first = f"Zz Long {1:02d}"
    last = f"Zz Long {LONG_ALBUM:02d}"
    if last in list_titles(nodes):
        raise Skipped(f"all {LONG_ALBUM} songs of the album are in the window")

    def end_key():
        key(KEY_END)
        after = wait_for(lambda found: last in list_titles(found), 10, f"{last!r} after End")
        if first in list_titles(after):
            raise Failed("End left the first song in view as well as the last")
        screenshot("the-end-of-a-long-album")
        key(KEY_HOME)
        wait_for(lambda found: first in list_titles(found), 10, f"{first!r} after Home")
        return f"End showed {last!r} and Home {first!r} again"

    parts.check("End goes to the last song, Home to the first", end_key)
    with LiveMouse() as mouse:

        def from_the_cover():
            seen = screen()
            names = [node for node in find(r"^Zz Long$", seen) if node.top < 400 and not node.is_field]
            rows_ = [box.parent for box in boxes(seen)]
            if not names or len(rows_) < 3:
                raise Failed(f"the album's name or its songs are not in view: {said_on(seen)[:20]}")
            at = mouse.go_to(*names[0].center)
            if landed_row(at, [names[0]]) is None:
                raise Skipped(f"the pointer could not be brought to the name: it is at {at}")
            mouse.press(Gesture.BTN_LEFT)
            try:
                mouse.go_to(names[0].center[0], (rows_[2].top + rows_[2].bottom) // 2)
            finally:
                mouse.release(Gesture.BTN_LEFT)
            after = screen()
            if selected_count(after) or not find("^Pick a song$", after):
                raise Failed(f"a drag from the name selected {selected_count(after)} songs: {said_on(after)[:20]}")
            return "a drag from the album's name selected nothing and opened nothing"

        parts.check("a drag from the cover or the name does nothing", from_the_cover)

        def scrolling():
            seen = screen()
            rows_ = [box.parent for box in boxes(seen)]
            if len(rows_) < 3:
                raise Failed(f"the album shows {len(rows_)} songs")
            x = drag_from(mouse, rows_, 0)
            try:
                edge = max(node.bottom for node in rows_)
                mouse.go_to(x, min(edge + 45, 1076))
                time.sleep(2)
                during = screen()
                most = selected_count(during)
                shown = len(boxes(during))
                screenshot("a-rectangle-scrolling-the-list")
                mouse.go_to(x, rows_[0].top + 60)
                time.sleep(1.5)
                back = selected_count(screen())
            finally:
                mouse.release(Gesture.BTN_LEFT)
            if most < shown + 3:
                raise Failed(f"below the list's edge the drag had {most} songs selected with {shown} in view")
            if back >= most:
                raise Failed(f"back up the selection did not shrink: {most} then {back}")
            return f"below the edge it had {most} songs selected with {shown} in view; back up, {back}"

        parts.check("a drag below the list's edge scrolls it and keeps selecting", scrolling)
    stop_selecting()
    return parts.report()


# --- A file opened by hand --------------------------------------------------------------------


def pick_a_file(avoid):
    """Ctrl+O, and a file chosen from what the system's picker shows first (or under Audio): the
    first whose name does not contain any of [avoid]. Its name."""
    key(KEY_CTRL, KEY_O)
    nodes = wait_for(
        lambda found: any("documentsui" in node.package for node in found), 25, "the system's file picker"
    )
    names = re.compile(r"\.(mp3|flac|m4a|ogg|opus|wav|aac)$", re.IGNORECASE)
    for attempt in range(3):
        files = [
            node
            for node in nodes
            if names.search(node.text) and not any(word in node.text for word in avoid)
        ]
        if files:
            chosen = files[0]
            tap(chosen.pressed_as())
            return chosen.text
        roots = [
            node.pressed_as()
            for node in nodes
            if re.fullmatch(r"Audio|Recent", node.text) and node.pressed_as().takes_click
        ]
        if not roots or attempt == 2:
            break
        tap(roots[0] if attempt == 0 else roots[-1])
        time.sleep(2)
        nodes = screen()
    key(KEY_BACK)
    raise Skipped(f"the picker shows no audio file to choose: {said_on(nodes)[:25]}")


def file_opened_by_hand():
    """A file opened with Ctrl+O stays beside the library when a search hides the song clicked
    before it; a song clicked, though, goes when a search hides it: "Pick a song" takes its place."""
    late_ready()
    parts = Parts()
    clicked = "Aerodynamic"
    home()
    nodes = screen()
    tap(row(clicked, nodes))
    wait_for(lambda found: title_field(found) == clicked, 40, f"the editor of {clicked!r}")

    def search_that_hides():
        key(KEY_CTRL, KEY_F)
        type_text("Digital")
        return wait_for(lambda found: clicked not in list_titles(found), 15, "the search")

    def search_cleared():
        found = find("^Clear search$")
        if found:
            tap(found[0])
        wait_for(lambda seen: clicked in list_titles(seen), 20, "the list whole again")

    def stays():
        picked = pick_a_file(("Aerodynamic",))
        opened = wait_for(
            lambda found: any(node.package == PACKAGE for node in found)
            and title_field(found) not in (None, clicked),
            40,
            "the file opened",
        )
        title = title_field(opened)
        screenshot("a-file-opened-by-hand")
        shown = search_that_hides()
        if title_field(shown) != title:
            raise Failed(f"a search that hides {clicked!r} took the editor of {title!r} away: {said_on(shown)[:20]}")
        search_cleared()
        return f"{picked} ({title!r}) stayed beside the library through a search that hides {clicked!r}"

    parts.check("the file opened by hand stays", stays)

    def goes():
        tap(row(clicked))
        wait_for(lambda found: title_field(found) == clicked, 40, f"the editor of {clicked!r}")
        search_that_hides()
        after = wait_for(lambda found: bool(find("^Pick a song$", found)), 15, "Pick a song")
        if title_field(after) == clicked:
            raise Failed(f"the editor of {clicked!r} stayed beside a list that does not have it")
        screenshot("a-song-hidden-by-a-search")
        search_cleared()
        return f"the editor of {clicked!r} gave way to 'Pick a song'"

    parts.check("a song hidden by a search goes", goes)
    return parts.report()


# --- Hundreds of songs selected ----------------------------------------------------------------


def transaction_errors():
    """How many lines of the log say a transaction was too large for the system."""
    log_text = adb("logcat", "-d", "-b", "all", timeout=90)
    return len(re.findall(r"TransactionTooLarge|FAILED BINDER TRANSACTION", log_text))


def big_selection():
    """With hundreds of songs listed, Ctrl+A shows beside the list how many are selected and how to
    edit them together, and opens no editor by itself; Ctrl+E opens the batch editor of all of them;
    and the app sent to the background with that editor open leaves no transaction too large in the
    log."""
    home()
    nodes = screen()
    total = number_in(sidebar_count("Songs", nodes)) or 0
    if total < 150:
        raise Skipped(f"only {total} songs are listed")
    if not find("^Pick a song$", nodes):
        key(KEY_CTRL, KEY_W)
    parts = Parts()
    library_has_the_keys()
    key(KEY_CTRL, KEY_A)

    def placeholder():
        seen = wait_for(
            lambda found: any(
                re.fullmatch(r"\d+ songs selected", node.text) and node.left >= PANEL_RIGHT for node in found
            ),
            20,
            "the placeholder beside the list",
        )
        said = [node.text for node in seen if re.fullmatch(r"\d+ songs selected", node.text) and node.left >= PANEL_RIGHT]
        button = [node for node in find("^Edit together$", seen) if node.left >= PANEL_RIGHT]
        if not button:
            raise Failed(f"the placeholder has no Edit together: {said_on(seen)[:30]}")
        if find(r"^Edit \d+ songs$", seen):
            raise Failed("a batch editor opened by itself for the whole selection")
        screenshot("hundreds-of-songs-selected")
        return f"{said[0]!r} with Edit together, and no batch editor"

    parts.check("Ctrl+A shows how many are selected, and no editor", placeholder)
    errors = transaction_errors()
    key(KEY_CTRL, KEY_E)

    def batch():
        seen = wait_for(lambda found: bool(find(r"^Edit \d+ songs$", found)), 60, "the batch editor")
        screenshot("hundreds-of-songs-edited-together")
        return find(r"^Edit \d+ songs$", seen)[0].text

    opened = parts.check("Ctrl+E opens the batch editor of all of them", batch)
    if opened:
        shell("input keyevent 3")  # Home: the app is sent to the background, with its state saved.
        time.sleep(5)
        now = transaction_errors()
        launch_app()

        def background():
            if now > errors:
                raise Failed(f"the log has {now - errors} lines about a transaction too large after Home")
            seen = wait_for(
                lambda found: any(node.package == PACKAGE for node in found), 30, "the app back"
            )
            again = "the editor was there" if find(r"^Edit \d+ songs$", seen) else "the library was there"
            return f"Home with the batch editor open left no transaction too large in the log; back, {again}"

        parts.check("Home leaves no transaction too large", background)
        key(KEY_CTRL, KEY_W)
    stop_selecting()
    return parts.report()


# --- Songs on the SD card ----------------------------------------------------------------------

card_listed = False


def on_card(action):
    """[action], once there are songs on the card to try it on."""

    def tried():
        if not card_listed:
            raise Skipped("no songs on an SD card")
        return action()

    return tried


def card_songs():
    """The songs the media store has from the card's folder: [(title, volume)]."""
    rows_said = shell(
        "content query --uri content://media/external/audio/media "
        "--projection title:volume_name:_data"
    )
    found = []
    for line in rows_said.splitlines():
        match = re.search(r"title=(.*), volume_name=(.*), _data=(.*)$", line.strip())
        if match and f"/{CARD_FOLDER}/" in match.group(3):
            found.append((match.group(1), match.group(2)))
    return found


def card_found_by_rescan():
    """Songs put where only a scan of the card finds them are listed once the app rescans."""
    global card_listed
    if card is None:
        raise Skipped("the device has no SD card")
    home()
    local = {name: os.path.join(songs_dir, "Homework", name) for name in CARD_SONGS.values()}
    shell("mkdir -p /data/local/tmp/card")
    for name, path in local.items():
        before[name] = probe(path)
        adb("push", path, f"/data/local/tmp/card/{name}", timeout=120)
    # Onto the card itself, beneath the view of it apps have, which tells the media store of every
    # file written through it: as if the card had been filled on a computer and the scan made
    # when it went in had missed them. There, only a scan finds them.
    underneath = f"{card['underneath']}/{CARD_FOLDER}"
    said = shell(
        f"su 0 sh -c 'mkdir -p \"{underneath}\" && cp /data/local/tmp/card/*.flac \"{underneath}/\""
        f" && ls \"{underneath}\"'"
    )
    if all(name in said for name in CARD_SONGS.values()):
        how = "put on the card where only a scan finds them"
    else:
        log(f"    as root: {said.strip()[:300]}")
        shell(f"mkdir -p '{card['root']}/{CARD_FOLDER}'")
        for name, path in local.items():
            adb("push", path, f"{card['root']}/{CARD_FOLDER}/{name}", timeout=120)
        how = "copied to the card the way apps see it, which the media store hears of at once"
    time.sleep(4)
    known = card_songs()
    key(KEY_CTRL, KEY_R)
    try:
        expect("^Around the World$", 90)
    except Failed:
        key(KEY_F5)
        expect("^Around the World$", 60)
    expect("^Da Funk$", 15)
    card_listed = True
    volumes = sorted({volume for _, volume in card_songs()})
    if "external_primary" in volumes:
        raise Failed(f"the media store has the card's songs on the device's own storage: {volumes}")
    screenshot("songs-on-the-card")
    told = f"the media store knew {len(known)} of them" if known else "the media store knew neither"
    return f"{how}; {told}, and Ctrl+R listed both. Their volume: {', '.join(volumes)}"


def card_folder():
    """The folder on the card says where it is, and so does the device's own."""
    key(KEY_CTRL, KEY_4)
    expect("^Homework$", 20)
    nodes = screen()
    on_card_folder = [node.text for node in nodes if re.search(r"^2 songs · .+/Music$", node.text)]
    on_device = [node.text for node in nodes if re.search(r"^4 songs · Music$", node.text)]
    screenshot("folders-on-the-card-and-the-device")
    key(KEY_CTRL, KEY_1)
    expect("One More Time", 15)
    if not on_card_folder:
        raise Failed(f"the card's folder does not say where it is: {said_on(nodes)[:30]}")
    if not on_device:
        raise Failed(f"the device's folder does not say where it is: {said_on(nodes)[:30]}")
    name = on_card_folder[0][len("2 songs · ") : -len("/Music")]
    return f"Homework says {on_card_folder[0]!r}, Discovery {on_device[0]!r}: the card is {name!r}"


def card_location():
    """The editor says where the song is: in the card's folder, under the card's name."""
    tap(row("Around the World"))
    verdict = expect("Looks like real lossless", 120)
    # The file's details are at the foot of the column beside the fields, out of sight: a finger
    # brings them up.
    x = verdict.center[0]
    location = None
    for _ in range(5):
        nodes = screen()
        location = next((node for node in nodes if node.says(r"Music/Homework/$")), None)
        if location:
            break
        shell(f"input swipe {x} 900 {x} 350 600")
        time.sleep(1.5)
    if location is None:
        raise Failed(f"nothing says where the song is: {said_on(screen())[-20:]}")
    # The file's line says its size, its bitrate and its folder, in that order.
    words = (location.text or location.description).split(" · ")[-1]
    screenshot("a-song-on-the-card")
    if words.startswith("Music/"):
        raise Failed(f"the location {words!r} does not say the song is on the card")
    return f"the editor says the song is in {words!r}"


def card_cover_saved():
    """A cover found for a song on the card is written to it there, and nothing else changes."""
    tap(row("Around the World"))
    expect("Looks like real lossless", 120)
    key(KEY_CTRL, KEY_K)
    use, reasons = read_cover_sheet()
    tap(use)
    expect_gone("Other covers|Use this cover", 20)
    expect(r"\d+ × \d+ px", 90)
    key(KEY_CTRL, KEY_S)
    allow_system_dialog(40)
    # "Saved" shows for four seconds, which a screen read every two may miss; the foot saying
    # nothing is changed any more says the same, and stays.
    expect("^Saved|^No changes$", 60)
    written = check_written([CARD_SONGS["Around the World"]], f"{card['root']}/{CARD_FOLDER}")
    key(KEY_CTRL, KEY_W)
    return f"{written}. The sheet said: {reasons[:6]}"


def turkish():
    said = shell(f"cmd locale set-app-locales {PACKAGE} --locales tr-TR")
    time.sleep(5)
    try:
        expect("Şarkılar", 30)
        nodes = screen()
        for wanted in ("Albümler", "Sanatçılar", "Klasörler"):
            if not find(wanted, nodes):
                raise Failed(f"nothing says {wanted!r}: {said_on(nodes)[:40]}")
        tap(row("One More Time"))
        expect("Gerçek kayıpsız gibi görünüyor", 120)
        screenshot("turkish-editor")
        key(KEY_F1)
        expect_shortcuts(title="^Klavye kısayolları$")
        screenshot("turkish-shortcuts")
        key(KEY_ESCAPE)
        expect_shortcuts(shown=False, title="^Klavye kısayolları$")
        about_in_turkish()
    finally:
        shell(f"cmd locale set-app-locales {PACKAGE} --locales en-US")
        time.sleep(4)
    return f"the app speaks Turkish when asked to {said.strip()[:60]}"


def quality_scan():
    """The library checks its lossless songs on its own, says what it found in a column of its
    table, and lists the fake ones apart."""
    wanted = {
        "One More Time": "^Real lossless$",
        "Aerodynamic": "^(Fake FLAC|Probably fake)$",
        "Harder, Better, Faster, Stronger": "^Upscaled$",
    }
    deadline = time.time() + 240
    while True:
        nodes = screen()
        found = {}
        for title, pattern in wanted.items():
            try:
                found[title] = row(title, nodes).holds(pattern)
            except Failed:
                found[title] = False
        mp3 = row("Digital Love", nodes)
        if all(found.values()):
            break
        if time.time() > deadline:
            raise Failed(f"the table's verdicts after four minutes: {found}. {said_on(nodes)[:40]}")
        time.sleep(3)
    if mp3.holds("^(Real lossless|Not checked|Checking…)$"):
        raise Failed("the MP3 was given a verdict in the table")
    screenshot("verdicts-in-the-table")
    # The sidebar's place for the fake ones: the leftmost of what says so, the badge being in the
    # table beside it.
    places = [node for node in find("^Fake FLAC$") if not node.is_field]
    if not places:
        raise Failed("the sidebar has no place for the fake ones")
    tap(min(places, key=lambda node: node.left).pressed_as())
    deadline = time.time() + 20
    while True:
        nodes = screen()
        listed = [title for title in SONGS if find("^" + re.escape(title) + "$", nodes)]
        if listed == ["Aerodynamic"]:
            break
        if time.time() > deadline:
            raise Failed(f"the fake ones' list shows {listed}")
        time.sleep(1)
    screenshot("the-fake-ones")
    key(KEY_CTRL, KEY_1)
    expect("One More Time", 15)
    return f"the table said {list(wanted.values())}, and the sidebar's list held Aerodynamic alone"


def looks():
    """The library in the day and at night, for whoever wants to see it."""
    home()
    screenshot("library-in-the-day")
    shell("cmd uimode night yes")
    try:
        time.sleep(4)
        expect("One More Time", 15)
        screenshot("library-at-night")
        tap(row("One More Time"))
        expect("Looks like real lossless", 120)
        screenshot("inspector-at-night")
        key(KEY_CTRL, KEY_W)
        shell("wm size 1080x1920")
        shell("wm density 420")
        time.sleep(4)
        expect("One More Time", 15)
        screenshot("phone-at-night")
        tap(row("Aerodynamic"))
        expect("Made from a lossy file|Probably made from a lossy file", 120)
        screenshot("phone-editor-at-night")
        key(KEY_CTRL, KEY_W)
    finally:
        shell("wm size reset")
        shell("wm density reset")
        shell("cmd uimode night no")
        time.sleep(3)
    expect(LIBRARY, 20)
    return "pictures of the library, the inspector and a phone's screens"


def playback_state():
    """The media session's state, as the system has it: 3 is playing."""
    dumped = shell("dumpsys media_session", timeout=60)
    ours = dumped.split(PACKAGE, 1)[1] if PACKAGE in dumped else ""
    match = re.search(r"state=(?:PLAYING\(3\)|(\d+))", ours)
    if not match:
        return None
    return 3 if match.group(1) is None else int(match.group(1))


def played_so_far():
    """Where the docked player's bar says the song is, in seconds: the time left of its line, which
    says what it is. None while there is no such bar."""
    nodes = screen()
    lines = [node for node in nodes if node.description in SEEK_LINE]
    if not lines:
        return None
    line = lines[0]
    times = [
        node
        for node in nodes
        if re.fullmatch(r"\d{1,3}:\d{2}", node.text)
        and node.right <= line.left
        and node.top < line.bottom
        and node.bottom > line.top
    ]
    if not times:
        return None
    minutes, seconds = max(times, key=lambda node: node.right).text.split(":")
    return int(minutes) * 60 + int(seconds)


# What the docked bar's line is called, in English and in Turkish.
SEEK_LINE = ("Position in the song", "Şarkıdaki konum")


def keeps_playing_in_background():
    """Android 17 stops what plays from an app in the background unless it plays as the system
    expects: through a media session, from a foreground service. The app's does. Before that, the
    docked bar follows the song: where it is reaches only the line and its times, apart from the
    rest of what plays, and nothing else would tell if it stopped reaching them."""
    home()
    # The first song played asks to show its notification, as a dialog over the app, and nothing of
    # the app's can be read from under it. Granted beforehand, as by someone who had answered it.
    shell(f"pm grant {PACKAGE} android.permission.POST_NOTIFICATIONS")
    tap(expect("^Play all$", 15))
    deadline = time.time() + 20
    while playback_state() != 3:
        if time.time() > deadline:
            raise Skipped(f"nothing played on this emulator: {playback_state()}")
        time.sleep(1)
    time.sleep(2)
    # Read twice while the song plays: the songs here last 16 s, and the next one starts again from
    # nothing, so any change says the time moved on, not only a later one.
    first = played_so_far()
    # What was there instead, should the bar not have been.
    instead = None if first is not None else said_on(screen())[-12:]
    time.sleep(3)
    then = played_so_far()
    screenshot("the-player-docked-under-the-panels")
    key(3)  # Home: the app goes to the background.
    time.sleep(10)
    state = playback_state()
    hardening = [
        line.strip()
        for line in shell("dumpsys audio", timeout=60).splitlines()
        if "hardening" in line.lower() or (PACKAGE in line and "mute" in line.lower())
    ]
    with open(os.path.join(out_dir, "audio-hardening.txt"), "w", encoding="utf-8") as file:
        file.write("\n".join(hardening))
    shell(f"am start -n {PACKAGE}/{MAIN}")
    time.sleep(3)
    shell("input keyevent 127")  # Pause, as a headset's button does.
    if state != 3:
        raise Failed(f"in the background the session is in state {state}: {hardening[:6]}")
    if first is None or then is None or then == first:
        raise Failed(
            f"the docked bar said {first} s, then {then} s, while the song played"
            + (f"; the screen said {instead}" if instead else "")
        )
    return (
        f"the docked bar said {first} s, then {then} s; still playing ten seconds into the"
        f" background. The audio service says: {hardening[:4]}"
    )


def editor_field(text, timeout=40):
    """The editor's field that holds [text], once it does."""
    deadline = time.time() + timeout
    while True:
        fields = [node for node in screen() if node.is_field and node.text == text]
        if fields:
            return fields[0]
        if time.time() > deadline:
            raise Failed(f"no field holds {text!r}: {said_on(screen())[:30]}")
        time.sleep(1)


def keyboard_state():
    """Whether the system shows a keyboard on screen, as its input method service says, in short."""
    said = shell("dumpsys input_method", timeout=60)
    found = re.findall(r"(mInputShown|mIsInputViewShown|mImeWindowVis|mDecorViewVisible)=(\S+)", said)
    return ", ".join(f"{name}={value}" for name, value in dict(found).items()) or "unknown"


def save_and_watch(shot):
    """Taps Save and watches the song being saved. Returns what says it was saved and the Save
    button, as they were seen together (None if the message came and went between two reads of
    the screen: each takes a second or two, and it shows for four), and what went wrong with the
    tap, if nothing came of it: then Ctrl+S saves instead, so that the step can go on."""
    tap(expect("^Save$", 10))
    tapped_at = time.time()
    seen, saved_at, started, missed = None, None, False, None
    deadline = tapped_at + 60
    while time.time() < deadline:
        nodes = screen()
        if not any(node.package == PACKAGE for node in nodes) and find(r"^(Allow|ALLOW)$", nodes):
            # The system asks once for each file whether the app may change it.
            tap(find(r"^(Allow|ALLOW)$", nodes)[0])
            started = True
            continue
        if find("^Saving", nodes):
            started = True
        messages = find("^Saved$", nodes)
        if len(messages) > 1:
            raise Failed(f"{len(messages)} messages say Saved at once")
        if messages and seen is None:
            started = True
            message = messages[0]
            while message.parent is not None and not message.holds("^Undo$"):
                message = message.parent
            # The button around the word: once saved it takes no click, so the nearest thing that
            # does would be something else.
            buttons = [node.parent or node for node in find("^Save$", nodes)]
            seen = (message, buttons[0] if buttons else None)
            screenshot(shot)
        if find("^No changes$", nodes):
            started = True
            saved_at = saved_at or time.time()
            if seen or time.time() - saved_at > 5:
                return seen, missed
        if not started and missed is None and time.time() - tapped_at > 8:
            # Nothing came of the tap. What was in front of the button, and the keys instead.
            missed = f"a tap on Save did nothing (keyboard: {keyboard_state()})"
            log(f"    {missed}")
            screenshot(f"{shot}-tap-missed")
            key(KEY_CTRL, KEY_S)
        time.sleep(0.3)
    raise Failed(f"the song was not saved after 60 s: {said_on(screen())[:30]}")


def clear_of(seen):
    """Whether the message stood clear of the Save button, as a line for the report."""
    if seen is None:
        return "the message came and went unseen"
    message, button = seen
    if button is None:
        raise Failed(f"Save was not in sight beside the message {message!r}")
    if message.bottom > button.top and message.top < button.bottom:
        raise Failed(f"the message {message!r} stood over Save {button!r}")
    return f"the message ended {button.top - message.bottom} px above Save"


def in_view(pattern, attempts=6, field=False):
    """The first node saying [pattern] (the first field, with [field]), scrolled into the part of
    a phone's editor nothing covers: under its head, above its foot and the keyboard under that."""
    for _ in range(attempts):
        nodes = screen()
        found = [node for node in nodes if node.says(pattern) and (node.is_field or not field)]
        foot = [node for node in nodes if node.says("^Save$")]
        bottom = (foot[0].top - 40) if foot else 1500
        if found:
            node = found[0]
            if 300 <= node.top and node.bottom <= bottom:
                return node
            # The content moves the way the finger does: up, to bring what is below into view.
            if node.top >= bottom or node.bottom > bottom:
                shell(f"input swipe 540 {bottom - 100} 540 {max(bottom - 500, 350)} 400")
            else:
                shell("input swipe 540 500 540 900 400")
        time.sleep(1)
    raise Failed(f"/{pattern}/ could not be brought into view: {said_on(screen())[:30]}")


def retyped_artist_is_kept():
    """An artist taken out and typed back in, then saved without pressing Enter, as someone who
    edits a chip and saves does. The song has to keep its artist, and nothing be left to save:
    Save once wrote the field without what was being typed, and the field added it after."""
    file_name = SONGS["Digital Love"]
    tap(in_view("^Remove Daft Punk$"))
    tap(in_view("^Type and press enter$|^Add another$"))
    for _ in range(3):
        shell("input text Daft%sPunk")
        time.sleep(1.5)
        typed = [node.text for node in screen() if node.is_field and node.focused]
        if typed == ["Daft Punk"]:
            break
        # A slow emulator can repeat the first key, as for a key held down: typed again.
        log(f"    typed 'Daft Punk', the field holds {typed!r}: again")
        key(KEY_CTRL, KEY_A)
        key(KEY_DELETE)
    else:
        raise Failed(f"the artist could not be typed back: {said_on(screen())[:30]}")
    tap(expect("^Save$", 10))
    expect("^No changes$", 20)
    artist = probe(pull(file_name))["tags"].get("artist")
    if artist != before[file_name]["tags"].get("artist"):
        raise Failed(f"Save without Enter left the artist as {artist!r}")
    if not find("^Daft Punk$"):
        raise Failed(f"the artist typed back is not a value of the field: {said_on(screen())[:30]}")
    return "an artist typed back in and saved without Enter was kept, with nothing left to save"


def saves_on_a_phone():
    """Saving on a phone again and again, as someone who had to wait for Save to show again would:
    what says a song was saved stays clear of the button, a newer message takes the place of the
    one on show, and the editor goes on showing the song as it was saved. The title is typed
    into with its field focused, as a person does, and the keyboard that brings up must leave
    Save in reach. Then an artist is typed back in and saved without Enter."""
    shell("wm size 1080x1920")
    shell("wm density 420")
    try:
        time.sleep(4)
        home()
        tap(row("Digital Love"))
        original = "Digital Love"
        edited = original
        notes, missed = [], []
        for attempt in (1, 2):
            editor_field(edited)
            tap(in_view("^" + re.escape(edited) + "$", field=True))
            key(KEY_MOVE_END)
            shell(f"input text _{attempt}")
            time.sleep(1.5)
            typed = next(
                (
                    node.text
                    for node in screen()
                    if node.is_field and node.text.startswith(edited) and node.text != edited
                ),
                None,
            )
            if typed is None:
                raise Failed(f"the title could not be changed: {said_on(screen())[:20]}")
            edited = typed
            seen, miss = save_and_watch(f"phone-saved-{attempt}")
            notes.append(clear_of(seen))
            if miss:
                missed.append(miss)
            # Still the song as it was saved, its title in its field and nothing left to save.
            editor_field(edited, 15)
        notes.append(retyped_artist_is_kept())
        # Back to the title it had, so that the steps after this one find the song as it was.
        tap(in_view("^" + re.escape(edited) + "$", field=True))
        key(KEY_CTRL, KEY_A)
        shell("input text Digital%sLove")
        editor_field(original, 15)
        _, miss = save_and_watch("phone-saved-back")
        if miss:
            missed.append(miss)
        file_name = SONGS[original]
        after = probe(pull(file_name))
        if after["tags"] != before[file_name]["tags"]:
            raise Failed(f"{file_name} is not back as it was: {after['tags']}")
        key(KEY_CTRL, KEY_W)
        expect(LIBRARY, 20)
        if missed:
            raise Failed("; ".join(missed) + f". Saved with Ctrl+S instead: {'; '.join(notes)}")
    finally:
        # Whatever happened, no song is left open with changes for the steps after this one.
        try:
            home()
        except Exception as error:
            log(f"    could not get back to the library: {error}")
        shell("wm size reset")
        shell("wm density reset")
        time.sleep(3)
    return f"saved as {edited!r} twice in a row: " + "; ".join(notes) + ". Then back as it was"


def glass_on_a_phone():
    """On a phone the player's bar and the selection's float over the list as glass."""
    home()
    tap(expect("^Play all$", 15))
    deadline = time.time() + 20
    while playback_state() != 3:
        if time.time() > deadline:
            raise Skipped(f"nothing played on this emulator: {playback_state()}")
        time.sleep(1)
    shell("wm size 1080x1920")
    shell("wm density 420")
    try:
        time.sleep(4)
        # On a phone only one screen shows: whatever was beside the library would be in front.
        home()
        expect("One More Time", 15)
        screenshot("phone-player-glass")
        # Paused, the bar stays and its row's bars stop moving: with them moving, a reading of the
        # screen once came back without the rows under them and the selection's bar.
        shell("input keyevent 127")
        time.sleep(1)
        key(KEY_CTRL, KEY_A)
        expect(SELECTED, 15)
        expect("^Edit together$", 10)
        screenshot("phone-selection-glass")
        key(KEY_ESCAPE)
        expect_gone(SELECTED, 15)
        # A menu is glass too, with nothing around it.
        tap(expect("^More options$", 10))
        expect("^Rescan music folders$", 10)
        time.sleep(1)
        screenshot("phone-menu-glass")
        key(KEY_ESCAPE)
        expect_gone("^Rescan music folders$", 10)
    finally:
        shell("input keyevent 127")
        shell("wm size reset")
        shell("wm density reset")
        time.sleep(3)
    return "pictures of the player's bar, the selection's bar and a menu over the list"


def ui_thread_times():
    """How long the app's own thread worked on each of its last frames (composing, laying out,
    recording what to draw), in ms: the median and the 90th percentile. The emulator draws without
    a graphics card, so its whole frames are slow whatever the app does; this is the part the app
    decides."""
    out = shell(f"dumpsys gfxinfo {PACKAGE} framestats")
    lines = out.split("---PROFILEDATA---")
    if len(lines) < 2:
        return None
    rows = [line.strip().rstrip(",") for line in lines[1].strip().splitlines() if line.strip()]
    if len(rows) < 2:
        return None
    header = rows[0].split(",")
    try:
        flags, start, sync = (header.index(name) for name in ("Flags", "HandleInputStart", "SyncStart"))
    except ValueError:
        return None
    spans = []
    for row in rows[1:]:
        cells = row.split(",")
        if len(cells) <= max(flags, start, sync) or cells[flags] != "0":
            continue
        span = (int(cells[sync]) - int(cells[start])) / 1_000_000
        if 0 < span < 5000:
            spans.append(span)
    if not spans:
        return None
    spans.sort()
    return spans[len(spans) // 2], spans[min(len(spans) - 1, len(spans) * 9 // 10)], len(spans)


def frame_stats():
    """What the system counted of the app's frames since they were last reset."""
    out = shell(f"dumpsys gfxinfo {PACKAGE}")
    stats = {}
    for name, pattern in (
        ("frames", r"Total frames rendered: (\d+)"),
        ("janky", r"Janky frames: \d+ \(([\d.]+)%\)"),
        ("p50", r"50th percentile: (\d+)ms"),
        ("p90", r"90th percentile: (\d+)ms"),
        ("p99", r"99th percentile: (\d+)ms"),
    ):
        match = re.search(pattern, out)
        if match:
            stats[name] = match.group(1)
    return stats


def scrolled(name, x, top, bottom, flings=12):
    """Flings the list under [x] down and back up, and says how its frames went."""
    shell(f"dumpsys gfxinfo {PACKAGE} reset")
    for _ in range(flings):
        shell(f"input swipe {x} {bottom} {x} {top} 180")
        time.sleep(0.35)
    for _ in range(flings):
        shell(f"input swipe {x} {top} {x} {bottom} 180")
        time.sleep(0.35)
    time.sleep(1)
    return f"{name}: " + frames_said()


def frames_said():
    """The frames since the last reset, as the system counted them, and the app's own share."""
    stats = frame_stats()
    ui = ui_thread_times()
    said = (
        f"{stats.get('frames', '?')} frames, {stats.get('janky', '?')}% janky, "
        f"p50 {stats.get('p50', '?')} ms, p90 {stats.get('p90', '?')} ms"
    )
    if ui:
        said += f"; the app's thread p50 {ui[0]:.1f} ms, p90 {ui[1]:.1f} ms (last {ui[2]} frames)"
    return said


def typed_in_editor():
    """Types into a song's title in the editor, letter after letter as fast as the system sends
    them, and says how the frames went: each letter is a change of the song in the editor, and
    everything that shows the song is asked whether it changed."""
    if not [node for node in find("^One More Time$") if not node.is_field]:
        # Below three hundred copies of another song, when builds are timed alone: found by a
        # search, as a person would.
        key(KEY_CTRL, KEY_F)
        shell("input text One%sMore")
        time.sleep(2)
    tap(row("One More Time"))
    deadline = time.time() + 40
    title = None
    while title is None:
        fields = [node for node in screen() if node.is_field and node.text == "One More Time"]
        if fields:
            title = fields[0]
        elif time.time() > deadline:
            raise Failed("One More Time did not open in the editor")
        else:
            time.sleep(1)
    tap(title)
    key(KEY_MOVE_END)
    time.sleep(1)
    shell(f"dumpsys gfxinfo {PACKAGE} reset")
    for _ in range(3):
        shell("input text abcdefghijklmnopqrst")
    time.sleep(2)
    said = frames_said()
    key(KEY_CTRL, KEY_W)
    tap(expect("^Discard$", 15))
    return "typing 60 letters in the editor: " + said


# How many copies of a song make a library long enough to scroll through.
LONG_LIBRARY = 300


def long_library():
    """A long library: a song with a cover, copied over and over, then found by a rescan. Copied
    once: builds timed one after the other find it there."""
    shell(
        f"[ -f /sdcard/Music/Long/{LONG_LIBRARY}.mp3 ] || "
        "(mkdir -p /sdcard/Music/Long && for i in $(seq 1 "
        + str(LONG_LIBRARY)
        + "); do cp '/sdcard/Music/Discovery/03 - Digital Love.mp3' /sdcard/Music/Long/$i.mp3; done)"
    )
    key(KEY_CTRL, KEY_R)
    deadline = time.time() + 120
    while time.time() < deadline:
        counted = [
            int(match.group(1))
            for node in screen()
            for match in [re.match(r"^(\d[\d,]*) songs", node.text.replace(",", ""))]
            if match
        ]
        if counted and max(counted) >= LONG_LIBRARY:
            return
        time.sleep(3)
    raise Failed(f"the long library was not listed: {said_on(screen())[:20]}")


def host_speed():
    """How fast the emulator is on this run, told by something the app has no part in: the time its
    shell takes to count to 100,000, the best of three. The machines the runs get are not all as
    fast: one run was slower than another in everything, copying files included, by up to four
    times, and its app with it."""
    best = None
    for _ in range(3):
        started = time.time()
        shell("i=0; while [ $i -lt 100000 ]; do i=$((i+1)); done", timeout=120)
        took = time.time() - started
        best = took if best is None else min(best, took)
    return f"the emulator's shell counts to 100,000 in {best * 1000:.0f} ms"


def performance():
    """How fast it starts, how smoothly a long library scrolls on a wide window and on a phone, and
    how much memory it holds then: numbers to compare between builds, not a pass or a fail. The
    emulator draws without a graphics card of its own, so its frames are slower than a phone's;
    what counts is how the numbers move from one build to the next, on machines as fast as each
    other: how fast this one is comes first."""
    notes = [host_speed()]
    starts = []
    for _ in range(3):
        shell(f"am force-stop {PACKAGE}")
        time.sleep(2)
        match = re.search(r"TotalTime: (\d+)", shell(f"am start -W -n {PACKAGE}/{MAIN}"))
        if match:
            starts.append(int(match.group(1)))
        time.sleep(5)
    if starts:
        notes.append(f"cold start {sorted(starts)[len(starts) // 2]} ms (of {starts})")
    home()
    notes.append(typed_in_editor())
    home()
    long_library()
    time.sleep(4)
    notes.append(scrolled("the table on a wide window", 1100, 330, 960))
    try:
        shell("wm size 1080x2340")
        shell("wm density 420")
        time.sleep(5)
        notes.append(scrolled("the rows on a phone", 540, 900, 2050))
    finally:
        shell("wm size reset")
        shell("wm density reset")
        time.sleep(3)
    memory = shell(f"dumpsys meminfo {PACKAGE}")
    with open(os.path.join(out_dir, "meminfo.txt"), "w", encoding="utf-8") as file:
        file.write(memory)
    match = re.search(r"TOTAL PSS:\s+(\d+)", memory) or re.search(r"\n\s*TOTAL\s+(\d+)", memory)
    if match:
        notes.append(f"memory {int(match.group(1)) // 1024} MB (PSS)")
    return "; ".join(notes)


def crash_report():
    """The app's last crash as the system logged it: the exception, what caused it, and the first
    of the app's own frames it went through. Saved whole beside the report."""
    crashes = adb("logcat", "-d", "-b", "crash", timeout=60)
    with open(os.path.join(out_dir, "logcat-crash.txt"), "w", encoding="utf-8") as file:
        file.write(crashes)
    lines = [line.split(": ", 1)[-1] for line in crashes.splitlines() if "AndroidRuntime" in line]
    said = [
        line
        for line in lines
        if not line.startswith("\tat ") or "bobbyesp" in line or "compose" in line
    ]
    return " | ".join(said[:14]) or "nothing in the crash log"


# The library's places, as its sidebar and its tabs name them.
PLACES = ["Albums", "Artists", "Folders", "Songs"]

LAYOUTS = [
    ("a wide window", None),
    ("a window with no room for the sidebar", ("1920x1080", 360)),
    ("a phone", ("1080x2340", 420)),
]


def open_place(place, layout):
    """Opens [place] from the sidebar's item or the tab, not the title over the list that says the
    same, and fails with what the crash log says if the app closes."""
    names = [
        node.pressed_as()
        for node in find(f"^{place}$")
        if not node.is_field and node.pressed_as().takes_click
    ]
    if not names:
        raise Failed(f"nothing opens {place!r} on {layout}: {said_on(screen())[:30]}")
    tap(min(names, key=lambda node: (node.left, node.top)))
    time.sleep(2.5)
    if not shell(f"pidof {PACKAGE}").strip():
        raise Failed(f"the app closed on {place}, on {layout}: {crash_report()}")


def close_details():
    """Where only one screen shows, what was beside the library is in front of it: the editor the
    inspector opened for the song clicked last, once a selection ends. Closed, the library is
    back."""
    for _ in range(3):
        if find(LIBRARY):
            return
        key(KEY_CTRL, KEY_W)
        time.sleep(1.5)


def visit_places(tag, while_there=None):
    """Each of the library's places opens: on a wide window, from its sidebar; on one with no room
    for a sidebar, from the tabs over the panel; on a phone, from its tabs. [while_there] is done in
    each, with the place and the layout's name."""
    opened = []
    try:
        for name, size in LAYOUTS:
            if size:
                shell(f"wm size {size[0]}")
                shell(f"wm density {size[1]}")
                time.sleep(4)
                close_details()
            for place in PLACES:
                open_place(place, name)
                if while_there:
                    while_there(place, name)
                    if not shell(f"pidof {PACKAGE}").strip():
                        raise Failed(f"the app closed in {place}, on {name}: {crash_report()}")
                screenshot(f"{tag}-{slug(name)}-{place.lower()}")
            opened.append(name)
    finally:
        shell("wm size reset")
        shell("wm density reset")
        time.sleep(3)
    return opened


def every_place_opens():
    """A place is a list, or a grid, of its own, and one with less in it than fills the window is a
    case of its own."""
    return "every place opened on " + ", ".join(visit_places("places"))


# Albums made for the run, each with a cover of its own, as a library of albums has.
COVERED_ALBUMS = 16


def make_covered_albums():
    """Songs of as many albums, each with a picture embedded as its front cover, put on the device
    and found by a rescan."""
    folder = os.path.join(out_dir, "covered-albums")
    os.makedirs(folder, exist_ok=True)
    for index in range(COVERED_ALBUMS):
        cover = os.path.join(folder, f"cover-{index:02d}.jpg")
        color = (index * 0x3A5F1D + 0x204060) & 0xFFFFFF
        subprocess.run(
            ["ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-i",
             f"color=c=0x{color:06x}:s=900x900,drawbox=x=150:y=150:w=600:h=600:color=white@0.6:t=40",
             "-frames:v", "1", cover],
            check=True,
        )
        song = os.path.join(folder, f"{index:02d} - Song {index}.mp3")
        subprocess.run(
            ["ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-i", "sine=frequency=330:duration=3",
             "-i", cover, "-map", "0:a", "-map", "1:v", "-c:a", "libmp3lame", "-b:a", "64k",
             "-c:v", "copy", "-id3v2_version", "3",
             "-metadata", f"title=Song {index}", "-metadata", f"album=Album {index:02d}",
             "-metadata", f"artist=Artist {index % 5}", "-metadata", "date=2020",
             "-metadata:s:v", "title=Album cover", "-metadata:s:v", "comment=Cover (front)",
             "-disposition:v", "attached_pic", song],
            check=True,
        )
    # And one album as compilations often are: the same title on songs by two artists, with no
    # album artist to say they are one. MediaStore gives both songs one album; the library tells
    # them apart by their artists.
    for artist in ("First Artist", "Second Artist"):
        cover = os.path.join(folder, "cover-00.jpg")
        song = os.path.join(folder, f"Shared - {artist}.mp3")
        subprocess.run(
            ["ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-i", "sine=frequency=220:duration=3",
             "-i", cover, "-map", "0:a", "-map", "1:v", "-c:a", "libmp3lame", "-b:a", "64k",
             "-c:v", "copy", "-id3v2_version", "3",
             "-metadata", f"title=Shared by {artist}", "-metadata", "album=Shared Album",
             "-metadata", f"artist={artist}",
             "-metadata:s:v", "title=Album cover", "-metadata:s:v", "comment=Cover (front)",
             "-disposition:v", "attached_pic", song],
            check=True,
        )
    home()
    before = listed_count()
    shell("mkdir -p /sdcard/Music/Covered")
    for name in sorted(os.listdir(folder)):
        if name.endswith(".mp3"):
            adb("push", os.path.join(folder, name), f"/sdcard/Music/Covered/{name}", timeout=60)
    key(KEY_CTRL, KEY_R)
    deadline = time.time() + 90
    while time.time() < deadline:
        if listed_count() >= before + COVERED_ALBUMS + 2:
            return
        time.sleep(3)
    raise Failed(f"the albums made for the run were not listed: {said_on(screen())[:20]}")


def listed_count():
    """How many songs the library's head says it has."""
    counted = [
        int(match.group(1))
        for node in screen()
        for match in [re.match(r"^(\d+) songs", node.text.replace(",", ""))]
        if match
    ]
    return max(counted, default=0)


def flung(place, layout):
    """Flings what the place lists, down and back up: a grid of covers loads them as it goes."""
    width, height = (1920, 1080)
    if "phone" in layout:
        width, height = (1080, 2340)
    x = width * 2 // 3 if "wide" in layout else width // 2
    for start, end in ((height * 4 // 5, height // 3), (height // 3, height * 4 // 5)):
        shell(f"input swipe {x} {start} {x} {end} 150")
        time.sleep(0.8)


def places_with_covers():
    """The places again, with a library of albums that have covers: a grid of pictures is what a
    library with covers shows, and what the first visit, on songs without any, could not try."""
    make_covered_albums()
    hovered = []

    def while_there(place, layout):
        flung(place, layout)
        if place == "Albums" and layout == "a wide window" and mouse_works:
            cards = [node.pressed_as() for node in find(r"^Album \d\d$")][:4]
            with LiveMouse() as mouse:
                for card in cards:
                    hovered.append(mouse.go_to(*aim(card)))
                    time.sleep(0.6)

    opened = visit_places("covered", while_there)
    return (
        f"{COVERED_ALBUMS} albums with covers; every place opened, flung through, on "
        + ", ".join(opened)
        + (f"; the pointer rested on {len(hovered)} albums" if hovered else "")
    )


def no_crash():
    crashes = adb("logcat", "-d", "-b", "crash", timeout=60)
    with open(os.path.join(out_dir, "logcat-crash.txt"), "w", encoding="utf-8") as file:
        file.write(crashes)
    everything = adb("logcat", "-d", "-v", "time", "*:W", timeout=90)
    with open(os.path.join(out_dir, "logcat-warnings.txt"), "w", encoding="utf-8") as file:
        file.write("\n".join(everything.splitlines()[-6000:]))
    ours = [line for line in crashes.splitlines() if PACKAGE in line or "metadator" in line]
    if ours:
        raise Failed(f"the app crashed: {crash_report()}")
    if not shell(f"pidof {PACKAGE}").strip():
        raise Failed("the app is not running any more")
    return "no crash in the log, and the app is still running"


def main():
    step("The device has an SD card", prepare_card, required=False)
    if not step("The songs are copied to the device", copy_songs):
        return
    if previous_apk:
        step(
            "Installed over the build before it, it opens what that one left",
            updates_the_build_before,
        )
    if not step("The APK installs", install):
        return
    if not step("It opens, and asks to see the music", opens_and_asks):
        step("Nothing crashed", no_crash)
        return
    if not step("It lists the songs once allowed", lists_songs):
        step("Nothing crashed", no_crash)
        return
    global restore_after_failure
    restore_after_failure = True
    step("Every place of the library opens, wide, narrower and on a phone", every_place_opens)
    step("On a wide window the editor has its place beside the library", two_panes)
    step("The library checks its lossless songs, and lists the fake ones", quality_scan)
    step("F1 lists the keyboard shortcuts", shortcut_list)
    step("Ctrl+F searches", search)
    step("Tab and Enter open a song without a mouse", keyboard_alone)
    # After the keyboard step, which counts its Tab presses from where the search left the focus.
    step(
        "Esc in the search field clears it, then leaves it",
        within("esc in the search", esc_in_the_search_field),
        required=False,
    )
    step(
        "Settings, About says who developed the app",
        within("about", about_says_who),
        required=False,
    )
    step(
        "A genuine FLAC is called genuine",
        verdict_of("One More Time", ["Looks like real lossless"]),
    )
    step(
        "A FLAC made from an MP3 is called that",
        verdict_of("Aerodynamic", ["Made from a lossy file", "Probably made from a lossy file"]),
    )
    step(
        "A FLAC of 96 kHz made from a CD is called upscaled",
        verdict_of("Harder, Better, Faster, Stronger", ["Lossless, but upscaled"]),
    )
    step("An MP3 gets no verdict", no_verdict_for_mp3)
    step("Ctrl+K finds a cover for a song (network)", find_cover_for_song, required=False)
    step("Ctrl+S saves it, and only it", save_song, required=cover_search_worked)
    step(
        "The library shows the cover just written",
        library_shows_saved_cover,
        required=cover_search_worked,
    )
    step(
        "The same cover again leaves nothing to save", same_cover_again, required=cover_search_worked
    )
    step("Choosing another song shows it in place of the last", choosing_replaces)
    step("A song with unsaved changes stays under the next", unsaved_stays)
    step("Ctrl+A and Ctrl+E edit the songs together", edit_together)
    step("Ctrl+K finds a cover for the album (network)", find_cover_for_album, required=False)
    step("Ctrl+S applies it to all, and only it", apply_to_all, required=cover_search_worked)
    step(
        "The library shows the covers written to all",
        library_shows_batch_covers,
        required=cover_search_worked,
    )
    step(
        "A cover replaced by the batch is the one the library shows",
        library_shows_replaced_cover,
        required=cover_search_worked,
    )
    step("Back in the library", leave_batch)
    step(
        "Ctrl+L looks up the album's metadata, and Esc leaves it unwritten (network)",
        within("metadata", find_metadata_for_album),
        required=False,
    )
    step("The activity follows its display without being recreated", follows_the_display)
    step("A narrow window shows one pane at a time", compact_layout)
    step("A mouse can be plugged in", plug_mouse, required=False)
    step("The second mouse button opens a song's menu", right_click_menu, required=False)
    step("A song opens in a window of its own", open_in_new_window, required=False)
    step("Ctrl and Shift change what a click selects", ctrl_click_selects, required=False)
    step("A button says what it does under the pointer", tooltip_on_hover, required=False)
    step(
        "One press leaves an album: its arrow, Esc, the Back key",
        within("back arrow", album_back_arrow),
        required=False,
    )
    step(
        "Back at the start of the app keeps it in front while a mouse is the pointer",
        within("back at the start", back_at_the_start),
        required=False,
    )
    step(
        "The editor beside the library follows the selection",
        within("editor follows", editor_follows_the_selection),
        required=False,
    )
    step(
        "A box selects in an album and in the Songs, and Shift on a box takes a range",
        within("boxes", boxes_and_shift),
        required=False,
    )
    step(
        "A rectangle dragged with the mouse selects the rows it touches",
        within("rectangle", drag_to_select),
        required=False,
    )
    step("Songs put on an SD card are found by a rescan", card_found_by_rescan)
    step("A folder on the SD card says where it is", on_card(card_folder))
    step(
        "A genuine FLAC on the SD card is called genuine",
        on_card(verdict_of("Around the World", ["Looks like real lossless"])),
    )
    step("The editor says the song is on the SD card", on_card(card_location))
    step(
        "A FLAC on the SD card made from an MP3 is called that",
        on_card(verdict_of("Da Funk", ["Made from a lossy file", "Probably made from a lossy file"])),
    )
    step(
        "A cover is written to a song on the SD card, and only it (network)",
        on_card(card_cover_saved),
        required=cover_search_worked,
    )
    step(
        "A cover saved to a song takes it off the list of songs without one (network)",
        within("cover leaves", cover_leaves_the_list),
        required=False,
    )
    step(
        "Editing an album's songs together writes their tags (SD card)",
        within("album artist", album_artist_written),
        required=False,
    )
    step(
        "A box and Shift on a box select in the Fake FLAC list too",
        within("fake boxes", boxes_in_the_fake_list),
        required=False,
    )
    step("It speaks Turkish", turkish)
    step(
        "The About page says who developed the app, in Turkish",
        turkish_about_reported,
        required=False,
    )
    step("How it looks, by day and by night, wide and narrow", looks, required=False)
    step(
        "Music keeps playing in the background (Android 17)",
        keeps_playing_in_background,
        required=False,
    )
    step("On a phone, saving again and again keeps the editor whole", saves_on_a_phone, required=False)
    step("On a phone the bars float as glass", glass_on_a_phone, required=False)
    step(
        "The player's bar opens the full-screen player, and Back closes it",
        within("player", full_player),
        required=False,
    )
    step(
        "Songs made for the garbled text, the covers and the merge come late",
        within("late songs", late_songs_arrive),
        required=False,
    )
    step(
        "The songs without a cover are counted and listed",
        within("no cover", no_cover_lists),
        required=False,
    )
    step(
        "The songs with garbled text are counted and listed, and the control is not",
        within("garbled list", garbled_lists),
        required=False,
    )
    step(
        "The editor offers to put a garbled title right, and the file is written right",
        within("editor repair", editor_repair),
        required=False,
    )
    step(
        "The batch editor puts the garbled text of a whole list right",
        within("batch repair", batch_repair),
        required=False,
    )
    step(
        "An album shows its first cover, and the mouse's second button opens its menu",
        within("album menu", album_menu),
        required=False,
    )
    step(
        "Two albums merge into one, in their files and in the library",
        within("merge", merge_albums),
        required=False,
    )
    step(
        "In a long album End goes to the last song and a rectangle scrolls",
        within("long album", long_album),
        required=False,
    )
    step(
        "A file opened by hand stays beside the library; a song hidden by a search goes",
        within("by hand", file_opened_by_hand),
        required=False,
    )
    step("Every place opens again, with albums that have covers", places_with_covers)
    step("How fast it is: starting, scrolling, memory", performance, required=False)
    step(
        "Hundreds of songs selected: a placeholder, then the batch editor, then Home",
        within("big selection", big_selection),
        required=False,
    )
    step("Nothing crashed", no_crash)


def timed_alone():
    """Only how fast it is, for timing two builds one after the other on one emulator: the machines
    the runs get differ more than one build from the next, so builds are compared on one machine.
    The APK is installed anew and allowed to see the music without its first screen."""
    if not step("The songs are copied to the device", copy_songs):
        return
    adb("uninstall", PACKAGE, timeout=120)
    if not step("The APK installs", install):
        return
    for permission in ("READ_MEDIA_AUDIO", "POST_NOTIFICATIONS"):
        shell(f"pm grant {PACKAGE} android.permission.{permission}")

    def opens():
        shell(f"am start -W -n {PACKAGE}/{MAIN}", timeout=90)
        expect("^Aerodynamic$", 60)
        # Every build timed with the same library: the long one, from the start.
        long_library()
        return "it opens on the library, and lists the long one"

    if not step("It opens on the songs", opens):
        return
    global restore_after_failure
    restore_after_failure = True
    step("How fast it is: starting, scrolling, memory", performance)


try:
    if os.environ.get("SMOKE_TIMED_ALONE") == "1":
        timed_alone()
    else:
        main()
finally:
    failed = [name for name, outcome, _ in results if outcome == "FAILED"]
    with open(os.path.join(out_dir, "report.txt"), "w", encoding="utf-8") as report:
        report.write(f"{len(results)} steps, {len(failed)} failed, in {time.time() - started:.0f} s\n\n")
        for name, outcome, detail in results:
            report.write(f"[{outcome}] {name}\n")
            if detail:
                report.write(f"    {detail}\n")
        if waited_out:
            report.write(f"\nWaited out on the way: {waited_out}\n")
    print(open(os.path.join(out_dir, "report.txt"), encoding="utf-8").read())
    log_file.close()

sys.exit(1 if failed else 0)
