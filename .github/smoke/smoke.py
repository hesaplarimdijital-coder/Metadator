#!/usr/bin/env python3
"""Uses the app on an emulator the way somebody at a desk would, and writes down what happened.

This is not a unit test. It is the check that the APK, as built, opens, lists songs, tells a real
FLAC from one made out of an MP3, finds a cover, writes it without touching anything else, follows
its window as it changes, answers to a keyboard and a mouse, and does all of that for songs on an
SD card too. Everything goes through adb, so the APK tried is the one that is published, untouched.

Usage: smoke.py <apk> <songs dir> <out dir>

Each step is tried whatever became of the one before it, and leaves a screenshot and what was on
screen. The report says which held. A step that needs the network, or a device this script has to
make up (the mouse), is reported but does not fail the run: neither is this build's to answer for.
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
KEY_1 = 8
KEY_4 = 11
KEY_A = 29
KEY_E = 33
KEY_F = 34
KEY_K = 39
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

# Something only the library shows, and always: the last of its tabs.
LIBRARY = "^Folders$"
SHORTCUTS = "^Keyboard shortcuts$"

VERDICTS = (
    "Looks like real lossless",
    "Lossless, but upscaled",
    "Can't tell whether it is real lossless",
    "Probably made from a lossy file",
    "Made from a lossy file",
)

apk_path, songs_dir, out_dir = sys.argv[1], sys.argv[2], sys.argv[3]
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


def screen(attempts=4):
    """What the window in front shows, as the accessibility tree has it."""
    global last_dump
    for _ in range(attempts):
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
    outcome, detail = "ok", ""
    try:
        detail = action() or ""
    except Skipped as reason:
        outcome, detail = "not tried", str(reason)
    except Exception as error:  # Whatever went wrong, the next step is still worth trying.
        outcome = "FAILED" if required else "did not hold (not required)"
        detail = f"{type(error).__name__}: {error}"
        log(traceback.format_exc())
    log(f"    {outcome} {detail}")
    results.append((name, outcome, str(detail)))
    try:
        if outcome != "ok":
            screen()
        screenshot(slug(name))
        if outcome != "ok" and restore_after_failure:
            # What it left open would fail every step after it for no fault of theirs.
            log(f"    back to the library: {home()}")
    except Exception as error:
        log(f"    could not tidy up: {error}")
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
            if find(r"^(Deny|Don.t allow|Cancel)$", nodes):
                tap(find(r"^(Deny|Don.t allow|Cancel)$", nodes)[0])
            else:
                shell(f"am start -n {PACKAGE}/{MAIN}")
                time.sleep(3)
        elif find(SHORTCUTS, nodes) or find("^Find a cover$", nodes):
            key(KEY_ESCAPE)
        elif find("^Discard$", nodes):
            tap(find("^Discard$", nodes)[0])
        elif find("^Finish$", nodes):
            tap(find("^Finish$", nodes)[0])
        elif find(r"^Edit \d+ songs$", nodes):
            key(KEY_CTRL, KEY_W)
        elif not find(LIBRARY, nodes):
            # The editor alone on a narrow window, or a second window: closed, the library is back.
            key(KEY_CTRL, KEY_W)
        elif find(r"\d selected", nodes) and find("Stop selecting", nodes):
            tap(find("Stop selecting", nodes)[0])
        elif find("^Clear search$", nodes):
            tap(find("^Clear search$", nodes)[0])
        elif find("^Clear filters$", nodes):
            tap(find("^Clear filters$", nodes)[0])
        elif all(find(re.escape(title), nodes) for title in SONGS):
            return "the library, with its four songs"
        else:
            key(KEY_CTRL, KEY_1)
        time.sleep(1.2)
    return f"not restored: {said_on(screen())[:25]}"


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
    """Says yes to the system: the permission to list music, or to change a file."""
    button = expect(r"^(Allow|ALLOW)$", timeout)
    tap(button)
    time.sleep(1.5)


def row(title, nodes=None):
    """The song's row in the list, not the editor's field that says the same."""
    titles = [node for node in find(re.escape(title), nodes) if not node.is_field]
    if not titles:
        raise Failed(f"no row says {title!r}")
    return min(titles, key=lambda node: (node.left, node.top)).pressed_as()


# --- Setting up --------------------------------------------------------------------------------

before = {}


def install():
    said = adb("install", "-r", apk_path, timeout=300)
    if "Success" not in said:
        raise Failed(said.strip()[-300:])
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
        shell(
            "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE "
            f"-d 'file://{MUSIC}/{file_name}'"
        )
    deadline = time.time() + 90
    while time.time() < deadline:
        songs = listed_songs()
        if len(songs) >= len(SONGS) and all(title in SONGS for _, title, _ in songs):
            return f"MediaStore lists {len(songs)} songs"
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
    expect(SHORTCUTS, 15)
    nodes = screen()
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
    expect_gone(SHORTCUTS, 15)
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
    """Tab until a song has the focus, then Enter: the keys reach what a tap reaches."""
    for _ in range(8):
        key(KEY_TAB)
    focused = None
    for _ in range(14):
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


def save_song():
    if not cover_search_worked:
        raise Skipped("no cover was found to save")
    key(KEY_CTRL, KEY_S)
    allow_system_dialog(40)
    expect("^Saved", 60)
    return check_written(["01 - One More Time.flac"])


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
    expect("4 selected", 20)
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


def apply_to_all():
    if not cover_search_worked:
        raise Skipped("no cover was found to apply")
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
            if find(r"\d selected", nodes):
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
    expect(SHORTCUTS, 15)
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
                expect(SHORTCUTS, 15)
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
    expect_gone(SHORTCUTS, 15)
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
    KEY_LEFTCTRL, KEY_LEFTSHIFT = 29, 42
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
                    {"type": 101, "data": [self.KEY_LEFTCTRL, self.KEY_LEFTSHIFT]},
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
    if find(r"\d selected", nodes):
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
        if chosen != [order[0]] or not find("^1 selected$", nodes):
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
    if now != order[:3] or not find("^3 selected$", nodes):
        raise Failed(f"a Shift+click on {order[2]!r} left {now} selected: {said_on(nodes)[:24]}")
    screenshot("ctrl-and-shift-click")
    tap(expect("Stop selecting", 10))
    expect_gone(r"\d selected", 15)
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
    words = location.text or location.description
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
    expect("^Saved", 60)
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
        expect("^Klavye kısayolları$", 15)
        screenshot("turkish-shortcuts")
        key(KEY_ESCAPE)
        expect_gone("^Klavye kısayolları$", 15)
    finally:
        shell(f"cmd locale set-app-locales {PACKAGE} --locales en-US")
        time.sleep(4)
    return f"the app speaks Turkish when asked to {said.strip()[:60]}"


def no_crash():
    crashes = adb("logcat", "-d", "-b", "crash", timeout=60)
    with open(os.path.join(out_dir, "logcat-crash.txt"), "w", encoding="utf-8") as file:
        file.write(crashes)
    everything = adb("logcat", "-d", "-v", "time", "*:W", timeout=90)
    with open(os.path.join(out_dir, "logcat-warnings.txt"), "w", encoding="utf-8") as file:
        file.write("\n".join(everything.splitlines()[-6000:]))
    ours = [line for line in crashes.splitlines() if PACKAGE in line or "metadator" in line]
    if ours:
        raise Failed(f"the app crashed: {ours[:6]}")
    if not shell(f"pidof {PACKAGE}").strip():
        raise Failed("the app is not running any more")
    return "no crash in the log, and the app is still running"


def main():
    if not step("The APK installs", install):
        return
    step("The device has an SD card", prepare_card, required=False)
    if not step("The songs are copied to the device", copy_songs):
        return
    if not step("It opens, and asks to see the music", opens_and_asks):
        step("Nothing crashed", no_crash)
        return
    if not step("It lists the songs once allowed", lists_songs):
        step("Nothing crashed", no_crash)
        return
    global restore_after_failure
    restore_after_failure = True
    step("On a wide window the editor has its place beside the library", two_panes)
    step("F1 lists the keyboard shortcuts", shortcut_list)
    step("Ctrl+F searches", search)
    step("Tab and Enter open a song without a mouse", keyboard_alone)
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
    step("Choosing another song shows it in place of the last", choosing_replaces)
    step("A song with unsaved changes stays under the next", unsaved_stays)
    step("Ctrl+A and Ctrl+E edit the songs together", edit_together)
    step("Ctrl+K finds a cover for the album (network)", find_cover_for_album, required=False)
    step("Ctrl+S applies it to all, and only it", apply_to_all, required=cover_search_worked)
    step("Back in the library", leave_batch)
    step("The activity follows its display without being recreated", follows_the_display)
    step("A narrow window shows one pane at a time", compact_layout)
    step("A mouse can be plugged in", plug_mouse, required=False)
    step("The second mouse button opens a song's menu", right_click_menu, required=False)
    step("A song opens in a window of its own", open_in_new_window, required=False)
    step("Ctrl and Shift change what a click selects", ctrl_click_selects, required=False)
    step("A button says what it does under the pointer", tooltip_on_hover, required=False)
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
    step("It speaks Turkish", turkish)
    step("Nothing crashed", no_crash)


try:
    main()
finally:
    failed = [name for name, outcome, _ in results if outcome == "FAILED"]
    with open(os.path.join(out_dir, "report.txt"), "w", encoding="utf-8") as report:
        report.write(f"{len(results)} steps, {len(failed)} failed, in {time.time() - started:.0f} s\n\n")
        for name, outcome, detail in results:
            report.write(f"[{outcome}] {name}\n")
            if detail:
                report.write(f"    {detail}\n")
    print(open(os.path.join(out_dir, "report.txt"), encoding="utf-8").read())
    log_file.close()

sys.exit(1 if failed else 0)
