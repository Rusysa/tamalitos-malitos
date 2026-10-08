"""Run owned DATA tests without compiling unfinished parallel UI/Drive sources.
Requires parent-installed JDK17/SDK35/Gradle-resolved runtime classpath.
No project configuration is modified; all generated files are under build/.
Usage: source tools/env.sh && python3 app/src/test/java/com/tamalitos/malitos/DataRunTests.py
"""
from pathlib import Path
import os
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[7]
BUILD = ROOT / "build/data-isolated"
BUILD.mkdir(parents=True, exist_ok=True)
classpath_file = ROOT / "build/data-runtime-classpath.txt"
if not classpath_file.exists():
    raise SystemExit("Resolve existing debugUnitTestRuntimeClasspath before running; see docs/DATA.md")
artifacts = [Path(p) for p in classpath_file.read_text().split(os.pathsep)]
jars = []
for artifact in artifacts:
    if not artifact.exists():
        # Gradle's runtime configuration includes the app's own unfinished jar.
        if artifact.is_relative_to(ROOT / "app/build"):
            continue
        raise SystemExit(f"Resolved dependency disappeared: {artifact}")
    if artifact.suffix == ".aar":
        destination = BUILD / "aar" / artifact.stem
        destination.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(artifact) as archive:
            for name in archive.namelist():
                if name == "classes.jar" or (name.startswith("libs/") and name.endswith(".jar")):
                    target = destination / Path(name).name
                    target.write_bytes(archive.read(name))
                    jars.append(target)
    elif artifact.suffix == ".jar":
        jars.append(artifact)

sdk = Path(os.environ.get("ANDROID_HOME", os.environ.get("ANDROID_SDK_ROOT", "")))
android_jar = sdk / "platforms/android-35/android.jar"
if not android_jar.exists():
    raise SystemExit("SDK35 android.jar unavailable; source tools/env.sh")
cache = Path.home() / ".gradle/caches/modules-2/files-2.1"
compiler = list(cache.rglob("kotlin-compiler-embeddable-2.1.20.jar"))
if not compiler:
    raise SystemExit("Parent Gradle has not resolved Kotlin 2.1.20 compiler")
compiler_cp = os.pathsep.join(str(p) for p in [*compiler, *cache.rglob("*.jar")])
compile_cp = os.pathsep.join(str(p) for p in [*jars, android_jar])
classes = BUILD / "classes"
classes.mkdir(exist_ok=True)
main = ROOT / "app/src/main/java/com/tamalitos/malitos"
tests = ROOT / "app/src/test/java/com/tamalitos/malitos"
sources = [main / p for p in ("Models.kt", "BusinessRules.kt", "BusinessStore.kt")]
sources += [tests / p for p in ("BusinessRulesTest.kt", "DataModelsTest.kt", "BusinessStoreTest.kt", "BackupTest.kt")]
command = ["java", "-Xmx768m", "-cp", compiler_cp, "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
           "-no-stdlib", "-no-reflect", "-jvm-target", "17", "-classpath", compile_cp, "-d", str(classes), *map(str, sources)]
result = subprocess.run(command)
if result.returncode:
    raise SystemExit(result.returncode)
selected = sys.argv[1:] or ["BusinessRulesTest", "DataModelsTest", "BusinessStoreTest", "BackupTest"]
selected = [s if "." in s else "com.tamalitos.malitos." + s for s in selected]
runtime_cp = os.pathsep.join([str(classes), *map(str, jars), str(android_jar)])
command = ["java", "-Xmx768m", "-Drobolectric.dependency.repo.url=https://repo.maven.apache.org/maven2",
           "-cp", runtime_cp, "org.junit.runner.JUnitCore", *selected]
raise SystemExit(subprocess.run(command, cwd=ROOT).returncode)
