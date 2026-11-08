"""Summarize the completed offline bytecode audit. No game or target class loads."""
import csv
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parent
HUD = Path('D:/AI/candidates/useless-stretcher-jei-hud-layout-20261107-a/delivery/useless_stretcher-1.21.1-1.6.4-catalog-P4-assembly-lock-zh-hud.jar')
OLD = Path('D:/AI/sources/DollChronicle/build/mygenesis-longhuan-r6-D/mods/useless_mod-1.21.1-2.4.6.2.jar')
NEW = ROOT / 'useless_mod-1.21.1-2.4.6.5-official.jar'
EAEP = ROOT / 'extendedae_plus-1.6.4.1.jar'

def digest(path, algorithm='sha256'):
    return hashlib.new(algorithm, path.read_bytes()).hexdigest()

def rows(name):
    with (ROOT/'asm-audit'/name).open(encoding='utf-8') as stream:
        return list(csv.DictReader(stream, delimiter='\t'))

assert digest(HUD) == 'e0e15336f1ffa8d1f9ac1839b7a29509bb2d6ecdc03c502ae92b1b4251048fb6'
assert digest(NEW) == '84f957c02dfac63dd3d9f3b269f59cb9704cc228725e9b044ae54c98718bde31'
assert digest(EAEP, 'sha512') == 'ee5819be711c7ce800462fc59f1712ad595d0ac42472919b773dab6e1923bdfe76665ce0e49079bc45a60a3b572c8d7b5edd20238f70f49d2c0ae8ef269dec96'
references = rows('reference-audit.tsv')
focused = rows('focused-injection-audit.tsv')
methods = rows('method-comparison.tsv')
deps = {}
for label, path in [('useless', NEW), ('eaep', EAEP)]:
    with zipfile.ZipFile(path) as archive:
        deps[label] = tomllib.loads(archive.read('META-INF/neoforge.mods.toml').decode('utf-8'))['dependencies']
report = {
    'checkedUtc': datetime.now(timezone.utc).isoformat(),
    'status': 'OFFLINE_NATIVE_ABI_CHECKS_PASSED_MIGRATION_REQUIRED',
    'gameLaunched': False,
    'modClassesLoaded': False,
    'inputs': [{'path':str(p),'size':p.stat().st_size,'sha256':digest(p)} for p in [HUD, OLD, NEW, EAEP]],
    'referenceOccurrences': len(references),
    'uniqueReferences': len({(x['kind'],x['owner'],x['name'],x['descriptor']) for x in references}),
    'unresolvedReferences': [x for x in references if x['status'] != 'RESOLVED'],
    'focusedChecks': focused,
    'focusedPassed': sum(x['status']=='PASS' for x in focused),
    'changedMethods': [x for x in methods if x['status']!='SEMANTICALLY_IDENTICAL'],
    'nativeDependenciesFromDownloadedToml': deps,
    'migrationRequirements': [
        'Do not widen only the native version gate: native 2.4.6.3+ adds AdapterGuards isolation to build, snapshot, synthetic/generated enumeration and per-recipe conversion.',
        'Preserve native guard disabled-action state across incremental slices; preserve per-recipe RuntimeException versus LinkageError behavior.',
        'EAEP 1.6.4.1 declares optional JEI >=19.70.0.460; when JEI is present, old 19.57.0.451 does not satisfy metadata.',
        'Native 2.4.6.5 declares optional DATA >=3.4.0 and EAEP >=1.6.4; existing old versions fail metadata when present.',
    ],
    'pending': [
        'Complete official DATA 3.4.0 bytes and inspect exact constructor/interfaces.',
        'Complete official JEI 19.70.0.460 bytes and scan real candidate references and exact injection points.',
        'Compile and targeted offline checks for the new integrated candidate; runtime checks owned by MyGenesis thread.',
    ],
    'limits': [
        'Static resolution proves owner/name/descriptor and static dispatch compatibility; it does not prove runtime behavior or performance.',
        'Method comparison skips debug tables and stack-map frames; labels and synthetic lambda names remain visible in evidence.',
        'Native old-pattern repair operates on physical omniversal-pattern items and skips the stretcher item; internal obsolete patterns are not thereby proven repaired.',
        'Partial DATA download is retained as .jar.partial and was never opened as a JAR or used as ABI evidence.',
    ],
}
assert not report['unresolvedReferences']
assert report['focusedPassed'] == len(focused) == 15
(ROOT/'audit-summary.json').write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
print(json.dumps({k:report[k] for k in ['status','referenceOccurrences','uniqueReferences','focusedPassed']},ensure_ascii=False))
