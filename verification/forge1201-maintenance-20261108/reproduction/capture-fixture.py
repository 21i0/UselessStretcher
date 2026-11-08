import json, subprocess,hashlib
from pathlib import Path
base=Path(__file__).parent
cache=base/'capture-mods'
cache.mkdir(exist_ok=True)
def get(url):
 return json.loads(subprocess.check_output(['curl.exe','-4','-fLsS','--connect-timeout','10','--max-time','45',url]))
def fetch(version):
 v=get('https://api.modrinth.com/v2/version/'+version)
 for f in v['files']:
  if not f.get('primary'): continue
  dest=cache/f['filename']
  for attempt in range(8):
   if dest.exists() and hashlib.sha1(dest.read_bytes()).hexdigest()==f['hashes']['sha1']: break
   subprocess.run(['curl.exe','-4','-fLsS','--connect-timeout','10','--max-time','45','-C','-','-o',str(dest),f['url']])
  assert hashlib.sha1(dest.read_bytes()).hexdigest()==f['hashes']['sha1'], dest
  print(v['project_id'],v['id'],v['version_number'],dest.name)
 for d in v['dependencies']:
  if d['dependency_type']!='required': continue
  if d.get('version_id'): fetch(d['version_id'])
  elif d.get('project_id'): latest(d['project_id'])
def latest(project):
 vs=get('https://api.modrinth.com/v2/project/'+project+'/version?game_versions=%5B%221.20.1%22%5D&loaders=%5B%22forge%22%5D')
 fetch(vs[0]['id'])
latest('ars-nouveau')
latest('productivebees')
