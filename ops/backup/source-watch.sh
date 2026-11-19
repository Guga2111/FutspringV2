#!/bin/sh
# PID 1: exiting kills every client in this dedicated network namespace.
# Polling cannot guarantee a byte-exact provider cap. The reviewed reservation
# includes a separate in-flight margin; enforced zero-cost feasibility is still required.
set -eu
deadline=$(( $(date +%s) + 1800 ))
while [ "$(date +%s)" -lt "$deadline" ]; do
  bytes=$(awk 'NR>2 && $1 != "lo:" {sum += $2 + $10} END {printf "%.0f",sum}' /proc/net/dev)
  if [ "$bytes" -ge "$SOURCE_STOP_BYTES" ]; then
    touch /work/egress-limit
    exit 77
  fi
  sleep 0.1
done
