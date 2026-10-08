#!/usr/bin/env python3
"""Real Android8.1 SAF round trip on emulator, using native DocumentsUI."""
import json
from pathlib import Path
import subprocess
import time
import android_ui_qa as ui

ROOT = Path(__file__).resolve().parents[1]
ui.require_emulator()
ui.OUTPUT.mkdir(parents=True,exist_ok=True)

def labels(): return [n.get('text') for n in ui.tree().iter('node')]

def navigate_backup():
    for attempt in range(4):
        nodes = list(ui.tree().iter('node'))
        backup = next((n for n in nodes if n.get('content-desc')=='Respaldo'),None)
        if backup is not None:
            ui.tap('Respaldo',description=True)
            return
        nav = next(n for n in nodes if n.get('content-desc') in ['Inicio','Clientes','Pedidos','Gastos','Informes'])
        import re
        b = list(map(int,re.findall(r'\d+',nav.get('bounds',''))))
        y = (b[1]+b[3])//2
        ui.adb('shell','input','swipe','660',str(y),'60',str(y),'350')
    raise AssertionError('Respaldo navigation not visible')

def downloads():
    return set(ui.adb('shell','toybox','ls','-1','/sdcard/Download').splitlines())

def export(name):
    before = downloads()
    ui.tap('Exportar respaldo local')
    ui.tap('SAVE')
    deadline = time.monotonic()+30
    while time.monotonic()<deadline:
        if any('exportado y verificado' in (label or '') for label in labels()): break
        time.sleep(.2)
    else: raise AssertionError('Export did not verify')
    added = downloads()-before
    assert len(added)==1, f'Expected one newly-created file, got {added}'
    remote = '/sdcard/Download/'+added.pop()
    local = ui.OUTPUT/(name+'.json')
    ui.adb('pull',remote,str(local))
    return remote,json.loads(local.read_text())

ui.adb('shell','am','force-stop','com.tamalitos.malitos')
ui.adb('shell','am','start','-W','-n','com.tamalitos.malitos/.MainActivity')
navigate_backup()
first_remote,first = export('final-before-restore')
ui.tap('Importar respaldo local')
ui.tap(first_remote.rsplit('/',1)[1])
assert '¿Reemplazar todos los datos?' in labels()
ui.tap('RESTAURAR ARCHIVO')
deadline=time.monotonic()+30
while time.monotonic()<deadline:
    if 'Resumen del negocio' in labels(): break
    time.sleep(.2)
else: raise AssertionError('Restore did not return to the dashboard')
navigate_backup()
status = next((s for s in labels() if s and s.startswith('Restauración completada.')),None)
assert status, 'Restore status was not confirmed'
second_remote,second = export('final-after-restore')
first.pop('exportedAt'); second.pop('exportedAt')
assert first==second, 'Restored data does not exactly match exported business snapshot'
result = {'passed':True,'flow':'Native DocumentsUI export -> confirmed import -> export',
          'exactBusinessSnapshotMatch':True,'android':ui.adb('shell','getprop','ro.build.version.release').strip(),
          'customers':len(first['customers']),'orders':len(first['orders']),'expenses':len(first['expenses']),
          'firstFile':first_remote,'secondFile':second_remote,'restoreStatus':status}
(ROOT/'docs/verification-evidence/native-saf-final.json').write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n')
print(json.dumps(result,indent=2,ensure_ascii=False))
ui.screenshot('respaldo-final-verificado')
