"""Summarize jfr print --json --events jdk.VirtualThreadPinned from stdin."""
import json
import re
import sys


def milliseconds(duration):
    match = re.fullmatch(r"PT(?:(\d+(?:\.\d+)?)H)?(?:(\d+(?:\.\d+)?)M)?(?:(\d+(?:\.\d+)?)S)?", duration)
    if not match or not any(match.groups()):
        raise ValueError(f"Unsupported JFR duration: {duration}")
    hours, minutes, seconds = (float(value or 0) for value in match.groups())
    return (hours * 3600 + minutes * 60 + seconds) * 1000


if __name__ == "__main__":
    events = json.load(sys.stdin)["recording"]["events"]
    durations = [milliseconds(event["values"]["duration"]) for event in events]
    print(f"{len(events)} events, {sum(value > 20 for value in durations)} longer than 20 ms "
          f"(max {max(durations, default=0):.3f} ms; recording threshold 0 ms)")
