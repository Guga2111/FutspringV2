"""Host-only operational helpers. No third-party Python dependencies."""

import contextlib
import fcntl
import hashlib
import json
import os
import resource
import signal
import subprocess
import tempfile
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
IMAGE = "postgres:17-alpine"
KINDS = ("daily", "pre-deploy")
CEILING = 8_000_000_000  # decimal GB; a local guard, never a billing guarantee
MANIFEST_LIMIT = 1_000_000


class Failure(Exception):
    pass


class BudgetFailure(Failure):
    pass


def private_file(path):
    path = Path(path)
    st = path.lstat()
    if (
        not path.is_file()
        or path.is_symlink()
        or st.st_uid != os.geteuid()
        or st.st_mode & 0o077
    ):
        raise Failure("Configuração privada inválida (proprietário/permissões).")
    return path


def read_json(path):
    value = json.loads(private_file(path).read_text())
    if not isinstance(value, dict):
        raise Failure("Configuração JSON deve ser um objeto.")
    return value


def atomic(path, content, mode=0o600):
    path = Path(path)
    fd, name = tempfile.mkstemp(prefix=".backup-", dir=path.parent)
    try:
        with os.fdopen(fd, "w") as out:
            os.fchmod(out.fileno(), mode)
            out.write(content)
            out.flush()
            os.fsync(out.fileno())
        os.replace(name, path)
        directory = os.open(path.parent, os.O_RDONLY)
        try:
            os.fsync(directory)
        finally:
            os.close(directory)
    finally:
        if os.path.exists(name):
            os.unlink(name)


def save_json(path, value):
    atomic(path, json.dumps(value, sort_keys=True) + "\n")


def sha256(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


@contextlib.contextmanager
def locked(path, wait=30):
    with open(path, "a") as lock:
        end = time.monotonic() + wait
        while True:
            try:
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
                break
            except BlockingIOError:
                if time.monotonic() >= end:
                    raise Failure(
                        "Outra execução mantém a trava; prazo esgotado."
                    ) from None
                time.sleep(0.1)
        yield


class Runner:
    def __init__(self, seconds=1800):
        self.end = time.monotonic() + seconds
        # Do not inherit AWS/PG credentials, proxy settings, Docker contexts or CLI overrides.
        self.env = {
            k: os.environ[k] for k in ("PATH", "HOME", "DOCKER_HOST") if k in os.environ
        }
        self.env.update({"LC_ALL": "C", "TZ": "UTC"})

    def run(
        self,
        args,
        *,
        data=None,
        timeout=120,
        env=None,
        input_stream=None,
        file_limit=None,
    ):
        remaining = min(timeout, self.end - time.monotonic())
        if remaining <= 0:
            raise Failure("Prazo total esgotado.")
        process = subprocess.Popen(
            args,
            stdin=input_stream
            or (subprocess.PIPE if data is not None else subprocess.DEVNULL),
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            start_new_session=True,
            env=env or self.env,
            preexec_fn=(
                lambda: resource.setrlimit(
                    resource.RLIMIT_FSIZE, (file_limit, file_limit)
                )
            )
            if file_limit
            else None,
        )
        try:
            output, _ = process.communicate(data, timeout=remaining)
        except BaseException:
            os.killpg(process.pid, signal.SIGKILL)
            process.wait()
            raise
        finally:
            if process.stdin:
                process.stdin.close()
            process.stdout.close()
        if process.returncode:
            # Raw libpq/AWS errors may include secrets or row contents. Never print them.
            raise Failure("Comando operacional falhou; detalhes sensíveis suprimidos.")
        return output.decode()

    def cleanup(self, args):
        try:
            subprocess.run(
                args,
                env=self.env,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                timeout=15,
                check=False,
            )
        except (OSError, subprocess.TimeoutExpired):
            pass


def metrics(directory, kind, values):
    lines = [
        f'futspring_backup_{key}{{kind="{kind}"}} {value}\n'
        for key, value in values.items()
    ]
    atomic(Path(directory) / f"futspring-backup-{kind}.prom", "".join(lines), 0o644)


def signals():
    def stop(_number, _frame):
        raise Failure("Execução interrompida.")

    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    signal.signal(signal.SIGALRM, stop)
    signal.alarm(
        1800
    )  # also bounds hashing, polling and local I/O outside subprocess calls
