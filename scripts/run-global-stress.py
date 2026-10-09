#!/usr/bin/env python3
"""Run full correctness tests and paired live 1024-target pressure measurements.

Keeps optional runtime mods, uses one Gradle worker and fresh sequential JVMs,
and retains failed logs/reports. Capacity TPS is derived from MSPT, not measured TPS.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import time
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--phase", choices=("all", "server", "unit", "mixed", "import", "export"), default="all")
    args = parser.parse_args()
    project = Path(__file__).resolve().parent.parent
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    output = (args.output or project / "build" / f"global-stress-{stamp}").resolve()
    output.mkdir(parents=True, exist_ok=True)
    temporary = output / "tmp"
    temporary.mkdir(exist_ok=True)
    environment = os.environ.copy()
    environment.update(TEMP=str(temporary), TMP=str(temporary))
    wrapper = project / ("gradlew.bat" if os.name == "nt" else "gradlew")
    init = project / "scripts/global-stress.init.gradle"

    def git(*arguments):
        return subprocess.check_output(["git", *arguments], cwd=project, text=True).strip()

    def source_digest():
        digest = hashlib.sha256()
        paths = sorted(path for path in (project / "src").rglob("*") if path.is_file())
        paths.extend(project / name for name in ("build.gradle", "gradle.properties"))
        for path in paths:
            digest.update(path.relative_to(project).as_posix().encode())
            digest.update(path.read_bytes())
        return digest.hexdigest()

    manifest_path = output / "manifest.json"
    snapshot = source_digest()
    if manifest_path.exists():
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        if manifest["sourceSha256"] != snapshot:
            raise RuntimeError("Source changed; choose a new output directory for this revision")
    else:
        manifest = {"startedUtc": datetime.now(timezone.utc).isoformat(), "project": str(project),
                    "head": git("rev-parse", "HEAD"), "dirty": bool(git("status", "--porcelain")),
                    "sourceSha256": snapshot, "initSha256": hashlib.sha256(init.read_bytes()).hexdigest(),
                    "settings": {"workers": 1, "gameHeapGiB": 3, "warmupTicks": 200,
                                 "sampleTicks": 1200, "targets": 1024, "keysOrSlots": 27,
                                 "extendedAePlusRuntime": True, "freshSequentialJvms": True, "freshWorldPerPhase": True,
                                 "capacityTpsKind": "derived_from_mean_mspt_not_measured_tps"},
                    "runs": []}

    def save():
        manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")

    def run(label, arguments, *, game=False, benchmark=False):
        if source_digest() != snapshot or hashlib.sha256(init.read_bytes()).hexdigest() != manifest["initSha256"]:
            raise RuntimeError("Source or test settings changed during the run")
        attempt = 1 + sum(entry["label"] == label for entry in manifest["runs"])
        log = output / f"{label}-attempt{attempt}.log"
        phase = f"{label}-attempt{attempt}"
        command = [str(wrapper), "-Dnet.minecraftforge.gradle.check.certs=false",
                   f"-Djava.io.tmpdir={temporary}", f'-Dorg.gradle.jvmargs=-Xmx2g -Djava.io.tmpdir="{temporary}"',
                   "--offline", "--no-daemon", "--max-workers=1", "--console=plain", "-x", "downloadAssets",
                   "-I", str(init), f"-Pae2ltGlobalStressOutput={output}", f"-Pae2ltGlobalStressPhase={phase}"]
        command += arguments
        print(f"START {label}; log={log}", flush=True)
        started = time.time()
        record = {"label": label, "attempt": attempt, "command": command, "log": str(log), "status": "RUNNING"}
        manifest["runs"].append(record)
        save()
        with log.open("w", encoding="utf-8") as stream:
            completed = subprocess.run(command, cwd=project, env=environment, stdout=stream, stderr=subprocess.STDOUT)
        content = log.read_text(encoding="utf-8", errors="replace")
        ok = completed.returncode == 0 and "BUILD SUCCESSFUL" in content
        record.update(exitCode=completed.returncode, elapsedSeconds=round(time.time() - started, 2))
        if game:
            registered = re.findall(r"(\d+) tests are now running!", content)
            passed = re.findall(r"All (\d+) required tests passed", content)
            record["registeredGameTests"] = int(registered[-1]) if registered else 0
            record["reportedGameTestsPassed"] = int(passed[-1]) if passed else 0
            record["explicitSkips"] = sorted(set(re.findall(r"MIGRATION_\w+_SKIPPED", content)))
            ok &= bool(registered and passed and registered[-1] == passed[-1])
            record["migrationMetrics"] = re.findall(r"MIGRATION_100K_PHYSICAL[^\r\n]+", content)
        if benchmark:
            scenario = next(arg.split("=", 1)[1] for arg in arguments if arg.startswith("-Pae2ltBenchmarkScenario="))
            fresh = [path for path in (output / "raw-benchmarks").glob(f"*{scenario}.json")
                     if path.stat().st_mtime >= started]
            if len(fresh) == 1:
                report = json.loads(fresh[0].read_text(encoding="utf-8"))
                destination = output / f"{label}-attempt{attempt}.json"
                shutil.copyfile(fresh[0], destination)
                record["report"] = str(destination)
                record["metrics"] = report
                ok &= not report["partial"] and report["samples"] == 1200
            else:
                ok = False
                record["reportError"] = f"Expected one fresh benchmark report, got {len(fresh)}"
        record["status"] = "PASS" if ok else "FAIL"
        save()
        print(f"{record['status']} {label}: {record['elapsedSeconds']} seconds", flush=True)
        return record

    if args.phase in ("all", "unit"):
        record = run("unit-and-adaptive-stress", ["test", "adaptiveBatchStress"])
        record["junit"] = {}
        for suite in ("test", "adaptiveBatchStress"):
            totals = dict.fromkeys(("tests", "failures", "errors", "skipped"), 0)
            for path in (project / "build/test-results" / suite).glob("TEST-*.xml"):
                root = ET.parse(path).getroot()
                for key in totals:
                    totals[key] += int(root.get(key, "0"))
            record["junit"][suite] = totals
            if not totals["tests"] or any(totals[key] for key in ("failures", "errors", "skipped")):
                record["status"] = "FAIL"
        save()

    if args.phase in ("all", "server", "mixed"):
        namespaces = ",".join(("ae2lt", "minecraft", "ae2lt_io", "ae2lt_interface_input", "ae2lt_main_fixes",
                               "ae2lt_jei_supply", "ae2lt_deferred", "ae2lt_protection", "ae2lt_quantum",
                               "ae2lt_overload", "ae2lt_catalyzer", "ae2lt_machine_recharge", "ae2lt_railgun",
                               "ae2lt_addon_api", "ae2lt_overload_refill", "ae2lt_migration", "ae2lt_provider_energy"))
        run("mixed-server", ["runInterfaceIoGameTestServer", f"-Pae2ltInterfaceIoTestNamespaces={namespaces}",
                             "-Pae2ltAddonApiTests=true", "-Pae2ltInscriberFixture=true",
                             "-Penable_extendedae_plus_runtime=true"], game=True)

    for category, profile in (("import", "1024x27"), ("export", "export-continuous-1024x27")):
        if args.phase not in ("all", "server", category):
            continue
        pair = {}
        for role in ("control", "stress"):
            scenario = f"gametest-{role}-{profile}-run1"
            pair[role] = run(f"{category}-{role}", ["runWirelessIoGameTestServer",
                f"-Pae2ltBenchmarkScenario={scenario}", f"-Pae2ltBenchmarkControl={str(role == 'control').lower()}",
                "-Pae2ltBenchmarkWarmupTicks=200", "-Pae2ltBenchmarkSampleTicks=1200",
                "-Pae2ltBenchmarkIncludeEligibility=true", "-Pae2ltBenchmarkDiagnostics=false",
                "-Pae2ltBenchmarkCommit=global-stress-working-tree", f"-Pae2ltBenchmarkGitHead={manifest['head']}",
                f"-Pae2ltBenchmarkWorktreeDirty={str(manifest['dirty']).lower()}",
                "-Penable_extendedae_plus_runtime=true"], game=True, benchmark=True)
        if all(entry["status"] == "PASS" for entry in pair.values()):
            run(f"{category}-performance-budget", ["checkWirelessIoBenchmark",
                f"-Pae2ltBenchmarkStressReport={pair['stress']['report']}",
                f"-Pae2ltBenchmarkControlReport={pair['control']['report']}"])

    manifest["finishedUtc"] = datetime.now(timezone.utc).isoformat()
    manifest["result"] = "PASS" if all(record["status"] == "PASS" for record in manifest["runs"]) else "FAIL"
    save()
    print(f"{manifest['result']}: {manifest_path}", flush=True)
    return 0 if manifest["result"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
