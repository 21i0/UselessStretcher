"""Run source collection checks against byte-identical production JAR entries.

Compile regression sources and capture ModDev's launch first. Only test classes
are added to a diagnostic copy under build/; that copy is never delivered.
Run again with --restart to test the actual saved world and pending journal.
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
parser.add_argument("--restart", action="store_true")
args = parser.parse_args()
run = args.run.resolve()
run.mkdir(parents=True, exist_ok=args.restart)
mods = run / "mods"
mods.mkdir(exist_ok=args.restart)
if args.restart:
    assert (run / "fluid-pending-fixture.nbt").is_file(), "No saved pending-fluid fixture"
    shutil.copy2(run / "logs/latest.log", run / "first-process.log")
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
        for compiled in [compiled_dir / (source.stem + ".class"), *compiled_dir.glob(source.stem + "$*.class")]:
            assert compiled.is_file(), f"Compile regression sources first: {compiled}"
            name = compiled.relative_to(repo / "build/classes/java/main").as_posix()
            assert name not in originals, f"Would replace production entry: {name}"
            output.write(compiled, name)
            added.append(name)
    for resource in (repo / "src/regression/resources").rglob("*"):
        if resource.is_file():
            name = resource.relative_to(repo / "src/regression/resources").as_posix()
            assert name not in originals, name
            output.write(resource, name)
            added.append(name)
with zipfile.ZipFile(args.jar) as original, zipfile.ZipFile(diagnostic) as output:
    assert all(original.read(name) == output.read(name) for name in original.namelist())
audit = {"production_jar": str(args.jar.resolve()),
         "production_sha256": hashlib.sha256(args.jar.read_bytes()).hexdigest(),
         "unchanged_production_entries": len(originals), "test_entries_added": added,
         "external_mods": sorted(p.name for p in mods.glob("*.jar") if p != diagnostic)}
(run / "production-entry-audit.json").write_text(json.dumps(audit, ensure_ascii=False, indent=2), encoding="utf-8")
if not args.restart:
    shutil.copy2(repo / "build/regression-fluid-20261021/server.properties", run / "server.properties")
    (run / "eula.txt").write_text("eula=true\n", encoding="utf-8")
launch = json.loads((repo / "build/fluid-production-launch.json").read_text(encoding="utf-8"))
classpath = [p for p in launch["classpath"] if Path(p).is_file()]
assert all("build/classes" not in p.replace("\\", "/") for p in classpath)
vm_args = repo / "build/moddev/regressionRunVmArgs.txt"
assert "-Duseless_stretcher.fluid_only=true" in vm_args.read_text(encoding="utf-8")
command = [str(args.java), "-Xmx3G", "-Dfile.encoding=UTF-8", "-cp", os.pathsep.join(classpath),
           "@" + str(vm_args), "@" + str(repo / "build/moddev/regressionRunProgramArgs.txt")]
env = {k: v for k, v in os.environ.items() if k not in {"MOD_CLASSES", "FML_MOD_CLASSES"}}
(run / "command.json").write_text(json.dumps(command, indent=2), encoding="utf-8")
console_name = "console-restart.log" if args.restart else "console.log"
with (run / console_name).open("w", encoding="utf-8") as console:
    result = subprocess.run(command, cwd=run, env=env, stdout=console, stderr=subprocess.STDOUT, timeout=300)
print("processExit=" + str(result.returncode), flush=True)
log = (run / "logs/latest.log").read_text(encoding="utf-8")
for line in log.splitlines():
    if "STAFF FLUID" in line:
        print(line, flush=True)
required = ["STAFF FLUID CHECKS PASSED", "STAFF FLUID REAL CELL PASSED", "STAFF FLUID UPSTREAM BINDING PASSED", "STAFF FLUID CROSS DIMENSION PASSED"]
if args.restart:
    required.append("STAFF FLUID REAL RESTART PASSED")
assert result.returncode == 0 and all(marker in log for marker in required), run / console_name
assert "CHECKS FAILED" not in log and "after an integration error" not in log, run / console_name
print("PACKAGED FLUID CHECKS PASSED" + (" WITH REAL RESTART" if args.restart else ""), flush=True)
