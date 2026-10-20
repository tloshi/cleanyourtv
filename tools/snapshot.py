#!/usr/bin/env python3
"""A snapshot of the whole TV before anything changes, and the restore that puts it back.

    snapshot.py --save <folder>                  read the TV and write the snapshot into <folder>
    snapshot.py --restore <folder> [--dry-run]   undo what changed since; --dry-run only lists it

recovery.py records what one debloat list is about to change. This records the whole TV, list or no
list: every package's state, which app owns Home, the settings this project touches, and getprop and
every setting for reference. Restore compares the TV with the snapshot and undoes the differences, so
it also covers a package disabled by hand or a list edited along the way.

Restore never removes an app you installed yourself: the only package it uninstalls is this project's
launcher. Settings outside RESTORED_SETTINGS (volume, picture, anything the TV changes on its own) are
reported, not reverted.
"""
import json
import os
from pathlib import Path
import re
import shlex
import sys
import time

PACKAGE_RE = re.compile(r"[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+")
HOME_RE = re.compile(r"[A-Za-z_][\w.]*/[\w.$]+")
KEY_RE = re.compile(r"[A-Za-z0-9_.]+")
CUSTOM_LAUNCHER = "com.gathin.tvhome"
NAMESPACES = ("system", "secure", "global")
# The settings this project changes: animation scales, Private DNS, keyboard, the launcher's
# accessibility helpers and the Clock screensaver.
RESTORED_SETTINGS = {
    "global": ("window_animation_scale", "transition_animation_scale", "animator_duration_scale",
               "private_dns_mode", "private_dns_specifier"),
    "secure": ("default_input_method", "enabled_input_methods", "enabled_accessibility_services",
               "accessibility_enabled", "screensaver_components", "screensaver_enabled",
               "screensaver_default_component", "screensaver_activate_on_sleep"),
}
HOME_QUERY = "-a android.intent.action.MAIN -c android.intent.category.HOME"
# Kept for reference next to snapshot.json; restore reads only snapshot.json.
REFERENCE_DUMPS = {
    "getprop.txt": "getprop",
    "packages-detail.txt": "pm list packages -f -i -U -u",
    "home-candidates.txt": f"cmd package query-activities --brief {HOME_QUERY}",
    "leanback-apps.txt": "cmd package query-activities --brief -a android.intent.action.MAIN "
                         "-c android.intent.category.LEANBACK_LAUNCHER",
    "preferred-activities.txt": "dumpsys package preferred-activities",
    "input-methods.txt": "ime list -s",
    "wifi-network-owner.txt": "dumpsys wifi | grep -o 'cname=[^ ]*' | sort -u",
    "tv-inputs.txt": "dumpsys tv_input",
}
SNAPSHOT_FILE = "snapshot.json"
VERSION = 1


def connect(host):
    # tv.py reads TV_HOST when it is imported, so the host has to be in place first.
    os.environ["TV_HOST"] = host
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import tv
    return tv.connect()


def shell(dev, command):
    return (dev.shell(command, read_timeout_s=120.0) or "").strip()


def package_names(dev, flags=""):
    output = shell(dev, f"pm list packages {flags}".strip())
    return {line.split(":", 1)[1].strip() for line in output.splitlines() if line.startswith("package:")}


def parse_settings(listing):
    pairs = (line.split("=", 1) for line in listing.splitlines() if "=" in line)
    return {key.strip(): value for key, value in pairs}


def read_state(dev):
    """What restore compares: package states, Home and every setting."""
    installed = package_names(dev)
    if not installed:
        raise SystemExit("the TV returned no packages; check the connection with tools/tv.py --probe")
    disabled = package_names(dev, "-d")
    home = shell(dev, f"cmd package resolve-activity --brief {HOME_QUERY}").splitlines()
    home_component = home[-1].strip() if home else ""
    return {
        "packages": {name: "disabled" if name in disabled else "enabled" for name in sorted(installed)},
        # System packages still on the TV but removed for this user (pm uninstall --user 0).
        "not_for_user": sorted(package_names(dev, "-u") - installed),
        "home": home_component if HOME_RE.fullmatch(home_component) else None,
        "settings": {space: parse_settings(shell(dev, f"settings list {space}")) for space in NAMESPACES},
    }


def user_state_line(dev, name):
    """The `User 0:` line of a package dump: says whether it was disabled at the factory (enabled=2)
    or by a user (enabled=3)."""
    return shell(dev, f"dumpsys package {name} | grep -m1 ' User 0:'")


