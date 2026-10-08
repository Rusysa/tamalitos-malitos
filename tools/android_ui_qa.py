#!/usr/bin/env python3
"""Text-first native Android QA. Refuses physical devices by default."""
import argparse
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from typing import Literal, overload

DEVICE = os.environ.get("TAMALITOS_QA_DEVICE", "emulator-5554")
OUTPUT = Path(os.environ.get("TMPDIR", str(Path.home() / ".hermes/cache/scratch"))) / "tamalitos-native-qa"

@overload
def adb(*args: str, binary: Literal[False] = False) -> str: ...
@overload
def adb(*args: str, binary: Literal[True]) -> bytes: ...
def adb(*args: str, binary: bool = False) -> str | bytes:
    run = subprocess.run(["adb", "-s", DEVICE, *args], check=True, capture_output=True)
    return run.stdout if binary else run.stdout.decode("utf-8", errors="replace")

def require_emulator():
    if adb("shell", "getprop", "ro.kernel.qemu").strip() != "1":
        raise SystemExit("QA requires an emulator; refusing to change a physical device.")

def tree():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    adb("shell", "uiautomator", "dump", "/sdcard/tamalitos-qa.xml")
    text = adb("shell", "cat", "/sdcard/tamalitos-qa.xml")
    (OUTPUT / "current.xml").write_text(text, encoding="utf-8")
    return ET.fromstring(text)

def dump():
    for node in tree().iter("node"):
        a = node.attrib
        if a.get("text") or a.get("content-desc"):
            print({k:a.get(k) for k in ("text", "content-desc", "class", "clickable", "bounds")})

def tap(label, description=False, occurrence=0):
    matches = [n for n in tree().iter("node") if n.get("content-desc" if description else "text") == label]
    if occurrence >= len(matches):
        raise SystemExit("No visible matching control: " + label)
    bounds = [int(v) for v in re.findall(r"\d+", matches[occurrence].get("bounds", ""))]
    if len(bounds) != 4:
        raise SystemExit("Control has no actionable bounds")
    x1,y1,x2,y2 = bounds
    adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))
    time.sleep(.5)

def screenshot(name):
    OUTPUT.mkdir(parents=True, exist_ok=True)
    path = OUTPUT / (name + ".png")
    path.write_bytes(adb("exec-out", "screencap", "-p", binary=True))
    print(path)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["dump", "tap", "shot", "swipe", "back", "type"])
    parser.add_argument("value", nargs="?")
    parser.add_argument("--desc", action="store_true")
    parser.add_argument("--occurrence", type=int, default=0)
    args = parser.parse_args()
    require_emulator()
    if args.action == "dump": dump()
    elif args.action == "tap": tap(args.value, args.desc, args.occurrence)
    elif args.action == "shot": screenshot(args.value or "screen")
    elif args.action == "back": adb("shell", "input", "keyevent", "4")
    elif args.action == "type": adb("shell", "input", "text", args.value.replace(" ", "%s"))
    elif args.action == "swipe":
        coords = (args.value or "660,220,70,220").split(",")
        if len(coords) != 4: raise SystemExit("Expected x1,y1,x2,y2")
        adb("shell", "input", "swipe", *coords, "350")
