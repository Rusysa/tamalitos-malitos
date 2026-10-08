#!/usr/bin/env python3
"""Package sources; omit credentials, caches and the machine-local JVM override."""
import hashlib
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'entregables/Tamalitos-Malitos-codigo.zip'
def delivery_bytes(path):
    content = path.read_bytes()
    if path == ROOT / 'gradle.properties':
        content = b''.join(line for line in content.splitlines(keepends=True)
                           if not line.lstrip().startswith(b'org.gradle.java.home='))
    return content

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
        info = zipfile.ZipInfo.from_file(path, arcname=str(path.relative_to(ROOT)))
        archive.writestr(info, delivery_bytes(path), compress_type=zipfile.ZIP_DEFLATED)
with zipfile.ZipFile(OUT) as archive:
    assert archive.testzip() is None
    names = archive.namelist()
    assert len(names) == len(files) == len(set(names))
    assert 'app/src/main/AndroidManifest.xml' in names
    assert 'gradle/wrapper/gradle-wrapper.jar' in names
    for path in files:
        assert archive.read(str(path.relative_to(ROOT))) == delivery_bytes(path), path
    assert b'org.gradle.java.home=' not in archive.read('gradle.properties')
print(f'Verified source ZIP: {OUT}\nFiles: {len(files)}\nBytes: {OUT.stat().st_size}\nSHA-256: {hashlib.sha256(OUT.read_bytes()).hexdigest()}')
