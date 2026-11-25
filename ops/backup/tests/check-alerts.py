#!/usr/bin/env python3
"""Validate the actual Grafana PromQL expressions with offline Prometheus unit tests."""

import json
import subprocess
import tempfile
from pathlib import Path

import yaml

root = Path(__file__).resolve().parents[3]
rules = yaml.safe_load(
    (root / "ops/observability/grafana/provisioning/alerting/rules.yml").read_text()
)["groups"][0]["rules"]
expressions = {rule["uid"]: rule["data"][0]["model"]["expr"] for rule in rules}
cases = json.loads(Path(__file__).with_name("alerts.cases.json").read_text())
tests = []
for name, suffix, series, expected in cases:
    tests.append(
        {
            "name": name,
            "interval": "1m",
            "input_series": [
                {"series": key, "values": str(value)} for key, value in series.items()
            ],
            "promql_expr_test": [
                {
                    "expr": expressions["futspring-" + suffix],
                    "eval_time": "0m",
                    "exp_samples": [{"labels": "{}", "value": expected}],
                }
            ],
        }
    )
with tempfile.TemporaryDirectory(prefix="futspring-promql-") as directory:
    target = Path(directory) / "tests.json"
    target.write_text(
        json.dumps({"rule_files": [], "evaluation_interval": "1m", "tests": tests})
    )
    subprocess.run(
        [
            "docker",
            "run",
            "--rm",
            "--pull=never",
            "--network=none",
            "--entrypoint=/bin/promtool",
            "-v",
            f"{target}:/tests.json:ro",
            "prom/prometheus:v3.15.1",
            "test",
            "rules",
            "/tests.json",
        ],
        check=True,
        timeout=60,
    )
