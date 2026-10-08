#!/usr/bin/env python3
"""Capture the measurement host for Table 5 (evaluation environment).

Writes evaluation/env.json consumed by the paper's environment table:
CPU, RAM, GPU, OS, service execution mode (JVM/native), Ollama + model
versions. Degrades gracefully when probes are unavailable (e.g. no GPU,
no Ollama running locally) and records nothing that cannot be verified.

Usage:
  python3 evaluation/env_probe.py [--mode JVM|native]
"""
import argparse
import json
import os
import platform
import shutil
import subprocess
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE / "env.json"


def cpu_info() -> dict:
    info: dict = {}
    try:
        info["count"] = os.cpu_count()
    except Exception:
        pass
    model = None
    try:
        with open("/proc/cpuinfo") as fh:
            for line in fh:
                if line.lower().startswith("model name"):
                    model = line.split(":", 1)[1].strip()
                    break
    except OSError:
        pass
    if model:
        info["model"] = model
    else:
        try:
            info["processor"] = platform.processor()
        except Exception:
            pass
    return info


def ram_gb() -> float | None:
    try:
        with open("/proc/meminfo") as fh:
            for line in fh:
                if line.startswith("MemTotal:"):
                    kb = int(line.split()[1])
                    return round(kb / (1024 * 1024), 2)
    except (OSError, ValueError, IndexError):
        pass
    return None


def gpu_info() -> list[dict] | None:
    nvidia = shutil.which("nvidia-smi")
    if not nvidia:
        return None
    try:
        out = subprocess.run(
            [
                nvidia,
                "--query-gpu=name,memory.total,driver_version",
                "--format=csv,noheader,nounits",
            ],
            capture_output=True,
            text=True,
            timeout=30,
        )
        if out.returncode != 0:
            return None
        gpus = []
        for line in out.stdout.strip().splitlines():
            parts = [p.strip() for p in line.split(",")]
            gpus.append(
                {"name": parts[0], "memory_mb": int(parts[1]), "driver": parts[2]}
            )
        return gpus
    except Exception:
        return None


def java_info() -> dict | None:
    java = shutil.which("java")
    if not java:
        return None
    try:
        out = subprocess.run([java, "-version"], capture_output=True, text=True, timeout=30)
        first = (out.stderr or out.stdout).splitlines()[0].strip()
        return {"command": java, "version_line": first}
    except Exception:
        return None


def ollama_info() -> dict | None:
    endpoint = "http://localhost:11434/api/tags"
    try:
        with urllib.request.urlopen(endpoint, timeout=5) as resp:
            data = json.load(resp)
        models = sorted(m.get("name", "") for m in data.get("models", []))
        return {"host": "localhost", "reachable": True, "models": models}
    except Exception:
        return {"host": "localhost", "reachable": False, "models": None}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--mode", choices=["JVM", "native"], default="JVM")
    args = ap.parse_args()

    env = {
        "hostname": platform.node(),
        "os": platform.platform(),
        "machine": platform.machine(),
        "cpu": cpu_info(),
        "ram_gb_total_measured": ram_gb(),
        "gpu": gpu_info(),
        "service_execution_mode": args.mode,
        "python": platform.python_version(),
        "java": java_info(),
        "ollama": ollama_info(),
        "probe_timestamp_utc": __import__("datetime").datetime.now(
            __import__("datetime").timezone.utc
        ).isoformat(),
    }
    OUT.write_text(json.dumps(env, indent=2) + "\n")
    print(json.dumps(env, indent=2))
    print(f"\nwrote {OUT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())