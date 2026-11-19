#!/usr/bin/env python3
"""Restore a local verified archive ONLY into a new networkless temporary PostgreSQL."""

import argparse
import json
import os
import re
import shutil
import subprocess
import tempfile
import time
import uuid
from pathlib import Path

from common import HERE, IMAGE, MANIFEST_LIMIT, Failure, Runner, atomic, sha256, signals


def drill(archive, manifest_path, run=None):
    run = run or Runner()
    started = time.monotonic()
    manifest_path, archive = Path(manifest_path), Path(archive)
    if manifest_path.stat().st_size > MANIFEST_LIMIT:
        raise Failure("Manifesto excede o limite.")
    manifest = json.loads(manifest_path.read_text())
    if not isinstance(manifest, dict):
        raise Failure("Manifesto deve ser um objeto JSON.")
    if (
        type(manifest["format_version"]) is not int
        or manifest["format_version"] != 1
        or type(manifest["size_bytes"]) is not int
        or not 0 < manifest["size_bytes"] <= 512_000_000
        or archive.stat().st_size != manifest["size_bytes"]
        or not isinstance(manifest["sha256"], str)
        or not re.fullmatch(r"[a-f0-9]{64}", manifest["sha256"])
    ):
        raise Failure("Formato/tamanho inválido.")
    container = "futspring-restore-drill-" + uuid.uuid4().hex
    with tempfile.TemporaryDirectory(prefix="futspring-drill-") as staging:
        staged = Path(staging) / "database.dump"
        shutil.copyfile(archive, staged)
        if sha256(staged) != manifest["sha256"]:
            raise Failure("SHA-256 divergente; restauração recusada.")
        # Copy into container, never mount the host or credentials into restored SQL's reach.
        try:
            run.run(
                [
                    "docker",
                    "run",
                    "-d",
                    "--pull=never",
                    "--name",
                    container,
                    "--network=none",
                    "--user=postgres",
                    "--read-only",
                    "--cap-drop=ALL",
                    "--security-opt=no-new-privileges",
                    "--memory=768m",
                    "--cpus=1",
                    "--pids-limit=128",
                    "--tmpfs",
                    "/tmp:rw,noexec,nosuid,size=600m",
                    "--tmpfs",
                    "/var/lib/postgresql/data:rw,nosuid,size=2g,uid=70,gid=70",
                    "--tmpfs",
                    "/var/run/postgresql:rw,nosuid,size=16m,uid=70,gid=70",
                    "-e",
                    "POSTGRES_HOST_AUTH_METHOD=trust",
                    IMAGE,
                    "postgres",
                    "-c",
                    "listen_addresses=",
                    "-c",
                    "statement_timeout=1200000",
                ]
            )
            ready = time.monotonic() + 45
            while True:
                try:
                    run.run(
                        ["docker", "exec", container, "pg_isready", "-U", "postgres"],
                        timeout=5,
                    )
                    break
                except Failure:
                    if time.monotonic() >= ready:
                        raise Failure("PostgreSQL descartável não iniciou.") from None
                    time.sleep(0.2)
            with staged.open("rb") as stream:
                run.run(
                    [
                        "docker",
                        "exec",
                        "-i",
                        container,
                        "sh",
                        "-c",
                        "cat > /tmp/database.dump",
                    ],
                    input_stream=stream,
                )
            run.run(
                [
                    "docker",
                    "exec",
                    container,
                    "createdb",
                    "-U",
                    "postgres",
                    "-T",
                    "template0",
                    "drill",
                ]
            )
            run.run(
                [
                    "docker",
                    "exec",
                    container,
                    "pg_restore",
                    "-U",
                    "postgres",
                    "--dbname=drill",
                    "--clean",
                    "--if-exists",
                    "--no-owner",
                    "--no-acl",
                    "--exit-on-error",
                    "--single-transaction",
                    "/tmp/database.dump",
                ],
                timeout=1200,
            )
            psql = [
                "docker",
                "exec",
                "-i",
                container,
                "psql",
                "-U",
                "postgres",
                "-d",
                "drill",
                "-XqAt",
                "-v",
                "ON_ERROR_STOP=1",
            ]
            actual = json.loads(
                run.run(psql, data=(HERE / "inspect.sql").read_bytes(), timeout=1200)
            )
            if actual != manifest["validation"]:
                raise Failure(
                    "Dados, constraints, índices ou histórico Flyway divergentes."
                )
            run.run(psql, data=(HERE / "sequences.sql").read_bytes(), timeout=120)
            return {
                "verified_at": int(time.time()),
                "duration_seconds": round(time.monotonic() - started, 3),
                "size_bytes": manifest["size_bytes"],
                "sha256": manifest["sha256"],
            }
        finally:
            run.cleanup(["docker", "rm", "-f", container])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive")
    parser.add_argument("manifest")
    parser.add_argument("--metrics-dir")
    args = parser.parse_args()
    os.umask(0o077)
    signals()
    try:
        result = drill(args.archive, args.manifest)
        if args.metrics_dir:
            atomic(
                Path(args.metrics_dir) / "futspring-restore.prom",
                f"futspring_backup_restore_timestamp_seconds {result['verified_at']}\n",
                0o644,
            )
        print(json.dumps(result))
    except (
        Failure,
        OSError,
        KeyError,
        ValueError,
        TypeError,
        subprocess.TimeoutExpired,
    ) as error:
        if isinstance(error, Failure):
            print(
                str(error)
            )  # only our fixed, sanitized messages; never raw command output
        print(
            "Teste de recuperação falhou; alvo descartável removido. Nenhuma conexão de produção aceita."
        )
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
