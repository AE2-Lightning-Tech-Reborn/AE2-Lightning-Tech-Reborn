"""Preview or apply the known GTLCore 1.2.3.3-fix3 recipe-field migration.

Only rewrites the renamed Java field token in KubeJS scripts. No scripts are
disabled, and every changed file is backed up before an atomic replacement.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import tempfile
from zipfile import ZipFile


FIX3_SHA256 = "6dae8e3f6b9b495b8c7c368f8a6e019d3dec182b06d14ff159d5e18f61b97a51"
OLD_FIELD = b"BLOCK_CONVERSIONRECIPES"
NEW_FIELD = b"BLOCK_CONVERSION_RECIPES"
TOKEN = re.compile(rb"\b" + OLD_FIELD + rb"\b")


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--instance", type=Path, required=True, help="Minecraft game directory containing mods and kubejs")
    parser.add_argument("--apply", action="store_true", help="Apply the previewed migration and retain original files")
    parser.add_argument("--report", type=Path, help="Optional JSON evidence file; must not already exist")
    args = parser.parse_args()
    root = args.instance.resolve(strict=True)
    scripts = (root / "kubejs").resolve(strict=True)
    if not scripts.is_relative_to(root):
        parser.error("kubejs resolves outside the selected instance")
    jars = sorted((root / "mods").glob("gtlcore-*.jar"))
    if len(jars) != 1:
        parser.error("expected exactly one GTLCore jar in the selected instance")
    jar_hash = digest(jars[0].read_bytes())
    if jar_hash != FIX3_SHA256:
        parser.error("unverified GTLCore artifact: this migration only supports the official 1.2.3.3-fix3 SHA256")
    with ZipFile(jars[0]) as jar:
        recipe_type = jar.read("org/gtlcore/gtlcore/common/data/GTLRecipeTypes.class")
        if NEW_FIELD not in recipe_type or OLD_FIELD in recipe_type:
            parser.error("GTLRecipeTypes does not match the verified field rename")
    if args.report and args.report.exists():
        parser.error("refusing to overwrite an existing report")

    changes = []
    for path in sorted(scripts.rglob("*.js")):
        if not path.resolve().is_relative_to(scripts):
            parser.error("script resolves outside the selected kubejs directory: " + str(path))
        before = path.read_bytes()
        after, count = TOKEN.subn(NEW_FIELD, before)
        if not count:
            continue
        backup = root / "ae2lt-compat-backups" / "gtlcore-1.2.3.3-fix3" / path.relative_to(scripts)
        if not backup.resolve().is_relative_to(root):
            parser.error("backup resolves outside the selected instance")
        if backup.exists() and backup.read_bytes() != before:
            parser.error("existing backup differs; preserve and review it before applying again: " + str(backup))
        changes.append((path, backup, before, after, count))

    report = {"gtlcore": jars[0].name, "gtlcoreSHA256": jar_hash, "applied": args.apply, "files": []}
    for path, backup, before, after, count in changes:
        if args.apply:
            backup.parent.mkdir(parents=True, exist_ok=True)
            if not backup.exists():
                with backup.open("xb") as output:
                    output.write(before)
            if path.read_bytes() != before:
                raise RuntimeError("script changed after the preview: " + str(path))
            descriptor, temporary = tempfile.mkstemp(prefix=path.name + ".", suffix=".tmp", dir=path.parent)
            try:
                with os.fdopen(descriptor, "wb") as output:
                    output.write(after)
                os.replace(temporary, path)
            finally:
                if os.path.exists(temporary):
                    os.unlink(temporary)
        report["files"].append({"script": path.relative_to(root).as_posix(), "replacements": count,
                                "beforeSHA256": digest(before), "afterSHA256": digest(after),
                                "backup": backup.relative_to(root).as_posix()})
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        with args.report.open("x", encoding="utf-8") as output:
            json.dump(report, output, ensure_ascii=False, indent=2)
            output.write("\n")
    print(json.dumps(report, ensure_ascii=True, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
