"""Synthetic tests. Default suite has no network; integration uses only local Docker."""

import copy
import importlib.util
import json
import multiprocessing
import os
import shlex
import subprocess
import sys
import tempfile
import textwrap
import time
import unittest
import uuid
from datetime import datetime, timezone
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import backup
from common import (
    HERE,
    BudgetFailure,
    Failure,
    Runner,
    locked,
    read_json,
    save_json,
    sha256,
)

spec = importlib.util.spec_from_file_location(
    "restore_drill", HERE / "restore-drill.py"
)
restore = importlib.util.module_from_spec(spec)
spec.loader.exec_module(restore)


def setup(root):
    config = json.loads((HERE / "config.example.json").read_text())
    for key, filename in (
        ("budget_file", "budget.json"),
        ("activation_file", "activation.json"),
        ("aws_credentials_file", "r2.credentials"),
    ):
        config[key] = str(root / filename)
    config.update(
        pg_credentials_dir=str(root / "pg"),
        state_dir=str(root / "state"),
        metrics_dir=str(root / "metrics"),
        max_archive_bytes=1_000_000,
        endpoint="https://" + "a" * 32 + ".r2.cloudflarestorage.com",
        lock_wait_seconds=0.2,
    )
    for key in ("pg_credentials_dir", "state_dir", "metrics_dir"):
        Path(config[key]).mkdir(mode=0o700)
    pg = Path(config["pg_credentials_dir"])
    for filename, content in (
        ("pg_service.conf", (HERE / "pg_service.example.conf").read_text()),
        ("pgpass", "source:5432:postgres:postgres:synthetic-only\n"),
        ("root.crt", "synthetic"),
    ):
        (pg / filename).write_text(content)
        (pg / filename).chmod(0o600)
    Path(config["aws_credentials_file"]).write_text("synthetic-only")
    Path(config["aws_credentials_file"]).chmod(0o600)
    now = int(time.time())
    approval = json.loads((HERE / "activation.example.json").read_text())
    approval.update(
        zero_additional_cost_enforced=True,
        owner_activation_accepted=True,
        billing_control_evidence="SYNTHETIC: no external calls",
        private_standard_bucket=True,
        verified_backup_restore_alert_evidence="synthetic",
        valid_until=now + 86400,
    )
    save_json(config["activation_file"], approval)
    b = json.loads((HERE / "budget.example.json").read_text())
    b.update(
        month=time.strftime("%Y-%m", time.gmtime()),
        observed_at=now,
        valid_until=now + 86400,
        complete_account_usage=True,
        writers_coordinated=True,
        source_usage_known=True,
        source_run_reservation_bytes=10_000_000,
        source_inflight_margin_bytes=1_000_000,
        source_measurement_reference="synthetic measured envelope",
    )
    save_json(config["budget_file"], b)
    return config


class LedgerTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.config = setup(self.root)

    def test_validations_and_disabled_example(self):
        backup.validate(self.config, int(time.time()))
        save_json(
            self.config["activation_file"],
            json.loads((HERE / "activation.example.json").read_text()),
        )
        with self.assertRaises(BudgetFailure):
            backup.validate(self.config, int(time.time()))

    def test_unknown_expired_uncoordinated_fail_before_io(self):
        original = read_json(self.config["budget_file"])
        for changes in (
            {"complete_account_usage": False},
            {"writers_coordinated": False},
            {"source_usage_known": False},
            {"observed_at": 0},
            {"valid_until": 0},
            {"month": "2000-01"},
            {"source_run_reservation_bytes": 0},
            {"invalidated": True},
        ):
            with self.subTest(changes=changes):
                save_json(self.config["budget_file"], dict(original, **changes))
                with patch.object(backup, "source_dump") as dump:
                    with self.assertRaises(BudgetFailure):
                        backup.execute(self.config, "daily", None)
                    dump.assert_not_called()
                self.assertIn(
                    'budget_blocked{kind="daily"} 1',
                    (self.root / "metrics/futspring-backup-daily.prom").read_text(),
                )
        Path(self.config["budget_file"]).unlink()
        with self.assertRaises(BudgetFailure):
            backup.reserve(self.config["budget_file"], self.config, int(time.time()))

    def test_r2_billing_acceptance_never_implies_activation_or_known_budget(self):
        approval = read_json(self.config["activation_file"])
        approval.update(zero_additional_cost_enforced=False)
        save_json(self.config["activation_file"], approval)
        with self.assertRaises(BudgetFailure):
            backup.validate(self.config, int(time.time()))
        approval["owner_r2_usage_charges_accepted"] = True
        save_json(self.config["activation_file"], approval)
        backup.validate(self.config, int(time.time()))
        for changes in (
            {"owner_activation_accepted": False},
            {"billing_control_evidence": None},
            {"verified_backup_restore_alert_evidence": None},
            {"valid_until": 0},
        ):
            save_json(self.config["activation_file"], dict(approval, **changes))
            with self.subTest(changes=changes), self.assertRaises(BudgetFailure):
                backup.validate(self.config, int(time.time()))
        approval.update(owner_activation_accepted=False, owner_validation_accepted=True)
        save_json(self.config["activation_file"], approval)
        backup.validate(self.config, int(time.time()), validation_only=True)
        with self.assertRaises(BudgetFailure):
            backup.validate(self.config, int(time.time()))
        b = read_json(self.config["budget_file"])
        b["source_usage_known"] = False
        save_json(self.config["budget_file"], b)
        with patch.object(backup, "source_dump") as dump:
            with self.assertRaises(BudgetFailure):
                backup.execute(self.config, "daily", None, validation_only=True)
            dump.assert_not_called()

    def test_boundaries_include_all_account_reservations_and_safety(self):
        original = read_json(self.config["budget_file"])
        costs = {
            "storage_bytes": 4_000_000,
            "class_a": 4,
            "class_b": 4,
            "source_bytes": 10_000_000,
        }
        for key, cost in costs.items():
            b = copy.deepcopy(original)
            b[key].update(
                used=b[key]["limit"] - cost - 100,
                external_reserved=50,
                local_reserved=25,
                safety_margin=25,
            )
            save_json(self.config["budget_file"], b)
            backup.reserve(
                self.config["budget_file"], self.config, int(time.time())
            )  # equality allowed
            with self.assertRaises(BudgetFailure):
                backup.reserve(
                    self.config["budget_file"], self.config, int(time.time())
                )
            b[key]["external_reserved"] += 1
            save_json(self.config["budget_file"], b)
            with self.assertRaises(BudgetFailure):
                backup.reserve(
                    self.config["budget_file"], self.config, int(time.time())
                )

    def test_run_cannot_cross_unknown_monthly_allowance(self):
        now = int(datetime(2026, 11, 30, 23, 45, tzinfo=timezone.utc).timestamp())
        b = read_json(self.config["budget_file"])
        b.update(month="2026-11", observed_at=now, valid_until=now + 86400)
        save_json(self.config["budget_file"], b)
        with self.assertRaises(BudgetFailure):
            backup.reserve(self.config["budget_file"], self.config, now)
        self.assertEqual(read_json(self.config["budget_file"]), b)

    def test_malformed_private_service_never_logs_its_content(self):
        config_path = self.root / "config.json"
        save_json(config_path, self.config)
        secret = "SYNTHETIC-SECRET-NEVER-LOG"
        (self.root / "pg/pg_service.conf").write_text(secret + "\n")
        result = subprocess.run(
            [
                sys.executable,
                str(HERE / "backup.py"),
                "daily",
                "--config",
                str(config_path),
            ],
            capture_output=True,
            text=True,
            timeout=10,
        )
        self.assertEqual(result.returncode, 1)
        self.assertNotIn(secret, result.stdout + result.stderr)
        self.assertEqual(result.stderr, "")

    def test_untrusted_permissions_tls_and_missing_config(self):
        path = Path(self.config["aws_credentials_file"])
        path.chmod(0o644)
        with self.assertRaises(Failure):
            backup.validate(self.config, int(time.time()))
        path.chmod(0o600)
        pg = self.root / "pg/pg_service.conf"
        pg.write_text(pg.read_text().replace("verify-full", "require"))
        with self.assertRaises(Failure):
            backup.validate(self.config, int(time.time()))

    def test_lock_wait_and_timeout(self):
        def hold(path, ready):
            with locked(path):
                ready.set()
                time.sleep(0.5)

        # fork is explicit for the local helper (works on Linux and macOS).
        ctx = multiprocessing.get_context("fork")
        ready = ctx.Event()
        child = ctx.Process(target=hold, args=(self.root / "lock", ready))
        child.start()
        try:
            self.assertTrue(ready.wait(3))
            with self.assertRaises(Failure):
                with locked(self.root / "lock", 0.1):
                    self.fail("overlapping execution")
        finally:
            child.join(3)
        with self.assertRaises(subprocess.TimeoutExpired):
            Runner().run(
                [sys.executable, "-c", "import time; time.sleep(2)"], timeout=0.05
            )

    def test_dump_failure_keeps_debits_and_cleans(self):
        def fail(_run, _config, work, _container, _stop):
            (work / "partial.dump").write_bytes(b"partial")
            raise Failure("synthetic denied access")

        with (
            patch.object(backup, "source_dump", side_effect=fail),
            patch.object(Runner, "cleanup"),
        ):
            with self.assertRaises(Failure):
                backup.execute(self.config, "pre-deploy", None)
        self.assertEqual(list((self.root / "state").glob("staging-*")), [])
        self.assertFalse((self.root / "state/pre-deploy.json").exists())
        budget = read_json(self.config["budget_file"])
        self.assertEqual(budget["source_bytes"]["local_reserved"], 10_000_000)
        self.assertEqual(budget["class_a"]["local_reserved"], 4)

    def test_source_guard_invalidates_ledger_and_cleans(self):
        def fail(_run, _config, work, _container, _stop):
            (work / "egress-limit").touch()
            raise Failure("synthetic source stop")

        with (
            patch.object(backup, "source_dump", side_effect=fail),
            patch.object(Runner, "cleanup"),
        ):
            with self.assertRaises(BudgetFailure):
                backup.execute(self.config, "daily", None)
        self.assertTrue(read_json(self.config["budget_file"])["invalidated"])
        self.assertFalse(list((self.root / "state").glob("staging-*")))

    def test_oversize_archive_never_uploads(self):
        work = self.root / "state"
        (work / "database.dump").write_bytes(
            b"a" * (self.config["max_archive_bytes"] + 1)
        )
        with patch.object(backup, "s3") as upload, self.assertRaises(Failure):
            backup.upload(Runner(), self.config, work, "daily", {}, None)
        upload.assert_not_called()

    def test_child_file_limit_bounds_download_disk_usage(self):
        target = self.root / "large"
        with self.assertRaises(Failure):
            Runner().run(
                [
                    sys.executable,
                    "-c",
                    "import sys; f=open(sys.argv[1],'wb'); f.write(b'x'*4096); f.flush()",
                    str(target),
                ],
                file_limit=1024,
            )
        self.assertLessEqual(target.stat().st_size, 1024)

    def test_s3_no_multipart_hidden_retries_or_env_credentials(self):
        class Capture(Runner):
            def run(self, args, **kw):
                self.args, self.kw = args, kw

        r = Capture()
        with patch.dict(
            os.environ,
            {
                "AWS_SECRET_ACCESS_KEY": "must-not-inherit",
                "PGPASSWORD": "must-not-inherit",
            },
        ):
            backup.s3(
                r,
                self.config,
                "put-object",
                "unique/database.dump",
                self.root / "a.dump",
            )
        self.assertEqual(r.kw["env"]["AWS_MAX_ATTEMPTS"], "1")
        self.assertNotIn("AWS_SECRET_ACCESS_KEY", r.kw["env"])
        self.assertNotIn("PGPASSWORD", r.kw["env"])
        self.assertIn("s3api", r.args)
        self.assertNotIn("must-not-inherit", str(r.args))

    def test_upload_failure_corruption_and_manifest_readback(self):
        work = self.root / "state"
        (work / "database.dump").write_bytes(b"synthetic archive")
        info = {
            "time": "2026-11-19T06:00:00+00:00",
            "server": "17",
            "client": "17",
            "source_wire_bytes": 100,
            "validation": {},
        }
        for fail in ("upload", "download", "archive-corrupt", "manifest-corrupt"):
            objects = {}
            keys = []

            def fake(
                _run, _config, op, key, path, keys=keys, objects=objects, fail=fail
            ):
                keys.append((op, key))
                if op == "put-object":
                    if fail == "upload":
                        raise Failure("synthetic upload denied")
                    self.assertNotIn(key, objects)
                    objects[key] = path.read_bytes()
                elif fail == "download":
                    raise Failure("synthetic download denied")
                else:
                    path.write_bytes(
                        objects[key]
                        + (
                            b"!"
                            if (key.endswith(".dump") and fail == "archive-corrupt")
                            or (key.endswith(".json") and fail == "manifest-corrupt")
                            else b""
                        )
                    )

            with self.subTest(fail=fail), patch.object(backup, "s3", side_effect=fake):
                with self.assertRaises(Failure):
                    backup.upload(Runner(), self.config, work, "daily", info, None)
            self.assertLessEqual(sum(op == "put-object" for op, _ in keys), 4)
            self.assertLessEqual(sum(op == "get-object" for op, _ in keys), 4)

    def test_restore_rejects_corrupt_archive_before_docker(self):
        archive, manifest = self.root / "database.dump", self.root / "manifest.json"
        archive.write_bytes(b"bad")
        save_json(manifest, {"format_version": 1, "size_bytes": 3, "sha256": "0" * 64})
        with patch.object(Runner, "run") as command:
            with self.assertRaises(Failure):
                restore.drill(archive, manifest)
            command.assert_not_called()

    def test_deploy_gate_precedes_every_publication_and_no_optional_backup(self):
        text = (HERE.parents[1] / "scripts/deploy.sh").read_text()
        gate = text.index("python3 /opt/futspring-backup/backup.py pre-deploy")
        self.assertLess(gate, text.index("rsync -az"))
        self.assertLess(gate, text.index('scp "${ENV_FILE}"'))
        self.assertLess(
            gate, text.index("docker compose -f docker-compose.prod.yml up")
        )
        self.assertNotIn("PG_DUMP_URL", text)
        self.assertNotIn("sql.gz", text)

    def test_ssh_bootstrap_requires_pinned_host_without_network(self):
        for workflow in ("deploy.yml", "deploy-observability.yml"):
            text = (HERE.parents[1] / ".github/workflows" / workflow).read_text()
            block = text.split("      - name: Configure SSH key\n", 1)[1]
            block = block.split("        run: |\n", 1)[1]
            script = textwrap.dedent(block.split("\n      - name:", 1)[0])
            for known_hosts in (None, "", "192.0.2.1 ssh-ed25519 SYNTHETIC"):
                with self.subTest(workflow=workflow, known_hosts=known_hosts):
                    with tempfile.TemporaryDirectory(dir=self.root) as directory:
                        root = Path(directory)
                        stubs = root / "bin"
                        stubs.mkdir()
                        scanned = root / "network-called"
                        scanner = stubs / "ssh-keyscan"
                        scanner.write_text(
                            "#!/bin/sh\nprintf scanned > "
                            + shlex.quote(str(scanned))
                            + "\n"
                        )
                        scanner.chmod(0o700)
                        ssh_dir = root / "ssh"
                        # Redirect only file paths, never validation or control flow.
                        # Do not touch the developer's real SSH directory or HOME.
                        isolated = script.replace("~/.ssh", shlex.quote(str(ssh_dir)))
                        env = {
                            "PATH": str(stubs) + os.pathsep + os.defpath,
                            "SSH_PRIVATE_KEY": "SYNTHETIC-PRIVATE-KEY",
                            "VPS_IP": "192.0.2.1",
                            "VPS_USER": "synthetic",
                        }
                        if known_hosts is not None:
                            env["SSH_KNOWN_HOSTS"] = known_hosts
                        result = subprocess.run(
                            ["bash", "-c", isolated],
                            env=env,
                            capture_output=True,
                            timeout=10,
                        )
                        self.assertFalse(scanned.exists())
                        if known_hosts:
                            self.assertEqual(result.returncode, 0)
                            self.assertEqual(
                                (ssh_dir / "known_hosts").read_text(),
                                known_hosts + "\n",
                            )
                            self.assertIn(
                                "StrictHostKeyChecking yes",
                                (ssh_dir / "config").read_text(),
                            )
                            self.assertEqual(
                                (ssh_dir / "id_deploy").stat().st_mode & 0o777, 0o600
                            )
                        else:
                            self.assertNotEqual(result.returncode, 0)
                            self.assertFalse(ssh_dir.exists())


