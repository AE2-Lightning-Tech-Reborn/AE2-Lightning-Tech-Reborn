"""Make a local, attributed Useless 20.2.4.7 compatibility copy without changing upstream files.

Run only with LT's GTO counter-entry adapter. The optional NeoECO range is the only
upstream compatibility declaration changed; no class bytecode or mixin is removed.
"""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import re
import tomllib
import zipfile

ORIGINAL_SHA256 = "8fdae841d6736b81e1d30b23811b470cf6014f94c8168cbab239b10841a80b55"
VERSION = "20.2.4.7-gto.1"


def prepare(source: Path, destination: Path) -> dict:
    if source.resolve() == destination.resolve():
        raise ValueError("Output must be separate from the original release")
    digest = hashlib.sha256(source.read_bytes()).hexdigest()
    if digest != ORIGINAL_SHA256:
        raise ValueError(f"Expected upstream Useless 20.2.4.7, got SHA256 {digest}")
    record = {
        "upstream": "https://github.com/SorrowMist/UselessMod/releases/tag/20.2.4.7",
        "upstreamSHA256": digest,
        "compatibilityVersion": VERSION,
        "changes": ["Declare exact NeoECO 20.5.2-gto.1 compatibility alongside the original range"],
        "requires": "AE2LT GTO counter-entry adapter (extendedae_plus/useless_mod present)",
        "classBytesChanged": False,
        "license": "Original MIT license and attribution retained",
    }
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_name(destination.name + ".part")
    if source.resolve() == temporary.resolve():
        raise ValueError("Temporary output must be separate from the original release")
    with zipfile.ZipFile(source) as original:
        if original.testzip() is not None or len(original.namelist()) != len(set(original.namelist())):
            raise ValueError("Invalid upstream archive")
        if any(re.fullmatch(r"META-INF/[^/]+\.(SF|RSA|DSA|EC)", name, re.I)
               for name in original.namelist()):
            raise ValueError("Refusing to invalidate a signed release")
        metadata = original.read("META-INF/mods.toml").decode("utf8")
        old_range = 'versionRange="[20.4.2,20.5)"'
        if metadata.count(old_range) != 1 or metadata.count('version="20.2.4.7"') != 1:
            raise ValueError("Unexpected upstream dependency or version metadata")
        metadata = metadata.replace(old_range, 'versionRange="[20.4.2,20.5),[20.5.2-gto.1]"')
        metadata = metadata.replace('version="20.2.4.7"', f'version="{VERSION}"')
        parsed = tomllib.loads(metadata)
        dependency = next(x for x in parsed["dependencies"]["useless_mod"] if x["modId"] == "neoecoae")
        assert not dependency["mandatory"]
        with zipfile.ZipFile(temporary, "w", compression=zipfile.ZIP_DEFLATED) as patched:
            for entry in original.infolist():
                data = original.read(entry)
                if entry.filename == "META-INF/mods.toml":
                    data = metadata.encode("utf8")
                patched.writestr(copy.copy(entry), data)
            attribution = zipfile.ZipInfo("META-INF/ae2lt-gto-compat.json", (1980, 1, 1, 0, 0, 0))
            attribution.compress_type = zipfile.ZIP_DEFLATED
            attribution.create_system = 3
            attribution.external_attr = 0o100644 << 16
            patched.writestr(attribution, json.dumps(record, indent=2))
        with zipfile.ZipFile(temporary) as patched:
            assert patched.testzip() is None
            for name in original.namelist():
                if name != "META-INF/mods.toml":
                    assert original.read(name) == patched.read(name), name
    temporary.replace(destination)
    record["outputSHA256"] = hashlib.sha256(destination.read_bytes()).hexdigest()
    return record


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    print(json.dumps(prepare(args.source, args.output), indent=2))
