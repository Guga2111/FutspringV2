#!/usr/bin/env python3
"""One fail-closed entry point for daily and pre-deploy database protection."""

import argparse
import configparser
import json
import os
import re
import shutil
import subprocess
import tempfile
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path

from common import (
    CEILING,
    HERE,
    IMAGE,
    KINDS,
    MANIFEST_LIMIT,
    BudgetFailure,
    Failure,
    Runner,
    locked,
    metrics,
    private_file,
    read_json,
    save_json,
    sha256,
    signals,
)


def integer(value):
    if type(value) is not int or value < 0:
        raise BudgetFailure("Orçamento inválido/desconhecido.")
    return value


def reserve(path, config, now):
    """The private account-wide ledger is operator reconciled, not bucket inventory.

    Debit worst-case costs durably BEFORE any network call; never refund failed or
    uncertain requests and never subtract lifecycle-eligible objects. All writers
    must share this ledger/lock or reserve their envelopes in external_reserved.
    """
    try:
        b = read_json(path)
    except (Failure, OSError, ValueError):
        raise BudgetFailure(
            "Estado de orçamento ausente, ilegível ou inválido."
        ) from None
    if (
        b.get("complete_account_usage") is not True
        or b.get("writers_coordinated") is not True
        or b.get("source_usage_known") is not True
        or b.get("invalidated") is not False
        or b.get("month") != datetime.fromtimestamp(now, timezone.utc).strftime("%Y-%m")
        or b.get("month")
        != datetime.fromtimestamp(now + 1800, timezone.utc).strftime("%Y-%m")
        or not now - 86400 <= integer(b.get("observed_at")) <= now
        or integer(b.get("valid_until")) < now + 1800
    ):
        raise BudgetFailure(
            "Orçamento desconhecido, vencido ou sem coordenação da conta."
        )
    cost = {
        "storage_bytes": 2 * (config["max_archive_bytes"] + MANIFEST_LIMIT),
        "class_a": 4,
        "class_b": 4,
        "source_bytes": integer(b.get("source_run_reservation_bytes")),
    }
    if not cost["source_bytes"] or not b.get("source_measurement_reference"):
        raise BudgetFailure("Falta medição conservadora do tráfego da origem.")
    if not 0 < integer(b.get("source_inflight_margin_bytes")) < cost["source_bytes"]:
        raise BudgetFailure("Falta margem medida para bytes em trânsito.")
    for key in cost:
        item = b[key]
        limit = integer(item["limit"])
        maximum = {
            "storage_bytes": CEILING,
            "class_a": 800_000,
            "class_b": 8_000_000,
            "source_bytes": 5_000_000_000,
        }[key]
        total = sum(
            integer(item[k])
            for k in ("used", "external_reserved", "local_reserved", "safety_margin")
        )
        if limit > maximum or total + cost[key] > limit:
            raise BudgetFailure("Reserva conservadora excede o orçamento disponível.")
    for key in cost:
        b[key]["local_reserved"] += cost[key]
    save_json(path, b)
    return b, cost


def validate(config, now, validation_only=False):
    try:
        approval = read_json(config["activation_file"])
    except (Failure, OSError, ValueError):
        raise BudgetFailure(
            "Evidência de ativação ausente, ilegível ou inválida."
        ) from None
    if (
        (
            approval.get("zero_additional_cost_enforced") is not True
            and approval.get("owner_r2_usage_charges_accepted") is not True
        )
        or approval.get(
            "owner_validation_accepted"
            if validation_only
            else "owner_activation_accepted"
        )
        is not True
        or not approval.get("billing_control_evidence")
        or (
            not validation_only
            and not approval.get("verified_backup_restore_alert_evidence")
        )
        or integer(approval.get("valid_until")) < now + 1800
        or approval.get("bucket") != config["bucket"]
    ):
        raise BudgetFailure(
            "Ativação bloqueada: controle financeiro/aceite não comprovados."
        )
    if not re.fullmatch(
        r"https://[a-f0-9]{32}\.r2\.cloudflarestorage\.com", config["endpoint"]
    ):
        raise Failure("Endpoint R2 inválido.")
    if not re.fullmatch(r"[a-z0-9][a-z0-9-]{1,61}[a-z0-9]", config["bucket"]):
        raise Failure("Bucket inválido.")
    if not 1 <= integer(config["max_archive_bytes"]) <= 512_000_000:
        raise Failure("Limite do arquivo inválido (máximo 512 MB, PUT simples).")
    credentials = Path(config["pg_credentials_dir"])
    service = configparser.ConfigParser(interpolation=None)
    service.read(private_file(credentials / "pg_service.conf"))
    pg = dict(service["backup"])
    if (
        pg.get("sslmode") != "verify-full"
        or pg.get("sslrootcert") != "/credentials/root.crt"
        or pg.get("passfile") != "/credentials/pgpass"
        or pg.get("port") != "5432"
        or pg.get("connect_timeout") != "10"
        or "password" in pg
        or "options" in pg
        or not {"host", "dbname", "user"} <= pg.keys()
    ):
        raise Failure("Origem deve usar sessão/direto 5432, verify-full e passfile.")
    for filename in ("pgpass", "root.crt"):
        private_file(credentials / filename)
    private_file(config["aws_credentials_file"])
    # Remote configuration is reviewed with a separate admin credential, never held here.
    if (
        approval.get("private_standard_bucket") is not True
        or approval.get("lock_days") != 7
        or approval.get("daily_retention_days") != 30
        or approval.get("pre_deploy_retention_days") != 7
    ):
        raise Failure("Proteção/retenção do bucket não comprovada.")