def save(folder, host):
    target = Path(folder) / SNAPSHOT_FILE
    if target.exists():
        raise SystemExit(f"{target} already exists. The first snapshot is the one that matters; "
                         "save a new one into another folder.")
    Path(folder).mkdir(parents=True, exist_ok=True)
    dev = connect(host)
    try:
        state = read_state(dev)
        for name, command in REFERENCE_DUMPS.items():
            (Path(folder) / name).write_text(shell(dev, command) + "\n", encoding="utf-8")
        details = [f"{name}: {user_state_line(dev, name)}"
                   for name, value in state["packages"].items() if value == "disabled"]
        (Path(folder) / "disabled-detail.txt").write_text("\n".join(details) + "\n", encoding="utf-8")
    finally:
        dev.close()
    snapshot = {"version": VERSION, "created": time.strftime("%Y-%m-%dT%H:%M:%S%z"), "host": host, **state}
    with open(target, "w", encoding="utf-8") as f:
        json.dump(snapshot, f, indent=2, sort_keys=True)
        f.write("\n")
    disabled_count = sum(1 for value in state["packages"].values() if value == "disabled")
    print(f"saved {len(state['packages'])} packages ({disabled_count} already disabled), "
          f"Home {state['home']}, {sum(len(s) for s in state['settings'].values())} settings -> {target}")


def setting_value(state, space, key):
    value = state["settings"].get(space, {}).get(key)
    return None if value in (None, "null") else value


def setting_commands(snapshot, current):
    commands = []
    for space, keys in RESTORED_SETTINGS.items():
        for key in keys:
            before, now = setting_value(snapshot, space, key), setting_value(current, space, key)
            if before == now or not KEY_RE.fullmatch(key):
                continue
            commands.append(f"settings delete {space} {key}" if before is None
                            else f"settings put {space} {key} {shlex.quote(before)}")
    return commands


def other_setting_changes(snapshot, current, space):
    """Settings outside RESTORED_SETTINGS that differ: reported, never reverted."""
    keys = set(snapshot["settings"].get(space, {})) | set(current["settings"].get(space, {}))
    return sorted(key for key in keys - set(RESTORED_SETTINGS.get(space, ()))
                  if setting_value(snapshot, space, key) != setting_value(current, space, key))


def package_commands(snapshot, current):
    """Put each recorded package back in its recorded state; returns (commands, notes)."""
    commands, notes = [], []
    for name, before in snapshot["packages"].items():
        now = current["packages"].get(name)
        if not PACKAGE_RE.fullmatch(name) or before == now:
            continue
        if now is None and name in current["not_for_user"]:
            commands.append(f"cmd package install-existing {name}")
        elif now is None:
            notes.append(f"{name} is gone from the TV; reinstall it from the Play Store if you miss it")
        elif before == "enabled":
            commands.append(f"pm enable {name}")
        else:
            commands.append(f"pm disable-user --user 0 {name}")
    for name in sorted(set(current["packages"]) - set(snapshot["packages"])):
        if name == CUSTOM_LAUNCHER:
            commands.append(f"pm uninstall {name}")
        else:
            notes.append(f"{name} was installed after the snapshot; left alone")
    return commands, notes


def plan(snapshot, current):
    """The commands that turn `current` back into `snapshot`, and notes for what they leave alone."""
    commands = setting_commands(snapshot, current)
    package_changes, notes = package_commands(snapshot, current)
    commands += package_changes
    # Android drops the Home preference whenever the set of Home apps changes, so it is set last.
    home = snapshot.get("home")
    if home and HOME_RE.fullmatch(home) and (package_changes or current.get("home") != home):
        commands.append(f"cmd package set-home-activity --user 0 {home}")
    for space in NAMESPACES:
        changed = other_setting_changes(snapshot, current, space)
        if changed:
            notes.append(f"{space} settings changed since, not reverted: {', '.join(changed)}")
    return commands, notes


def restore(folder, dry_run):
    with open(Path(folder) / SNAPSHOT_FILE, encoding="utf-8") as f:
        snapshot = json.load(f)
    if snapshot.get("version") != VERSION:
        raise SystemExit(f"{folder}: unknown snapshot format")
    dev = connect(os.environ.get("TV_HOST") or snapshot["host"])
    try:
        commands, notes = plan(snapshot, read_state(dev))
        print(f"snapshot from {snapshot['created']}: {len(commands)} change(s) to undo")
        for command in commands:
            print(f"$ {command}")
            if not dry_run:
                print(shell(dev, command))
        remaining = [] if dry_run else plan(snapshot, read_state(dev))[0]
    finally:
        dev.close()
    for note in notes:
        print(f"note: {note}")
    if dry_run:
        print("dry run: nothing was changed")
    elif remaining:
        print("still different from the snapshot:\n  " + "\n  ".join(remaining))
        return 1
    else:
        print("the TV matches the snapshot")
    return 0


def main():
    args = sys.argv[1:]
    if len(args) == 2 and args[0] == "--save":
        host = os.environ.get("TV_HOST") or sys.exit("Set TV_HOST to your TV's IP address first")
        save(args[1], host)
        return 0
    if args[:1] == ["--restore"] and len(args) in (2, 3) and args[2:] in ([], ["--dry-run"]):
        return restore(args[1], dry_run=args[2:] == ["--dry-run"])
    sys.stderr.write(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main())
