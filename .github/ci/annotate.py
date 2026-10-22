"""Turns what a failed CI step left behind into annotations on the run.

The logs of a run can only be read from a browser or a client that reaches GitHub's log storage;
annotations come with the run itself, through the same API as its jobs. So a failed step says here,
in at most ten lines, what failed and where: a compiler error, a failed test, a lint error, a file
that is not formatted.

Usage: annotate.py kotlin <log> | spotless <log> | tests | lint
"""

import glob
import os
import re
import sys
import xml.etree.ElementTree as ET

# GitHub keeps ten error annotations per step.
MOST = 10
WORKSPACE = os.environ.get("GITHUB_WORKSPACE", os.getcwd()).rstrip("/") + "/"


def relative(path):
    path = path.removeprefix("file://")
    return path[len(WORKSPACE):] if path.startswith(WORKSPACE) else path


def escape_data(text):
    return text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def escape_property(text):
    return escape_data(text).replace(":", "%3A").replace(",", "%2C")


def error(message, file=None, line=None, col=None, title=None):
    properties = []
    if file:
        properties.append("file=" + escape_property(file))
    if line:
        properties.append("line=" + str(line))
    if col:
        properties.append("col=" + str(col))
    if title:
        properties.append("title=" + escape_property(title))
    print("::error " + ",".join(properties) + "::" + escape_data(message))


def emit(found, what):
    """Annotates the first findings, and says how many there were in all."""
    for item in found[:MOST - 1]:
        error(**item)
    if len(found) >= MOST:
        error("%d %s in all; the first %d are above" % (len(found), what, MOST - 1))
    elif not found:
        error("the step failed, but no %s were found to point at" % what)


def kotlin(log):
    found = []
    pattern = re.compile(r"^e: (?:file://)?(\S+?):(\d+):(\d+) (.*)$")
    for line in open(log, encoding="utf-8", errors="replace"):
        match = pattern.match(line.strip())
        if match:
            path, number, column, message = match.groups()
            found.append(dict(message=message, file=relative(path), line=number, col=column))
        elif line.startswith("> Task") and line.rstrip().endswith("FAILED"):
            found.append(dict(message=line.strip(), title="failed task"))
    emit(found, "compiler errors")


def spotless(log):
    found = []
    text = open(log, encoding="utf-8", errors="replace").read()
    for match in re.finditer(r"^\s+(\S+\.kts?)$", text, re.MULTILINE):
        found.append(dict(message="not formatted as ktfmt does it", file=relative(match.group(1))))
    emit(found, "files with format violations")


def tests():
    found = []
    for path in glob.glob("**/build/test-results/**/*.xml", recursive=True):
        for case in ET.parse(path).getroot().iter("testcase"):
            failure = case.find("failure")
            if failure is None:
                failure = case.find("error")
            if failure is None:
                continue
            first = (failure.get("message") or failure.text or "").strip().splitlines()[:3]
            found.append(
                dict(
                    message="%s > %s: %s" % (case.get("classname"), case.get("name"), " / ".join(first)),
                    title="failed test",
                )
            )
    emit(found, "failed tests")


def lint():
    found = []
    for path in glob.glob("**/build/reports/lint-results-*.xml", recursive=True):
        for issue in ET.parse(path).getroot().iter("issue"):
            if issue.get("severity") not in ("Error", "Fatal"):
                continue
            location = issue.find("location")
            found.append(
                dict(
                    message="[%s] %s" % (issue.get("id"), issue.get("message")),
                    file=relative(location.get("file")) if location is not None else None,
                    line=location.get("line") if location is not None else None,
                )
            )
    emit(found, "lint errors")


if __name__ == "__main__":
    kind = sys.argv[1]
    if kind in ("kotlin", "spotless"):
        if os.path.exists(sys.argv[2]):
            globals()[kind](sys.argv[2])
        else:
            error("the step failed before it wrote %s" % sys.argv[2])
    else:
        globals()[kind]()