def source_dump(run, config, work, container, source_stop_bytes):
    run.run(
        [
            "docker",
            "run",
            "-d",
            "--pull=never",
            "--name",
            container,
            "--user",
            f"{os.geteuid()}:{os.getegid()}",
            "--network",
            config.get("source_network", "bridge"),
            "--read-only",
            "--cap-drop=ALL",
            "--security-opt=no-new-privileges",
            "--memory=512m",
            "--cpus=1",
            "--pids-limit=128",
            "--ulimit",
            f"fsize={config['max_archive_bytes']}:{config['max_archive_bytes']}",
            "--tmpfs",
            "/tmp:rw,noexec,nosuid,size=16m",
            "-v",
            f"{Path(config['pg_credentials_dir']).resolve()}:/credentials:ro",
            "-v",
            f"{work}:/work",
            "-v",
            f"{HERE}:/tool:ro",
            "-e",
            "PGSERVICEFILE=/credentials/pg_service.conf",
            "-e",
            "PGSERVICE=backup",
            "-e",
            "PGOPTIONS=-c statement_timeout=1200000 -c lock_timeout=10000",
            "-e",
            f"SOURCE_STOP_BYTES={source_stop_bytes}",
            IMAGE,
            "sh",
            "/tool/source-watch.sh",
        ]
    )
    run.run(
        [
            "docker",
            "exec",
            "-d",
            container,
            "sh",
            "-c",
            "psql -XqAt -v ON_ERROR_STOP=1 -f /tool/snapshot.sql >/work/source-error 2>&1",
        ]
    )
    deadline = time.monotonic() + 20
    snapshot = work / "snapshot.json"
    while not snapshot.exists() or not snapshot.stat().st_size:
        if (work / "egress-limit").exists():
            raise BudgetFailure("Tráfego da origem atingiu o limite.")
        if time.monotonic() >= deadline:
            raise Failure("Snapshot não disponível; acesso à origem falhou.")
        time.sleep(0.1)
    info = json.loads(snapshot.read_text())
    if not re.fullmatch(r"[0-9A-F-]+", info["id"]):
        raise Failure("Identificador de snapshot inválido.")
    prefix = f"BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY; SET TRANSACTION SNAPSHOT '{info['id']}';\n"
    run.run(
        ["docker", "exec", "-i", container, "psql", "-XqAt", "-v", "ON_ERROR_STOP=1"],
        data=(prefix + (HERE / "preflight.sql").read_text() + "\nROLLBACK;").encode(),
    )
    info["validation"] = json.loads(
        run.run(
            [
                "docker",
                "exec",
                "-i",
                container,
                "psql",
                "-XqAt",
                "-v",
                "ON_ERROR_STOP=1",
            ],
            data=(prefix + (HERE / "inspect.sql").read_text() + "\nROLLBACK;").encode(),
            timeout=1200,
        )
    )
    run.run(
        [
            "docker",
            "exec",
            container,
            "pg_dump",
            "-Fc",
            "--compress=gzip:6",
            "--schema=public",
            "--strict-names",
            "--no-owner",
            "--no-acl",
            "--snapshot",
            info["id"],
            "--file=/work/database.dump",
        ],
        timeout=1200,
    )
    toc = run.run(
        ["docker", "exec", container, "pg_restore", "--list", "/work/database.dump"]
    )
    for table in (
        "users",
        "peladas",
        "dailies",
        "finance_movements",
        "flyway_schema_history",
    ):
        if f"TABLE DATA public {table} " not in toc:
            raise Failure("Arquivo sem dados obrigatórios da aplicação.")
    if not (work / "database.dump").stat().st_size:
        raise Failure("Arquivo vazio.")
    info["client"] = run.run(
        ["docker", "exec", container, "pg_dump", "--version"]
    ).strip()
    # Dedicated network namespace: includes preflight, fingerprints, snapshot and dump,
    # TLS and protocol overhead, both directions. No source retry is performed.
    net = run.run(["docker", "exec", container, "cat", "/proc/net/dev"])
    info["source_wire_bytes"] = sum(
        int(v[1]) + int(v[9])
        for line in net.splitlines()[2:]
        if (v := line.split()) and v[0] != "lo:"
    )
    return info


