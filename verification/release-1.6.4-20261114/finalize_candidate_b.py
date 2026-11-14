"""Package two freshly compiled exact-version gates over immutable E1. Never starts Java."""
from __future__ import annotations

import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import struct
import zipfile

ROOT = Path(__file__).resolve().parent
CLASSES = {
    "com/sorrowmist/useless/stretcher/mixin/UselessStretcherMixinPlugin.class",
    "com/sorrowmist/useless/stretcher/content/catalog/CatalogPatchSupport.class",
}
METADATA = "META-INF/neoforge.mods.toml"
OLD_VERSION = b"1.21.1-1.6.4-catalog-P4-assembly-lock-zh-hud-base2465-jei1970-sponge1-capsule-gravity-e1"
NEW_VERSION = b"1.21.1-1.6.4"
BASES = {"1.21.1-2.4.6", "1.21.1-2.4.6.2", "1.21.1-2.4.6.5", "1.21.1-2.4.6.6"}
JEIS = {"19.57.0.451", "19.70.0.460", "19.70.0.469"}


def require(ok, message):
    if not ok:
        raise RuntimeError(message)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def identity(path):
    data = path.read_bytes()
    return {"path": str(path), "size": len(data), "sha256": sha(data)}


def class_info(data):
    """Read class identity and every externally visible member without a JVM."""
    offset = 0

    def take(n):
        nonlocal offset
        value = data[offset:offset + n]
        require(len(value) == n, "Truncated class")
        offset += n
        return value

    def u1():
        return take(1)[0]

    def u2():
        return struct.unpack(">H", take(2))[0]

    def u4():
        return struct.unpack(">I", take(4))[0]

    require(u4() == 0xCAFEBABE, "Not a class file")
    minor, major = u2(), u2()
    count = u2()
    pool = [None] * count
    i = 1
    while i < count:
        tag = u1()
        if tag == 1:
            pool[i] = take(u2()).decode("utf-8", errors="replace")
        elif tag in (3, 4):
            take(4)
        elif tag in (5, 6):
            take(8)
            i += 1
        elif tag in (7, 8, 16, 19, 20):
            pool[i] = (tag, u2())
        elif tag in (9, 10, 11, 12, 17, 18):
            pool[i] = (tag, u2(), u2())
        elif tag == 15:
            take(3)
        else:
            raise RuntimeError("Unknown class constant tag " + str(tag))
        i += 1

    def class_name(index):
        return pool[pool[index][1]] if index else None

    access, this_index, super_index = u2(), u2(), u2()
    interfaces = [class_name(u2()) for _ in range(u2())]

    def skip_attributes():
        for _ in range(u2()):
            u2()
            take(u4())

    def members():
        result = []
        for _ in range(u2()):
            flags, name, descriptor = u2(), pool[u2()], pool[u2()]
            if not flags & 0x0002:  # Private implementation is not external ABI.
                result.append([name, descriptor, flags])
            skip_attributes()
        return sorted(result)

    fields, methods = members(), members()
    skip_attributes()
    require(offset == len(data), "Trailing or unparsed class bytes")
    return {"name": class_name(this_index), "super": class_name(super_index),
            "access": access, "interfaces": interfaces, "minor": minor, "major": major,
            "fields": fields, "methods": methods,
            "strings": sorted(value for value in pool if isinstance(value, str))}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--classes", type=Path, required=True,
                        help="Root-owned successful fresh javac output directory; exactly two classes")
    parser.add_argument("--compile-log", type=Path, required=True,
                        help="Preserved actual root javac output log, including an empty successful log")
    args = parser.parse_args()
    manifest_path = ROOT / "INPUTS-b.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    require(manifest["status"] == "PREPARED_NOT_COMPILED_NOT_TESTED", "Unexpected input plan")
    for entry in manifest["inputs"]:
        path = Path(entry["path"])
        require(path.is_file() and identity(path) == entry, "Frozen input differs: " + str(path))
    baseline = Path(manifest["baseline"]["path"])
    require(identity(baseline) == manifest["baseline"], "E1 baseline changed")
    classes_root = args.classes.resolve(strict=True)
    require(classes_root.is_relative_to(ROOT.parent), "Classes must be in this release work root")
    paths = {p.relative_to(classes_root).as_posix(): p for p in classes_root.rglob("*") if p.is_file()}
    require(set(paths) == CLASSES, "Exactly two production classes required; no inner/helper/stale files")
    compile_log = args.compile_log.resolve(strict=True)
    require(compile_log.is_file(), "Actual compiler log required")
    overlay = {name: path.read_bytes() for name, path in paths.items()}
    overlay[METADATA] = (ROOT / "src/main/resources/META-INF/neoforge.mods.toml").read_bytes()
    out_dir = ROOT / "staging"
    out_dir.mkdir(exist_ok=True)
    output = out_dir / "useless_stretcher-1.21.1-1.6.4.jar"
    receipt_path = output.with_suffix(".receipt.json")
    require(not output.exists() and not receipt_path.exists(), "Never replace an existing candidate/receipt")
    temporary = out_dir / "useless_stretcher-1.21.1-1.6.4.jar.b.part"
    require(not temporary.exists(), "Previous partial output remains; inspect it before another attempt")
    with zipfile.ZipFile(baseline) as old:
        names = old.namelist()
        require(len(names) == 789 and len(set(names)) == len(names), "Unexpected E1 entry set")
        require(set(overlay).issubset(names), "Overlay would add a new production entry")
        original_metadata = old.read(METADATA)
        require(original_metadata.count(OLD_VERSION) == 1, "Unexpected original version count")
        require(overlay[METADATA] == original_metadata.replace(OLD_VERSION, NEW_VERSION),
                "Metadata must change only the display version; dependency minimum remains unchanged")
        abi = []
        for name in sorted(CLASSES):
            before, after = class_info(old.read(name)), class_info(overlay[name])
            require(after["major"] == 65 and after["minor"] == 0, "Expected Java 21 class")
            require(after["name"] + ".class" == name, "Class entry/name mismatch")
            for key in ("name", "super", "access", "interfaces", "minor", "major", "fields", "methods"):
                require(before[key] == after[key], "External class ABI changed: " + name + " " + key)
            require(BASES.issubset(after["strings"]) and JEIS.issubset(after["strings"]),
                    "One or more exact old/new whitelist values missing: " + name)
            require(old.read(name) != overlay[name], "Fresh gate class did not change: " + name)
            abi.append({"entry": name, "externalAbiUnchanged": True,
                        "beforeSha256": sha(old.read(name)), "afterSha256": sha(overlay[name]),
                        "beforeBytes": len(old.read(name)), "afterBytes": len(overlay[name])})
        forbidden = ("/regression/", "/validation/", "Checks.class", "EDataLauncher", "RecoveryDataLauncher",
                     "MemoryPreferences", "capsule_gravity_n1_fixture")
        require(not any(token in name for name in names for token in forbidden), "Test/fixture leaked")
        with temporary.open("xb") as stream:
            with zipfile.ZipFile(stream, "w") as new:
                new.comment = old.comment
                for info in old.infolist():
                    new.writestr(copy.copy(info), overlay.get(info.filename, old.read(info.filename)))
            stream.flush()
            os.fsync(stream.fileno())
        rows = []
        with zipfile.ZipFile(temporary) as new:
            require(new.namelist() == names and new.testzip() is None, "ZIP entry set or CRC differs")
            for name in names:
                before, after = old.read(name), new.read(name)
                require(after == overlay.get(name, before), "Unexpected packaged entry: " + name)
                rows.append({"entry": name, "changed": after != before, "size": len(after), "sha256": sha(after)})
        require({r["entry"] for r in rows if r["changed"]} == CLASSES | {METADATA},
                "Change whitelist differs")
    require(identity(baseline) == manifest["baseline"], "E1 baseline changed during packaging")
    for entry in manifest["inputs"]:
        require(identity(Path(entry["path"])) == entry, "Input changed during packaging")
    receipt = {"status": "PRODUCTION_CANDIDATE_PACKAGED_AWAITING_ROOT_TESTS",
               "artifact": str(output), "sha256": sha(temporary.read_bytes()), "size": temporary.stat().st_size,
               "version": NEW_VERSION.decode(), "baseline": manifest["baseline"],
               "inputManifest": identity(manifest_path), "compileLog": identity(compile_log),
               "compiledClasses": [identity(paths[name]) for name in sorted(CLASSES)],
               "entryCount": len(rows), "changedEntries": 3, "unchangedEntries": len(rows) - 3,
               "javaStartedByFinalizer": False, "testsRunByFinalizer": False,
               "fixtureExcluded": True, "metadataDependencyMinimumUnchanged": True,
               "abiChecks": abi, "entries": rows}
    os.rename(temporary, output)
    with receipt_path.open("x", encoding="utf-8", newline="\n") as stream:
        json.dump(receipt, stream, ensure_ascii=False, indent=2)
        stream.write("\n")
    print(json.dumps({k: v for k, v in receipt.items() if k not in ("entries", "abiChecks")}, ensure_ascii=False))
    print(json.dumps({"receipt": str(receipt_path), "receiptSha256": sha(receipt_path.read_bytes())}))


if __name__ == "__main__":
    main()
