"""Run regression checks against unchanged entries from a supplied production JAR.

The diagnostic copy adds only compiled regression sources/resources. It is kept
under build/, never delivered. No production entry may be replaced.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

repo = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser()
parser.add_argument("--jar", type=Path, required=True)
parser.add_argument("--run", type=Path, required=True)
parser.add_argument("--fixture", type=Path, required=True)
parser.add_argument("--java", type=Path, required=True)
args = parser.parse_args()
run = args.run.resolve()
run.mkdir(parents=True, exist_ok=False)
mods = run / "mods"
mods.mkdir()
for mod in args.fixture.glob("*.jar"):
    assert not mod.name.startswith("useless_stretcher-"), mod
    shutil.copy2(mod, mods / mod.name)
diagnostic = mods / "useless_stretcher-packaged-diagnostic.jar"
added = []
with zipfile.ZipFile(args.jar) as original, zipfile.ZipFile(diagnostic, "w", zipfile.ZIP_DEFLATED) as output:
    originals = set(original.namelist())
    for entry in original.infolist():
        output.writestr(entry, original.read(entry))
    for source in (repo / "src/regression/java").rglob("*.java"):
        relative = source.relative_to(repo / "src/regression/java")
        compiled_dir = repo / "build/classes/java/main" / relative.parent
        compiled_classes = [compiled_dir / (source.stem + ".class"), *compiled_dir.glob(source.stem + "$*.class")]
        for compiled in compiled_classes:
            assert compiled.is_file(), f"Compile regression sources first: {compiled}"
            name = compiled.relative_to(repo / "build/classes/java/main").as_posix()
            assert name not in originals, f"Test would replace production entry: {name}"
            output.write(compiled, name)
            added.append(name)
    for resource in (repo / "src/regression/resources").rglob("*"):
        if not resource.is_file():
            continue
        name = resource.relative_to(repo / "src/regression/resources").as_posix()
        assert name not in originals, f"Test would replace production resource: {name}"
        output.write(resource, name)
        added.append(name)
with zipfile.ZipFile(args.jar) as original, zipfile.ZipFile(diagnostic) as output:
    assert all(original.read(name) == output.read(name) for name in original.namelist())
audit = {"production_jar": str(args.jar.resolve()),
         "production_sha256": hashlib.sha256(args.jar.read_bytes()).hexdigest(),
         "unchanged_production_entries": len(originals), "test_entries_added": added,
         "external_mods": sorted(p.name for p in mods.glob("*.jar") if p != diagnostic)}
(run / "production-entry-audit.json").write_text(json.dumps(audit, ensure_ascii=False, indent=2), encoding="utf-8")
shutil.copy2(repo / "build/regression-occultism-12244-live-baseline-20261019/server.properties", run / "server.properties")
(run / "eula.txt").write_text("eula=true\n", encoding="utf-8")
launch = json.loads((repo / "build/occultism-production-launch.json").read_text(encoding="utf-8"))
classpath = [p for p in launch["classpath"] if Path(p).is_file()]
assert all("build/classes" not in p.replace("\\", "/") for p in classpath)
command = [str(args.java), "-Xmx3G", "-Dfile.encoding=UTF-8", "-cp", os.pathsep.join(classpath),
           "@" + str(repo / "build/moddev/regressionRunVmArgs.txt"),
           "@" + str(repo / "build/moddev/regressionRunProgramArgs.txt")]
env = {k: v for k, v in os.environ.items() if k not in {"MOD_CLASSES", "FML_MOD_CLASSES"}}
(run / "command.json").write_text(json.dumps(command, indent=2), encoding="utf-8")
with (run / "console.log").open("w", encoding="utf-8") as console:
    result = subprocess.run(command, cwd=run, env=env, stdout=console, stderr=subprocess.STDOUT, timeout=300)
print("processExit=" + str(result.returncode), flush=True)
log = (run / "logs/latest.log").read_text(encoding="utf-8") if (run / "logs/latest.log").exists() else ""
for line in log.splitlines():
    if any(marker in line for marker in ("OCCULTISM LIVE", "OCCULTISM WAITING", "OCCULTISM COMPLETED", "OCCULTISM RESTART", "OCCULTISM DELAYED", "EXTERNAL ACCELERATION CHECKS")):
        print(line, flush=True)
assert result.returncode == 0, (run / "console.log")
assert "EXTERNAL ACCELERATION CHECKS PASSED" in log and "OCCULTISM LIVE RITUAL CHECKS PASSED" in log, (run / "console.log")
assert "CHECKS FAILED" not in log, (run / "console.log")
assert "OCCULTISM COMPLETED IDLE CHECKS PASSED" in log and "OCCULTISM RESTART RELOAD CHECKS PASSED" in log, (run / "console.log")