def s3(run, config, operation, key, path):
    env = dict(
        run.env,
        AWS_SHARED_CREDENTIALS_FILE=config["aws_credentials_file"],
        AWS_CONFIG_FILE="/dev/null",
        AWS_EC2_METADATA_DISABLED="true",
        AWS_MAX_ATTEMPTS="1",
        AWS_DEFAULT_REGION="auto",
        AWS_PAGER="",
        AWS_REQUEST_CHECKSUM_CALCULATION="when_required",
        AWS_RESPONSE_CHECKSUM_VALIDATION="when_required",
    )
    args = [
        "aws",
        "--endpoint-url",
        config["endpoint"],
        "--cli-connect-timeout",
        "10",
        "--cli-read-timeout",
        "60",
        "s3api",
        operation,
        "--bucket",
        config["bucket"],
        "--key",
        key,
    ]
    if operation == "put-object":
        args += ["--body", str(path), "--storage-class", "STANDARD"]
    else:
        args.append(str(path))
    run.run(
        args,
        timeout=180,
        env=env,
        file_limit=MANIFEST_LIMIT
        if key.endswith(".json")
        else config["max_archive_bytes"],
    )


def upload(run, config, work, kind, info, commit):
    archive = work / "database.dump"
    digest, size = sha256(archive), archive.stat().st_size
    if size > config["max_archive_bytes"]:
        raise Failure("Arquivo excede a reserva; nenhum envio permitido.")
    for attempt in range(2):
        # An uncertain PUT is never overwritten/reused, even within the same run.
        key = (
            f"database/{kind}/{datetime.now(timezone.utc):%Y-%m-%d}/{uuid.uuid4().hex}"
        )
        manifest = {
            "format_version": 1,
            "snapshot_at": info["time"],
            "kind": kind,
            "archive_key": key + "/database.dump",
            "size_bytes": size,
            "sha256": digest,
            "postgres_server": info["server"],
            "pg_dump": info["client"],
            "commit": commit,
            "source_wire_bytes": info["source_wire_bytes"],
            "validation": info["validation"],
        }
        save_json(work / "manifest.json", manifest)
        if (work / "manifest.json").stat().st_size > MANIFEST_LIMIT:
            raise Failure("Manifesto excede a reserva.")
        try:
            s3(run, config, "put-object", manifest["archive_key"], archive)
            s3(
                run,
                config,
                "get-object",
                manifest["archive_key"],
                work / "download.dump",
            )
            if (work / "download.dump").stat().st_size != size or sha256(
                work / "download.dump"
            ) != digest:
                raise Failure("Integridade do arquivo remoto inválida.")
            s3(
                run,
                config,
                "put-object",
                key + "/manifest.json",
                work / "manifest.json",
            )
            s3(
                run,
                config,
                "get-object",
                key + "/manifest.json",
                work / "download.json",
            )
            if (work / "download.json").read_bytes() != (
                work / "manifest.json"
            ).read_bytes():
                raise Failure("Integridade do manifesto remoto inválida.")
            return manifest
        except Failure:
            if attempt == 1:
                raise
    raise AssertionError("unreachable")


