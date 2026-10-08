#!/usr/bin/env python3
"""Real window/rotation QA for Compose. Changes only a disposable emulator."""
import json
from pathlib import Path
import re
import time
import android_ui_qa as ui

ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / 'docs/verification-evidence/compose'
CAPTURES = ROOT / 'docs/capturas/compose'


def texts():
    return [node.get('text', '') for node in ui.tree().iter('node')]


def shot(name):
    path = CAPTURES / (name + '.png')
    path.write_bytes(ui.adb('exec-out', 'screencap', '-p', binary=True))
    return str(path.relative_to(ROOT))


def geometry(size, density):
    ui.adb('shell', 'wm', 'size', size)
    ui.adb('shell', 'wm', 'density', density)


def override(command, label):
    match = re.search('Override ' + label + r': (\S+)', ui.adb('shell', 'wm', command))
    return match.group(1) if match else 'reset'


def main():
    ui.require_emulator()
    CAPTURES.mkdir(parents=True, exist_ok=True)
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    (EVIDENCE / 'windows.json').unlink(missing_ok=True)
    old_size, old_density = override('size', 'size'), override('density', 'density')
    images = []
    try:
        geometry('1600x1000', '160')
        ui.adb('shell', 'am', 'force-stop', 'com.tamalitos.malitos')
        ui.adb('shell', 'am', 'start', '-W', '-n', 'com.tamalitos.malitos/.MainActivity')
        ui.tap('Clientes')
        visible = texts()
        destinations = ['Inicio', 'Clientes', 'Pedidos', 'Gastos', 'Informes', 'Productos', 'Respaldo']
        assert all(destination in visible for destination in destinations), 'Missing wide navigation destination'
        customer = next(text for text in visible if text.startswith('Ver cliente: '))
        ui.tap(customer)
        visible = texts()
        assert 'Directorio de clientes' in visible and 'Editar cliente' in visible, 'Client list/detail not simultaneously visible'
        images.append(shot('tableta-clientes'))
        ui.tap('Pedidos')
        order = next(text for text in texts() if text.startswith('Ver pedido #'))
        ui.tap(order)
        visible = texts()
        assert 'Saldo pendiente' in visible and any(text.startswith('Ver pedido #') for text in visible), 'Order list/detail not simultaneously visible'
        images.append(shot('tableta-pedidos'))
        geometry('720x1280', '320')
        ui.tap('Clientes')
        ui.tap('Agregar cliente')
        ui.tap('Nombre del cliente *')
        ui.adb('shell', 'input', 'text', 'Borrador%sQA%srotacion')
        images.append(shot('telefono-formulario'))
        geometry('1280x720', '320')
        visible = texts()
        images.append(shot('telefono-horizontal-teclado'))
        assert 'Borrador QA rotacion' in visible, 'Unsaved draft lost after actual window resize'
        assert 'Guardar' in visible and 'Volver' in visible, 'Form actions unreachable in short landscape'
        # Unlike Compose's in-view test dispatch, adb input traverses the actual
        # window manager and catches an IME covering the button.
        ui.tap('Guardar')
        deadline = time.monotonic() + 8
        while time.monotonic() < deadline:
            saved = texts()
            # Detail actions are below the fold at this height; verify the saved
            # customer's heading and detail subtitle, not an offscreen button.
            if 'Borrador QA rotacion' in saved and 'Datos de contacto, pedidos y cuenta' in saved:
                break
        else:
            raise AssertionError('Real save tap did not reach the editor action with keyboard open')
        result = {'passed': True, 'emulator': ui.DEVICE, 'widePixels': '1600x1000', 'wideDensity': 160,
                  'phonePixels': '720x1280', 'phoneDensity': 320,
                  'shortLandscapePixels': '1280x720', 'allSevenWideDestinations': True,
                  'clientAndOrderListDetailVisible': True, 'unsavedDraftSurvivesResize': True,
                  'shortLandscapeSaveTappableThroughWindowManager': True, 'screenshots': images,
                  'records': 'Synthetic customer saved on disposable emulator only'}
        (EVIDENCE / 'windows.json').write_text(json.dumps(result, indent=2, ensure_ascii=False) + '\n')
        print(json.dumps(result, indent=2, ensure_ascii=False))
    finally:
        ui.adb('shell', 'input', 'keyevent', '4')
        ui.adb('shell', 'wm', 'size', old_size)
        ui.adb('shell', 'wm', 'density', old_density)


if __name__ == '__main__':
    main()
