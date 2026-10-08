#!/usr/bin/env python3
"""Package source/documents only; exclude credentials and machine/build caches."""
import hashlib
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'entregables/Tamalitos-Malitos-codigo.zip'
files = {ROOT / name for name in [
    '.gitignore', 'CONTRACT.md', 'README.md', 'Tamalitos_Malitos_Proyecto.docx',
    'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties', 'gradlew',
    'gradlew.bat', 'app/build.gradle.kts',
]}
for directory in ['app/src', 'gradle', 'docs']:
    files.update(path for path in (ROOT / directory).rglob('*') if path.is_file())
files.update(path for path in (ROOT / 'tools').iterdir()
             if path.is_file() and path.suffix in ['.py', '.sh'] and path.name != 'env.sh')
for path in files:
    relative = path.relative_to(ROOT)
    assert path.is_file() and not path.is_symlink(), relative
    assert not any(part in ['.git', '.gradle', '.kotlin', '__pycache__', 'build'] for part in relative.parts), relative
    assert path.suffix not in ['.jks', '.keystore', '.pem', '.key', '.db', '.sqlite'], relative
    assert not path.name.startswith('.env') and path.name not in ['local.properties', 'env.sh'], relative
OUT.parent.mkdir(exist_ok=True)
with zipfile.ZipFile(OUT, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
    for path in sorted(files):
        archive.write(path, arcname=str(path.relative_to(ROOT)))
with zipfile.ZipFile(OUT) as archive:
    assert archive.testzip() is None
    names = archive.namelist()
    assert len(names) == len(files) == len(set(names))
    assert 'app/src/main/AndroidManifest.xml' in names
    assert 'gradle/wrapper/gradle-wrapper.jar' in names
    for path in files:
        assert archive.read(str(path.relative_to(ROOT))) == path.read_bytes(), path
print(f'Verified source ZIP: {OUT}\nFiles: {len(files)}\nBytes: {OUT.stat().st_size}\nSHA-256: {hashlib.sha256(OUT.read_bytes()).hexdigest()}')