def execute(config, kind, commit, run=None, validation_only=False):
    run = run or Runner()
    state_dir = Path(config["state_dir"])
    state_dir.mkdir(parents=True, exist_ok=True, mode=0o700)
    if (
        state_dir.stat().st_mode & 0o077
        or state_dir.is_symlink()
        or state_dir.stat().st_uid != os.geteuid()
    ):
        raise Failure("Diretório de estado deve ser privado.")
    with locked(state_dir / "run.lock"):
        now = int(time.time())
        last_path = state_dir / f"{kind}.json"
        state_error = False
        try:
            last = read_json(last_path) if last_path.exists() else {}
        except (Failure, OSError, ValueError):
            last, state_error = {}, True
        values = {
            "attempt_timestamp_seconds": now,
            "success": -1,
            "budget_blocked": 0,
            "snapshot_timestamp_seconds": last.get("snapshot_timestamp_seconds", 0),
            "size_bytes": last.get("size_bytes", 0),
            "duration_seconds": 0,
            "prospective_storage_bytes": 0,
        }
        container = "futspring-backup-" + uuid.uuid4().hex
        work = None
        checking_budget = True
        try:
            metrics(config["metrics_dir"], kind, values)
            if state_error:
                raise Failure("Estado anterior inválido; revisão necessária.")
            validate(config, now, validation_only)
            budget, cost = reserve(config["budget_file"], config, now)
            checking_budget = False
            values["prospective_storage_bytes"] = sum(
                budget["storage_bytes"][k]
                for k in (
                    "used",
                    "external_reserved",
                    "local_reserved",
                    "safety_margin",
                )
            )
            if (
                shutil.disk_usage(state_dir).free
                < 3 * config["max_archive_bytes"] + 20_000_000
            ):
                raise Failure("Espaço temporário insuficiente.")
            work = Path(tempfile.mkdtemp(prefix="staging-", dir=state_dir))
            try:
                info = source_dump(
                    run,
                    config,
                    work,
                    container,
                    cost["source_bytes"] - budget["source_inflight_margin_bytes"],
                )
            except BaseException:
                if (work / "egress-limit").exists():
                    budget["invalidated"] = True
                    save_json(config["budget_file"], budget)
                    raise BudgetFailure(
                        "Limite de tráfego da origem atingido; orçamento invalidado."
                    ) from None
                raise
            run.cleanup(["docker", "rm", "-f", container])
            if info["source_wire_bytes"] > cost["source_bytes"]:
                budget["invalidated"] = True
                save_json(config["budget_file"], budget)
                raise BudgetFailure(
                    "Tráfego medido excede a reserva; orçamento invalidado."
                )
            manifest = upload(run, config, work, kind, info, commit)
            values.update(
                snapshot_timestamp_seconds=int(
                    datetime.fromisoformat(manifest["snapshot_at"]).timestamp()
                ),
                size_bytes=manifest["size_bytes"],
                success=1,
            )
            save_json(last_path, values)
        except BudgetFailure:
            values["budget_blocked"] = 1
            raise
        except (OSError, ValueError, KeyError, TypeError, configparser.Error):
            values["budget_blocked"] = int(checking_budget)
            raise
        finally:
            run.cleanup(["docker", "rm", "-f", container])
            if work:
                shutil.rmtree(work)
            values["duration_seconds"] = round(time.time() - now, 3)
            if values["success"] != 1:
                values["success"] = 0
            metrics(config["metrics_dir"], kind, values)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("kind", choices=KINDS)
    parser.add_argument("--config", default="/etc/futspring-backup/config.json")
    parser.add_argument("--commit", default=None)
    parser.add_argument(
        "--validation-only",
        action="store_true",
        help="Manual acceptance rehearsal; still requires financial evidence and owner acceptance.",
    )
    args = parser.parse_args()
    os.umask(0o077)
    signals()
    try:
        if args.commit and not re.fullmatch(r"[a-f0-9]{40}", args.commit):
            raise Failure("Commit inválido.")
        execute(
            read_json(args.config),
            args.kind,
            args.commit,
            validation_only=args.validation_only,
        )
    except (
        Failure,
        OSError,
        ValueError,
        KeyError,
        TypeError,
        configparser.Error,
        subprocess.TimeoutExpired,
    ) as error:
        if isinstance(error, Failure):
            print(
                str(error)
            )  # only fixed, sanitized errors; never raw libpq/AWS output
        print(
            "Backup falhou ou foi bloqueado; nenhuma publicação autorizada. Confira configuração/orçamento e métricas."
        )
        return 1
    print("Backup remoto e manifesto verificados.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
