"""Offline constant-pool type audit; target JARs are inspected, never loaded."""
import hashlib
import json
from pathlib import Path
import re
import struct
import sys
import zipfile

ROOT = Path(__file__).resolve().parent
HUD = Path('D:/AI/candidates/useless-stretcher-jei-hud-layout-20261107-a/delivery/useless_stretcher-1.21.1-1.6.4-catalog-P4-assembly-lock-zh-hud.jar')
EXPECTED_HUD = 'e0e15336f1ffa8d1f9ac1839b7a29509bb2d6ecdc03c502ae92b1b4251048fb6'
OUTPUT = ROOT/'jei-data-type-audit.json'
if len(sys.argv) > 1:
    HUD, EXPECTED_HUD, OUTPUT = Path(sys.argv[1]), sys.argv[2].lower(), Path(sys.argv[3])
    assert OUTPUT.resolve().is_relative_to(ROOT.resolve())
JEI = Path('D:/AI/sources/DollChronicle/verification/mod-updates-20261107/staging/jei-1.21.1-neoforge-19.70.0.460.jar')
DATA = JEI.with_name('data_energistics-1.21.1-3.4.0.jar')
assert hashlib.sha256(HUD.read_bytes()).hexdigest() == EXPECTED_HUD
assert hashlib.sha256(JEI.read_bytes()).hexdigest() == '097cdfae99b208dfb94f8c55e813af10e55688ecf4e42116fed1b7e26f0f4fcf'
assert hashlib.sha256(DATA.read_bytes()).hexdigest() == '5fc69019f1068c9c333efaebf0d7f731dd663c38245a104ad597d91003299367'

def strings(blob):
    assert blob[:4] == b'\xca\xfe\xba\xbe'
    count = struct.unpack_from('>H', blob, 8)[0]
    pos, index = 10, 1
    while index < count:
        tag = blob[pos]
        pos += 1
        if tag == 1:
            length = struct.unpack_from('>H', blob, pos)[0]
            pos += 2
            yield blob[pos:pos+length].decode('utf-8', 'replace')
            pos += length
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            pos += 4
        elif tag in (5, 6):
            pos += 8
            index += 1
        elif tag in (7, 8, 16, 19, 20):
            pos += 2
        elif tag == 15:
            pos += 3
        else:
            raise ValueError(f'Unknown constant-pool tag {tag}')
        index += 1

out = {}
with zipfile.ZipFile(HUD) as candidate:
    candidates = {n: list(strings(candidate.read(n))) for n in candidate.namelist() if n.endswith('.class')}
    for label, prefix, target in [('jei', 'mezz/jei/', JEI), ('data', 'com/fish_dan_/data_energistics/', DATA)]:
        found = {}
        slash_pattern = re.compile(re.escape(prefix)+r'[A-Za-z0-9_$/]+')
        dot_pattern = re.compile(re.escape(prefix.replace('/', '.'))+r'[A-Za-z0-9_$.]+')
        for owner, texts in candidates.items():
            for text in texts:
                types = set(slash_pattern.findall(text)) | {x.replace('.', '/') for x in dot_pattern.findall(text)}
                for name in types:
                    if not name.endswith('/'):
                        found.setdefault(name, set()).add(owner)
        with zipfile.ZipFile(target) as archive:
            present = set(archive.namelist())
        out[label] = {
            'target': str(target), 'uniqueReferencedClasses':len(found),
            'classes': [{'name':name, 'callers':sorted(callers), 'present':name+'.class' in present} for name,callers in sorted(found.items())],
            'missing': sorted(name for name in found if name+'.class' not in present),
        }
report = {'gameLaunched':False,'modClassesLoaded':False,'candidate':str(HUD),'results':out,
          'scope':'All class-file UTF8 constants searched for slash/dot type names, including member descriptors, signatures, Mixin target strings and class literals.'}
OUTPUT.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps({k:{'uniqueReferencedClasses':v['uniqueReferencedClasses'],'missing':v['missing']} for k,v in out.items()}))
