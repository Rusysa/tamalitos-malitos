#!/usr/bin/env python3
"""Collect genuine build/test evidence; never turn a partial run into success."""
import hashlib
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / 'docs/verification-evidence'

def collect(instrumentation=None):
    files = sorted((ROOT/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'))
    if not files: raise SystemExit('Missing JVM JUnit XML results')
    unit = {'tests':0, 'failures':0, 'errors':0, 'skipped':0}
    suites = []
    for file in files:
        root = ET.parse(file).getroot()
        counts = {key:int(root.get(key,'0')) for key in unit}
        for key,value in counts.items(): unit[key] += value
        suites.append({'name':root.get('name'), **counts})
    if unit['failures'] or unit['errors'] or unit['skipped']:
        raise SystemExit('Incomplete JVM suite: '+json.dumps(unit))
    lintfile = ROOT/'app/build/reports/lint-results-debug.xml'
    if not lintfile.exists(): raise SystemExit('Missing lint results')
    issues = ET.parse(lintfile).getroot().findall('issue')
    lint = {key:sum(n.get('severity')==key for n in issues) for key in ['Error','Fatal','Warning','Information']}
    if lint['Error'] or lint['Fatal']: raise SystemExit('Lint errors: '+json.dumps(lint))
    native = None
    if instrumentation:
        text = Path(instrumentation).read_text()
        match = re.search(r'OK \((\d+) tests?\)',text)
        if not match or 'FAILURES!!!' in text or 'INSTRUMENTATION_FAILED' in text or 'INSTRUMENTATION_STATUS_CODE: -2' in text:
            raise SystemExit('Android instrumentation did not pass')
        count = int(match.group(1))
        completed = re.findall(r'INSTRUMENTATION_STATUS_CODE: 0\s*',text)
        if len(completed)!=count: raise SystemExit('Android test count mismatch')
        native = {'tests':count,'failures':0,'log':str(Path(instrumentation).resolve())}
    word = ROOT/'Tamalitos_Malitos_Proyecto.docx'
    original = 'f6573f20095bc58f485ad26ffaf09d73e0bf5ef0afdf02ba3d5ec5b04f1e70d9'
    word_hash = hashlib.sha256(word.read_bytes()).hexdigest()
    if word_hash != original: raise SystemExit('Original Word was modified')
    apk = ROOT/'app/build/outputs/apk/debug/app-debug.apk'
    if not apk.exists(): raise SystemExit('No real APK was built')
    result = {'jvm':unit,'suites':suites,'lint':lint,'androidInstrumented':native,
              'apk':{'path':str(apk),'bytes':apk.stat().st_size,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest()},
              'wordUnmodified':True,'wordSha256':word_hash,
              'googleDriveLiveTested':False,'googleDrivePending':'Google Cloud and authorized account setup'}
    EVIDENCE.mkdir(parents=True,exist_ok=True)
    (EVIDENCE/'summary.json').write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n')
    print(json.dumps(result,indent=2,ensure_ascii=False))

if __name__ == '__main__': collect(sys.argv[1] if len(sys.argv)>1 else None)
