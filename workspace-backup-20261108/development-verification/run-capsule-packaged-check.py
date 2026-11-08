"""Append opt-in checks without changing any production entry, then run the real game."""
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
parser.add_argument('--jar', type=Path, required=True)
parser.add_argument('--run', type=Path, required=True)
parser.add_argument('--fixture', type=Path, required=True)
parser.add_argument('--mode', choices=['client', 'server'], required=True)
parser.add_argument('--expect-failure', action='store_true')
parser.add_argument('--restart', action='store_true')
parser.add_argument('--world-from', type=Path)
parser.add_argument('--variants', action='store_true', help='Run the native structure variant save/restart suite (server only).')
parser.add_argument('--stage', type=int, choices=[1, 2, 3], help='Variant suite process number; defaults to 1 with --variants.')
args = parser.parse_args()
if args.variants:
    if args.mode != 'server':
        parser.error('--variants requires --mode server')
    args.stage = args.stage or 1
    if args.stage > 1 and not args.restart:
        parser.error('--variants --stage 2/3 requires --restart and the same --run directory')
elif args.stage is not None:
    parser.error('--stage requires --variants')


def evidence_path(name):
    """A retry must keep every previous variant-stage log/audit intact."""
    path = run / name
    if not args.variants:
        return path
    path = path.with_name(path.stem + f'-variants-stage{args.stage}' + path.suffix)
    attempt = 2
    candidate = path
    while candidate.exists():
        candidate = path.with_name(path.stem + f'-attempt{attempt}' + path.suffix)
        attempt += 1
    return candidate


run = args.run.resolve()
run.mkdir(parents=True, exist_ok=args.restart)
mods = run / 'mods'
mods.mkdir(exist_ok=args.restart)
if args.world_from:
    assert args.mode == 'server' and not args.restart
    shutil.copytree(args.world_from, run / 'world')
if args.restart:
    shutil.copy2(run / 'logs/latest.log', evidence_path('previous-process.log'))
for mod in args.fixture.glob('*.jar'):
    assert not mod.name.startswith('useless_stretcher-'), mod
    shutil.copy2(mod, mods / mod.name)
diagnostic = mods / 'useless_stretcher-capsule-diagnostic.jar'
source_set = 'storyTest' if args.mode == 'client' else 'regression'
added = []
with zipfile.ZipFile(args.jar) as original, zipfile.ZipFile(diagnostic, 'w', zipfile.ZIP_DEFLATED) as output:
    names = set(original.namelist())
    for entry in original.infolist():
        output.writestr(entry, original.read(entry))
    for source in (repo / f'src/{source_set}/java').rglob('*.java'):
        relative = source.relative_to(repo / f'src/{source_set}/java')
        compiled_dir = repo / 'build/classes/java/main' / relative.parent
        for compiled in [compiled_dir / (source.stem + '.class'), *compiled_dir.glob(source.stem + '$*.class')]:
            assert compiled.is_file(), f'Compile {source_set} sources first: {compiled}'
            name = compiled.relative_to(repo / 'build/classes/java/main').as_posix()
            assert name not in names, f'Would overwrite production entry: {name}'
            output.write(compiled, name)
            added.append(name)
with zipfile.ZipFile(args.jar) as original, zipfile.ZipFile(diagnostic) as output:
    assert all(original.read(name) == output.read(name) for name in names)
audit = {'production_jar': str(args.jar.resolve()),
         'production_sha256': hashlib.sha256(args.jar.read_bytes()).hexdigest(),
         'unchanged_production_entries': len(names), 'test_entries_added': added,
         'external_mods': sorted(mod.name for mod in args.fixture.glob('*.jar'))}
if args.variants:
    audit.update({'suite': 'capsule_variants', 'stage': args.stage})
evidence_path('production-entry-audit.json').write_text(json.dumps(audit, ensure_ascii=False, indent=2), encoding='utf-8')
if args.mode == 'server' and not args.restart:
    properties = repo / ('build/regression-variants-release-20261103/server.properties' if args.variants
                         else 'build/regression-installed-stretcher-20261023/server.properties')
    assert properties.is_file(), f'Missing isolated server configuration: {properties}'
    shutil.copy2(properties, run / 'server.properties')
    (run / 'eula.txt').write_text('eula=true\n', encoding='utf-8')
launch = json.loads((repo / f'build/capsule-{args.mode}-launch.json').read_text(encoding='utf-8'))
classpath = [entry for entry in launch['classpath'] if Path(entry).is_file()]
assert all('build/classes' not in entry.replace('\\', '/') for entry in classpath)
prefix = 'storyPreview' if args.mode == 'client' else 'regression'
vm_args = repo / f'build/moddev/{prefix}RunVmArgs.txt'
vm_options = {line.strip() for line in vm_args.read_text(encoding='utf-8').splitlines()}
if args.variants:
    assert '-Duseless_stretcher.capsule_variants_only=true' in vm_options, vm_args
    assert '-Duseless_stretcher.regression=true' in vm_options, vm_args
    assert '-Duseless_stretcher.capsule_repair_only=true' not in vm_options, vm_args
else:
    assert '-Duseless_stretcher.capsule_repair_only=true' in vm_options, vm_args
java = Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot/bin/java.exe')
command = [str(java), '-Dfile.encoding=UTF-8', '-cp', os.pathsep.join(classpath),
           '@' + str(vm_args), '@' + str(repo / f'build/moddev/{prefix}RunProgramArgs.txt')]
env = {key: value for key, value in os.environ.items() if key not in {'MOD_CLASSES', 'FML_MOD_CLASSES'}}
evidence_path('command.json').write_text(json.dumps(command, indent=2), encoding='utf-8')
console_path = evidence_path('console.log' if args.variants or not args.restart else 'console-restart.log')
try:
    with console_path.open('w', encoding='utf-8') as console:
        result = subprocess.run(command, cwd=run, env=env, stdout=console, stderr=subprocess.STDOUT, timeout=1200)
finally:
    if args.variants and (run / 'logs/latest.log').is_file():
        shutil.copy2(run / 'logs/latest.log', evidence_path('latest.log'))
log = (run / 'logs/latest.log').read_text(encoding='utf-8')
for line in log.splitlines():
    if any(marker in line for marker in ['CAPSULE MAP', 'CAPSULE METEORITE', '2I NOSE', 'CAPSULE VARIANT']):
        print(line, flush=True)
assert result.returncode == 0, console_path
if args.variants:
    required = ('CAPSULE VARIANTS CHECKS FAILED' if args.expect_failure else {
        1: 'CAPSULE VARIANTS RESTART 1 REQUIRED',
        2: 'CAPSULE VARIANTS RESTART 2 REQUIRED',
        3: 'CAPSULE VARIANTS CHECKS PASSED',
    }[args.stage])
else:
    required = ('CAPSULE MAP RENDER CHECKS' if args.mode == 'client' else 'CAPSULE METEORITE CHECKS')
    required += ' FAILED' if args.expect_failure else ' PASSED'
assert required in log, run / 'logs/latest.log'
if not args.expect_failure:
    assert 'CHECKS FAILED' not in log, run / 'logs/latest.log'
label = f'PACKAGED CAPSULE VARIANTS STAGE {args.stage}' if args.variants else 'PACKAGED CAPSULE'
print(label + (' EXPECTED FAILURE REPRODUCED' if args.expect_failure else ' CHECKS PASSED'), flush=True)
