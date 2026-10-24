"""Run isolated real FE GameTests serially; retain reports, recycle disposable worlds on Windows."""
import argparse
import ctypes
import csv
import hashlib
import json
import os
from pathlib import Path
import platform
import statistics
import subprocess
import sys
import time


SCENARIOS = {
    "control256": (256, False, False, 0),
    "active256": (256, True, False, 0),
    "active1024": (1024, True, False, 0),
    "control8192": (8192, False, False, 0),
    "active8192": (8192, True, False, 0),
    "saturated8192": (8192, True, True, 0),
    "queries1024": (1024, True, False, 32),
    "maxbudget8192": (8192, True, False, 0),
}


def summarize(output):
    output = Path(output)
    manifest = json.loads((output / "manifest.json").read_text(encoding="utf-8"))
    groups = {}
    for run in manifest["runs"]:
        if not run["complete"]:
            raise ValueError(f"Cannot summarize incomplete run: {run['scenario']}")
        report = json.loads((output / (run["scenario"] + ".json")).read_text(encoding="utf-8"))
        groups.setdefault(run["scenario"].rsplit("-", 1)[0], []).append(report)
    rows = []
    for scenario, reports in groups.items():
        samples = sorted(n / 1e6 for report in reports for n in report["rawTickEventNanos"])
        row = {"scenario": scenario, "repeats": len(reports), "sampleTicks": len(samples),
               "meanMs": statistics.fmean(samples),
               "p95Ms": samples[(len(samples) * 95 + 99) // 100 - 1],
               "p99Ms": samples[(len(samples) * 99 + 99) // 100 - 1],
               "maxMs": samples[-1], "ticksOver50ms": sum(n > 50 for n in samples),
               "minRunMeanMs": min(r["tickEventMs"]["mean"] for r in reports),
               "maxRunMeanMs": max(r["tickEventMs"]["mean"] for r in reports),
               "meanDeliveredFePerTick": statistics.fmean(r["sampleDeliveredFePerTick"] for r in reports),
               "gcMillis": sum(r["gcMillis"] for r in reports)}
        if all("rawPostTickNanos" in r for r in reports):
            post = sorted(n / 1e6 for r in reports for n in r["rawPostTickNanos"])
            row.update({"postMeanMs": statistics.fmean(post),
                        "postP95Ms": post[(len(post) * 95 + 99) // 100 - 1],
                        "postP99Ms": post[(len(post) * 99 + 99) // 100 - 1],
                        "allocationCounterSupported": all(r["allocationCounterSupported"] for r in reports),
                        "meanTickAllocatedBytes": statistics.fmean(r["meanTickAllocatedBytes"] for r in reports),
                        "meanPostTickAllocatedBytes": statistics.fmean(r["meanPostTickAllocatedBytes"] for r in reports)})
        rows.append(row)
    (output / "summary.json").write_text(json.dumps(rows, indent=2), encoding="utf-8")
    with (output / "summary.csv").open("w", newline="", encoding="utf-8") as csv_file:
        writer = csv.DictWriter(csv_file, fieldnames=rows[0].keys()); writer.writeheader(); writer.writerows(rows)
    return rows


def recycle(directory, root):
    directory = directory.resolve()
    if root.resolve() not in directory.parents or directory == root.resolve():
        raise ValueError(f"Refusing cleanup outside benchmark directory: {directory}")
    if os.name != "nt":
        return False  # Never fall back to irreversible deletion.
    class SHFILEOPSTRUCT(ctypes.Structure):
        _fields_ = [("hwnd", ctypes.c_void_p), ("wFunc", ctypes.c_uint),
                    ("pFrom", ctypes.c_wchar_p), ("pTo", ctypes.c_wchar_p),
                    ("fFlags", ctypes.c_ushort), ("fAnyOperationsAborted", ctypes.c_int),
                    ("hNameMappings", ctypes.c_void_p), ("lpszProgressTitle", ctypes.c_wchar_p)]
    operation = SHFILEOPSTRUCT()
    operation.wFunc = 3  # FO_DELETE
    operation.pFrom = str(directory) + "\0\0"
    operation.fFlags = 0x40 | 0x10 | 0x4 | 0x400  # ALLOWUNDO, NOCONFIRMATION, SILENT, NOERRORUI
    result = ctypes.windll.shell32.SHFileOperationW(ctypes.byref(operation))
    return result == 0 and not operation.fAnyOperationsAborted and not directory.exists()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scenarios", nargs="+", choices=SCENARIOS, default=list(SCENARIOS))
    parser.add_argument("--repeats", type=int, default=3)
    parser.add_argument("--warmup", type=int, default=300)
    parser.add_argument("--samples", type=int, default=1200)
    parser.add_argument("--java-home")
    parser.add_argument("--cpu-affinity", type=lambda value: int(value, 0),
                        help="Windows CPU affinity mask applied inside each test JVM, e.g. 0xf")
    parser.add_argument("--keep-worlds", action="store_true")
    parser.add_argument("--jfr", action="store_true", help="Record a separate diagnostic workload; do not mix with timing comparisons")
    args = parser.parse_args()
    if args.repeats < 1 or args.warmup < 1 or args.samples < 128 or args.warmup + args.samples >= 23000:
        parser.error("repeats/warmup must be positive; samples >= 128; total ticks < 23000")
    if args.cpu_affinity is not None:
        if os.name != "nt" or args.cpu_affinity <= 0:
            parser.error("--cpu-affinity requires Windows and a positive mask")
    repo = Path(__file__).resolve().parents[1]
    output = repo / "build" / "reports" / "fe-benchmark" / time.strftime("%Y%m%d-%H%M%S")
    output.mkdir(parents=True, exist_ok=False)
    head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip()
    diff = subprocess.check_output(["git", "diff", "HEAD"], cwd=repo)
    source_hashes = {str(p.relative_to(repo)): hashlib.sha256(p.read_bytes()).hexdigest()
                     for directory in [repo / "src/main/java/com/moakiee/ae2lt/logic/energy",
                                       repo / "src/jdb/java/com/moakiee/ae2lt/logic/energy"]
                     for p in directory.glob("*.java")}
    manifest = {"gitHead": head, "trackedDiffSha256": hashlib.sha256(diff).hexdigest(),
                "sourceSha256": source_hashes, "platform": platform.platform(),
                "cpuAffinity": args.cpu_affinity, "jfr": args.jfr,
                "warmup": args.warmup, "samples": args.samples, "runs": []}
    env = os.environ.copy()
    if args.java_home:
        env["JAVA_HOME"] = str(Path(args.java_home).resolve())
    print(f"Reports: {output}", flush=True)
    for repeat in range(args.repeats):
        # Reverse alternate rounds to reduce a fixed warm-cache ordering bias.
        scenarios = args.scenarios if repeat % 2 == 0 else list(reversed(args.scenarios))
        for scenario in scenarios:
            targets, bind, saturated, queries = SCENARIOS[scenario]
            label = f"{scenario}-{repeat + 1}"
            report = output / f"{label}.json"
            run_dir = output / f"world-{label}"
            command = [str(repo / ("gradlew.bat" if os.name == "nt" else "gradlew")),
                       "-I", "scripts/wireless-energy-test.init.gradle",
                       "-I", "scripts/wireless-energy-benchmark.init.gradle",
                       f"-PwirelessEnergyGameTestDirectory={run_dir.relative_to(repo / 'build').as_posix()}",
                       f"-PfeBenchReport={report}", f"-PfeBenchScenario={label}", f"-PfeBenchGitHead={head}",
                       f"-PfeBenchTargets={targets}", f"-PfeBenchBind={str(bind).lower()}",
                       f"-PfeBenchSaturated={str(saturated).lower()}", f"-PfeBenchQueries={queries}",
                       f"-PfeBenchHighBudget={str(scenario == 'maxbudget8192').lower()}",
                       f"-PfeBenchWarmup={args.warmup}", f"-PfeBenchSamples={args.samples}",
                       f"-PfeBenchAffinity={args.cpu_affinity or 0}",
                       "runGameTestServer", "--offline", "--no-daemon", "--no-configuration-cache", "--max-workers=2"]
            if args.jfr:
                command.insert(1, f"-PfeBenchJfr={output / (label + '.jfr')}")
            print(f"Running {label}", flush=True)
            with (output / f"{label}.log").open("w", encoding="utf-8") as log:
                completed = subprocess.run(command, cwd=repo, env=env, stdout=log, stderr=subprocess.STDOUT, timeout=1200)
            log_text = (output / f"{label}.log").read_text(encoding="utf-8", errors="replace")
            valid = completed.returncode == 0 and report.exists() and "All 1 required tests passed" in log_text
            row = {"scenario": label, "exitCode": completed.returncode, "complete": valid}
            if valid:
                data = json.loads(report.read_text(encoding="utf-8"))
                valid = data.get("conservationAndBudgetsPassed") is True and data.get("sampleTicks") == args.samples
                valid = valid and data.get("affinityMask") == (args.cpu_affinity or 0)
                row.update({"complete": valid, "tickEventMs": data["tickEventMs"],
                            "deliveredFePerTick": data["sampleDeliveredFePerTick"], "gcMillis": data["gcMillis"]})
            if valid and not args.keep_worlds:
                row["worldRecycled"] = recycle(run_dir, output)
            manifest["runs"].append(row)
            (output / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
            print(json.dumps(row), flush=True)
            if not valid:
                print(f"Failed; evidence retained at {output / (label + '.log')}", file=sys.stderr)
                return 1
    summarize(output)
    return 0


if __name__ == "__main__":
    sys.exit(main())