@unittest.skipUnless(
    os.environ.get("BACKUP_DOCKER_TESTS") == "1", "opt-in local Docker integration"
)
class DockerTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory(prefix="backup-synthetic-")
        cls.root = Path(cls.tmp.name)
        cls.config = setup(cls.root)
        cls.runner = Runner(1200)
        cls.network = "futspring-test-" + uuid.uuid4().hex
        cls.source = "futspring-source-" + uuid.uuid4().hex
        cls.addClassCleanup(cls.cleanup)
        cls.runner.run(["docker", "network", "create", "--internal", cls.network])
        cls.runner.run(
            [
                "docker",
                "run",
                "-d",
                "--pull=never",
                "--name",
                cls.source,
                "--network",
                cls.network,
                "--network-alias",
                "source",
                "--memory=512m",
                "--cpus=1",
                "--tmpfs",
                "/var/lib/postgresql/data:rw,size=512m",
                "-e",
                "POSTGRES_PASSWORD=synthetic-only",
                backup.IMAGE,
            ]
        )
        for _ in range(100):
            try:
                cls.runner.run(
                    [
                        "docker",
                        "exec",
                        cls.source,
                        "sh",
                        "-c",
                        'test "$(cat /proc/1/comm)" = postgres && pg_isready -U postgres',
                    ]
                )
                break
            except Failure:
                time.sleep(0.1)
        pg = Path(cls.config["pg_credentials_dir"])
        cls.runner.run(
            [
                "openssl",
                "req",
                "-x509",
                "-newkey",
                "rsa:2048",
                "-nodes",
                "-days",
                "1",
                "-keyout",
                str(cls.root / "server.key"),
                "-out",
                str(pg / "root.crt"),
                "-subj",
                "/CN=source",
                "-addext",
                "subjectAltName=DNS:source",
            ]
        )
        (pg / "root.crt").chmod(0o600)
        cls.runner.run(
            [
                "docker",
                "cp",
                str(cls.root / "server.key"),
                cls.source + ":/tmp/server.key",
            ]
        )
        cls.runner.run(
            ["docker", "cp", str(pg / "root.crt"), cls.source + ":/tmp/server.crt"]
        )
        cls.runner.run(
            [
                "docker",
                "exec",
                cls.source,
                "chown",
                "postgres:postgres",
                "/tmp/server.key",
                "/tmp/server.crt",
            ]
        )
        cls.sql(
            "ALTER SYSTEM SET ssl='on';\nALTER SYSTEM SET ssl_cert_file='/tmp/server.crt';\n"
            "ALTER SYSTEM SET ssl_key_file='/tmp/server.key';\nSELECT pg_reload_conf();"
        )
        migrations = HERE.parents[1] / "core/src/main/resources/db/migration"
        cls.sql(
            "CREATE TABLE flyway_schema_history (installed_rank int PRIMARY KEY, version varchar(50), success boolean);"
        )
        for file in sorted(
            migrations.glob("V*.sql"), key=lambda p: int(p.name.split("__")[0][1:])
        ):
            n = int(file.name.split("__")[0][1:])
            cls.sql(
                file.read_text()
                + f"\nINSERT INTO flyway_schema_history VALUES({n},'{n}',true);"
            )
        cls.sql((HERE / "tests/seed.sql").read_text())
        cls.sql(
            "ALTER TABLE users ENABLE ROW LEVEL SECURITY; "
            "CREATE ROLE backup_reader LOGIN BYPASSRLS; "
            "GRANT CONNECT ON DATABASE postgres TO backup_reader; "
            "GRANT USAGE ON SCHEMA public TO backup_reader; "
            "GRANT SELECT ON ALL TABLES IN SCHEMA public TO backup_reader; "
            "GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO backup_reader; "
            "ALTER ROLE backup_reader SET default_transaction_read_only=on;"
        )
        cls.sql("\\password backup_reader\nsynthetic-only\nsynthetic-only\n")
        (pg / "pg_service.conf").write_text(
            (HERE / "pg_service.example.conf")
            .read_text()
            .replace("SESSION_OR_DIRECT_HOST", "source")
            .replace("BACKUP_READER", "backup_reader")
        )
        (pg / "pgpass").write_text(
            "source:5432:postgres:backup_reader:synthetic-only\n"
        )
        cls.config["source_network"] = cls.network
        cls.config["max_archive_bytes"] = 5_000_000

    @classmethod
    def cleanup(cls):
        cls.runner.cleanup(["docker", "rm", "-f", cls.source])
        cls.runner.cleanup(["docker", "network", "rm", cls.network])
        cls.tmp.cleanup()

    @classmethod
    def sql(cls, sql):
        return cls.runner.run(
            [
                "docker",
                "exec",
                "-i",
                cls.source,
                "psql",
                "-U",
                "postgres",
                "-XqAt",
                "-v",
                "ON_ERROR_STOP=1",
            ],
            data=sql.encode(),
        )

    def test_real_dump_restore_and_failures(self):
        objects = {}
        original_run = self.runner.run
        concurrent_write = False

        def change_after_inspection(args, **kwargs):
            nonlocal concurrent_write
            if "pg_dump" in args and "-Fc" in args and not concurrent_write:
                concurrent_write = True
                self.sql(
                    "INSERT INTO users(email,password,username) VALUES('later@invalid','synthetic','Depois do snapshot');"
                )
            return original_run(args, **kwargs)

        def storage(_run, _config, op, key, path):
            if op == "put-object":
                self.assertNotIn(key, objects)
                objects[key] = path.read_bytes()
            else:
                path.write_bytes(objects[key])

        with (
            patch.object(backup, "s3", side_effect=storage),
            patch.object(self.runner, "run", side_effect=change_after_inspection),
        ):
            backup.execute(self.config, "daily", "a" * 40, self.runner)
            backup.execute(self.config, "pre-deploy", "b" * 40, self.runner)
        self.assertEqual(len(objects), 4)
        manifest_key = next(k for k in objects if k.endswith("manifest.json"))
        manifest = json.loads(objects[manifest_key])
        self.assertGreater(manifest["source_wire_bytes"], 0)
        self.assertTrue(concurrent_write)
        self.assertEqual(self.sql("SELECT count(*) FROM users;").strip(), "2")
        self.assertIn("<rows>1</rows>", manifest["validation"]["tables"]["users"])
        self.assertIn("finance_movements", manifest["validation"]["tables"])
        self.assertEqual(manifest["validation"]["row_security"]["users"], [True, False])
        archive_path, manifest_path = (
            self.root / "verified.dump",
            self.root / "manifest.json",
        )
        archive_path.write_bytes(objects[manifest["archive_key"]])
        manifest_path.write_bytes(objects[manifest_key])
        checked_log_targets = []

        def inspect_restore_logging(args, **kwargs):
            output = original_run(args, **kwargs)
            if args[:2] == ["docker", "run"] and "--name" in args:
                target = args[args.index("--name") + 1]
                if target.startswith("futspring-restore-drill-"):
                    driver = original_run(
                        [
                            "docker",
                            "inspect",
                            "--format",
                            "{{.HostConfig.LogConfig.Type}}",
                            target,
                        ]
                    ).strip()
                    self.assertEqual(driver, "none")
                    checked_log_targets.append(target)
            if "pg_restore" in args and "--dbname=drill" in args:
                target = args[2]
                marker = b"SYNTHETIC-RESTORE-ROW-MUST-NOT-REACH-LOGS"
                original_run(
                    ["docker", "exec", "-i", target, "psql", "-U", "postgres"],
                    data=b"DO $$ BEGIN RAISE LOG '" + marker + b"'; END $$;",
                )
                logs = subprocess.run(
                    ["docker", "logs", target],
                    capture_output=True,
                    timeout=10,
                    check=False,
                )
                self.assertNotIn(marker, logs.stdout + logs.stderr)
            return output

        with patch.object(self.runner, "run", side_effect=inspect_restore_logging):
            result = restore.drill(archive_path, manifest_path, self.runner)
        self.assertEqual(len(checked_log_targets), 1)
        print("Synthetic restore evidence:", json.dumps(result))
        self.assertGreater(result["size_bytes"], 0)
        self.assertFalse(list((self.root / "state").glob("staging-*")))
        prior_success = read_json(self.root / "state/daily.json")
        # Same real SQL path must reject lack of SELECT permissions and schema dependencies.
        self.sql(
            "CREATE ROLE denied LOGIN PASSWORD 'synthetic-only'; GRANT USAGE ON SCHEMA public TO denied;"
        )
        pg = Path(self.config["pg_credentials_dir"])
        original = (pg / "pg_service.conf").read_text()
        (pg / "pg_service.conf").write_text(
            original.replace("user=backup_reader", "user=denied")
        )
        (pg / "pgpass").write_text("source:5432:postgres:denied:synthetic-only\n")
        with self.assertRaises(Failure), patch.object(backup, "s3") as upload:
            backup.execute(self.config, "daily", None, self.runner)
        upload.assert_not_called()
        self.assertEqual(read_json(self.root / "state/daily.json"), prior_success)
        self.assertIn(
            'success{kind="daily"} 0',
            (self.root / "metrics/futspring-backup-daily.prom").read_text(),
        )
        self.assertFalse(list((self.root / "state").glob("staging-*")))
        # SELECT grants alone must not permit a filtered/empty RLS backup.
        self.sql(
            "GRANT SELECT ON ALL TABLES IN SCHEMA public TO denied; "
            "GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO denied;"
        )
        with self.assertRaises(Failure), patch.object(backup, "s3") as upload:
            backup.execute(self.config, "daily", None, self.runner)
        upload.assert_not_called()
        self.assertEqual(read_json(self.root / "state/daily.json"), prior_success)
        (pg / "pg_service.conf").write_text(original)
        (pg / "pgpass").write_text(
            "source:5432:postgres:backup_reader:synthetic-only\n"
        )
        self.sql("CREATE POLICY needs_review ON users FOR SELECT USING (true);")
        with self.assertRaises(Failure), patch.object(backup, "s3") as upload:
            backup.execute(self.config, "daily", None, self.runner)
        upload.assert_not_called()
        self.sql("DROP POLICY needs_review ON users;")
        self.sql(
            "CREATE SCHEMA dependency; CREATE TYPE dependency.custom AS ENUM ('a'); CREATE TABLE public.unsupported(x dependency.custom);"
        )
        with self.assertRaises(Failure), patch.object(backup, "s3") as upload:
            backup.execute(self.config, "daily", None, self.runner)
        upload.assert_not_called()
        self.sql("DROP TABLE public.unsupported; DROP SCHEMA dependency CASCADE;")
        # Constraints are restored, data/history and stale sequences are actually detected.
        bad = copy.deepcopy(manifest)
        bad["validation"]["row_security"]["users"] = [False, False]
        save_json(manifest_path, bad)
        with self.assertRaises(Failure):
            restore.drill(archive_path, manifest_path, self.runner)
        bad = copy.deepcopy(manifest)
        bad["validation"]["flyway"] = "changed"
        save_json(manifest_path, bad)
        with self.assertRaises(Failure):
            restore.drill(archive_path, manifest_path, self.runner)
        self.sql("SELECT setval('users_id_seq',1,false);")
        with tempfile.TemporaryDirectory(dir=self.root) as w:
            container = "futspring-backup-" + uuid.uuid4().hex
            try:
                info = backup.source_dump(
                    self.runner, self.config, Path(w), container, 9_000_000
                )
            finally:
                self.runner.cleanup(["docker", "rm", "-f", container])
            bad.update(
                validation=info["validation"],
                size_bytes=(Path(w) / "database.dump").stat().st_size,
                sha256=sha256(Path(w) / "database.dump"),
            )
            save_json(manifest_path, bad)
            with self.assertRaises(Failure):
                restore.drill(Path(w) / "database.dump", manifest_path, self.runner)
        budget = read_json(self.config["budget_file"])
        budget.update(
            source_run_reservation_bytes=1_000_001,
            source_inflight_margin_bytes=1_000_000,
        )
        save_json(self.config["budget_file"], budget)
        with self.assertRaises(BudgetFailure), patch.object(backup, "s3") as upload:
            backup.execute(self.config, "daily", None, self.runner)
        upload.assert_not_called()
        self.assertTrue(read_json(self.config["budget_file"])["invalidated"])


if __name__ == "__main__":
    unittest.main()
