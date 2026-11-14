"""Assemble the reviewed release source snapshot without touching any Git state."""
from __future__ import annotations

import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import re
import subprocess
import tarfile

ROOT = Path(__file__).resolve().parent
RELEASE = ROOT.parent
PLAN = ROOT / "SOURCE-OVERLAY-PLAN.json"
EXPECTED_PLAN_SHA = "4f597ee46e1a417984a73160526a8a3c1bd40bcc3e5e913d59f40b1a52cb1f0e"
HEAD = "f6536f457e807c5ae64781a12dd6e45e6caa1dd8"


def require(value, message):
    if not value:
        raise RuntimeError(message)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def identity(path):
    data = path.read_bytes()
    return dict(path=str(path), size=len(data), sha256=sha(data))


def copy_checked(source, target, expected=None):
    data = source.read_bytes()
    if expected:
        require(sha(data) == expected["sha256"] and len(data) == expected["size"], "Source changed: " + str(source))
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)
    return identity(source)


def main():
    require(sha(PLAN.read_bytes()) == EXPECTED_PLAN_SHA, "Reviewed source overlay plan changed")
    plan = json.loads(PLAN.read_text(encoding="utf-8"))
    require(plan["head"] == HEAD and not plan["receiptSourceMismatches"] and not plan["deletedAgainstHead"], "Unexpected source plan")
    destination = ROOT / "source-tree"
    require(not destination.exists(), "Never replace an existing assembled source tree")
    repository = Path(plan["repo"])
    archive = subprocess.run(["git", "archive", "--format=tar", HEAD], cwd=repository,
                             check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout
    committed = {}
    modes = {}
    destination.mkdir()
    with tarfile.open(fileobj=io.BytesIO(archive), mode="r:") as source:
        for member in source.getmembers():
            path = PurePosixPath(member.name)
            require(not path.is_absolute() and ".." not in path.parts and ".git" not in path.parts,
                    "Unsafe committed archive path")
            if member.isdir():
                continue
            require(member.isfile(), "Unexpected symlink or special member in committed tree")
            data = source.extractfile(member).read()
            committed[member.name] = data
            modes[member.name] = oct(member.mode)
            target = destination / member.name
            target.parent.mkdir(parents=True, exist_ok=True)
            with target.open("xb") as stream:
                stream.write(data)
    copied = []
    for layer in plan["layers"]:
        require(not layer.get("deletedResources"), "Unreviewed source deletion")
        require(identity(Path(layer["sourceReceipt"]["path"])) == layer["sourceReceipt"], "Layer receipt changed")
        for entry in layer["sourceEntries"]:
            relative = entry["relative"]
            require(relative.startswith("src/main/") and ".." not in PurePosixPath(relative).parts, "Unsafe source overlay")
            original = Path(entry["path"])
            copy_checked(original, destination / relative, entry)
            copied.append(dict(stage=layer["stage"], relative=relative, **identity(original)))
    for entry in plan["finalSources"]:
        target = destination / entry["relative"]
        require(target.is_file() and sha(target.read_bytes()) == entry["sha256"], "Final source differs: " + str(target))
    java = list((destination / "src/main/java").rglob("*.java"))
    resources = [p for p in (destination / "src/main/resources").rglob("*") if p.is_file()]
    require(len(java) == 304 and len(resources) == 137, "Source inventory differs")
    gradle = destination / "gradle.properties"
    before = gradle.read_bytes()
    after, count = re.subn(rb"(?m)^mod_version=[^\r\n]+", b"mod_version=1.21.1-1.6.4", before)
    require(count == 1, "Expected one committed mod_version")
    gradle.write_bytes(after)
    evidence = destination / "verification/release-1.6.4-20261114"
    require(not evidence.exists(), "Release evidence directory already tracked")
    evidence.mkdir()
    production = RELEASE / "production"
    inputs = [
        (PLAN, "SOURCE-OVERLAY-PLAN.json"),
        (ROOT / "SOURCE-PUBLICATION-PLAN.md", "SOURCE-PUBLICATION-PLAN.md"),
        (ROOT / "release-notes.md", "release-notes.md"),
        (production / "INPUTS-b.json", "production-inputs.json"),
        (production / "SOURCE.patch", "production-source.patch"),
        (production / "finalize_candidate_b.py", "finalize_candidate_b.py"),
        (production / "javac-production.args", "javac-production.args"),
        (production / "classpath.txt", "classpath.txt"),
        (production / "staging/useless_stretcher-1.21.1-1.6.4.receipt.json", "production-artifact-receipt.json"),
        (Path(plan["layers"][-2]["root"]) / "verification/E-SOURCE-FREEZE.json", "E-SOURCE-FREEZE.json"),
        (Path(plan["layers"][-2]["root"]) / "verification/native-entry-e1/E1-NATIVE-SMOKE-REPORT.md", "E1-NATIVE-SMOKE-REPORT.md"),
        (Path(__file__), "assemble_source_tree.py"),
    ]
    inputs.extend((Path(layer["sourceReceipt"]["path"]), "layer-%02d-source-receipt.json" % i)
                  for i, layer in enumerate(plan["layers"], 1))
    copied_evidence = []
    for source, name in inputs:
        require(source.is_file() and source.stat().st_size <= 500_000, "Only small source/text evidence is added")
        require(source.suffix.lower() in (".json", ".md", ".txt", ".py", ".patch", ".args"), "No JAR/class/log evidence")
        source.read_bytes().decode("utf-8-sig")
        copied_evidence.append(dict(relative="verification/release-1.6.4-20261114/" + name,
                                    source=copy_checked(source, evidence / name)))
    note = """# 1.6.4 production source provenance

This tree starts from the committed Git tree f6536f457e807c5ae64781a12dd6e45e6caa1dd8,
never the maintenance working directory. The reviewed source overlays contain
304 Java files and 137 main resources. SOURCE-OVERLAY-PLAN.json records every origin
and SHA256. E1 directly overlays sponge1; no additional C/D overlay is required.
The formal overlay changes only two exact-version gates and the mod metadata version.
gradle.properties also declares 1.21.1-1.6.4.

The exact delivered binary uses the frozen inherited production entries and two
root-compiled gate classes; see production-artifact-receipt.json. The production
packager changes exactly two class entries plus META-INF/neoforge.mods.toml and
retains all 786 other E1 entries. Compilation and runtime execution are performed
by the root controller; these preparation scripts never start Java.

The original committed build.gradle and vendor binaries remain unchanged. Its
default libs/compile selection is historical Useless Mod 2.4.6 / AE2 19.2.17,
without an explicit JEI dependency; that default is not a validated clean build
of this release. NativeAdapterGuards needs the newer official base API. The exact
frozen compiler/package plans provide provenance; a clean full Gradle rebuild is
pending an explicit dependency configuration. No newer vendor JAR is bundled here.

All new evidence here is source or small UTF-8 text, not classes, mod JARs or logs.
Old tracked vendor JARs, assets and historical evidence elsewhere in this tree are
preserved from the original commit. Existing working-directory changes, unknown
files, player saves and test instances are never copied.

E1-NATIVE-SMOKE-REPORT.md describes only its original single-room server smoke.
New staff compatibility runtime results must be appended by the root controller;
this snapshot does not claim completion of an in-progress client test. Refer to
the final publication receipt for the actual tested/posted file identity.

No commit, tag, index, branch or remote was changed by assembly. A release tag must
point at an assembled source commit, not at the historical parent commit.
"""
    (evidence / "PROVENANCE.md").write_text(note, encoding="utf-8", newline="\n")
    rows, diff = [], []
    for path in sorted(destination.rglob("*")):
        if not path.is_file():
            continue
        relative = path.relative_to(destination).as_posix()
        data = path.read_bytes()
        state = "added" if relative not in committed else "unchanged" if data == committed[relative] else "changed"
        record = dict(relative=relative, size=len(data), sha256=sha(data), stateAgainstHead=state)
        rows.append(record)
        if state != "unchanged":
            diff.append(record)
    require(not set(committed) - {r["relative"] for r in rows}, "Committed file was deleted")
    new_binary = [r["relative"] for r in diff if r["stateAgainstHead"] == "added" and
                  r["relative"].lower().endswith((".class", ".jar", ".log", ".zip"))]
    require(not new_binary, "New binary or runtime log entered the release tree")
    tree_fingerprint = sha("".join(r["relative"] + "\0" + r["sha256"] + "\n" for r in rows).encode("utf-8"))
    receipt = dict(status="ASSEMBLED_REVIEWED_SOURCE_TREE_NO_GIT_MUTATION_NO_CLEAN_BUILD",
                   sourceTree=str(destination), parentCommit=HEAD, repository=str(repository),
                   sourcePlan=identity(PLAN), gitArchiveSha256=sha(archive), committedFileCount=len(committed),
                   fileCount=len(rows), mainJavaCount=len(java), mainResourceCount=len(resources),
                   treeContentFingerprintSha256=tree_fingerprint, newBinaryOrLogEntries=new_binary,
                   overlayCopies=copied, evidenceCopies=copied_evidence, files=rows,
                   diffAgainstHead=diff, deletedAgainstHead=[], gitStateChanged=False, javaStarted=False,
                   originalCommittedModes=modes,
                   buildGradlePolicy="Preserved committed file unchanged; historical dependency default is not clean-build-certified.")
    receipt_path = ROOT / "ASSEMBLED-RECEIPT.json"
    with receipt_path.open("x", encoding="utf-8", newline="\n") as stream:
        json.dump(receipt, stream, ensure_ascii=False, indent=2)
        stream.write("\n")
    print(json.dumps({k: v for k, v in receipt.items()
                      if k not in ("overlayCopies", "evidenceCopies", "files", "diffAgainstHead", "originalCommittedModes")}, indent=2))
    print(json.dumps(dict(receipt=identity(receipt_path), diffCount=len(diff))))


if __name__ == "__main__":
    main()
