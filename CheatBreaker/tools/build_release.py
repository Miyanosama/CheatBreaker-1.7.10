from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import argparse
import hashlib
import os
import shutil
import subprocess
import sys

parser = argparse.ArgumentParser(description='Package the current Java 8 class overlay for CheatBreaker.')
parser.add_argument('--install', action='store_true', help='Also copy the JAR into the Minecraft version directory.')
args = parser.parse_args()

root = Path(__file__).resolve().parent.parent
subprocess.run([sys.executable, str(root / 'tools/check_keyboard_routing.py')], check=True)
classes = root / 'target' / 'classes'
rebuilt = root / 'target' / 'rebuild-classes'
output = root / 'target' / 'cheatbreaker-client-1.0-SNAPSHOT.jar'
if not classes.is_dir() or not rebuilt.is_dir():
    raise SystemExit('Missing target/classes or target/rebuild-classes; compile the base and changed sources first')
java_compiler = shutil.which('javac')
if java_compiler is None:
    raise SystemExit('Java 8 javac is required for the config value checks')
java_runtime = Path(java_compiler).with_name('java.exe' if os.name == 'nt' else 'java')
config_check_classes = root / 'target' / 'config-check-classes'
config_check_classes.mkdir(parents=True, exist_ok=True)
config_check_classpath = os.pathsep.join((str(rebuilt), str(classes), str(config_check_classes)))
subprocess.run([java_compiler, '-encoding', 'UTF-8', '-source', '8', '-target', '8',
                '-cp', config_check_classpath, '-d', str(config_check_classes),
                str(root / 'src/test/java/com/cheatbreaker/client/config/ConfigValueCodecTest.java')], check=True)
subprocess.run([str(java_runtime), '-cp', config_check_classpath,
                'com.cheatbreaker.client.config.ConfigValueCodecTest'], check=True)
raw_classes = [
    'RawMouseInput.class',
    'RawMouseInput$Kernel32.class',
    'RawMouseInput$User32.class',
    'RawMouseInput$WindowClass.class',
    'RawMouseInput$WindowProc.class',
    'RawMouseInput$RawInputDevice.class',
]
minecraft_classes = [file.name for file in (rebuilt / 'net/minecraft/client').glob('Minecraft*.class')
                     if file.name == 'Minecraft.class' or file.name.startswith('Minecraft$')]
screenshot_classes = [file.name for file in (rebuilt / 'net/minecraft/util').glob('ScreenShotHelper*.class')
                      if file.name == 'ScreenShotHelper.class' or file.name.startswith('ScreenShotHelper$')]
borderless_classes = [file.name for file in (rebuilt / 'com/cheatbreaker/client/util/display').glob('BorderlessFullscreen*.class')]
overlay = {
    'com/cheatbreaker/client/CheatBreaker.class',
    'com/cheatbreaker/client/CheatBreaker$1.class',
    *(f'com/cheatbreaker/client/util/input/{name}' for name in raw_classes),
    'com/cheatbreaker/client/util/input/PhysicalKeyboard.class',
    'com/cheatbreaker/client/util/input/PhysicalKeyboard$User32.class',
    'com/cheatbreaker/client/util/input/KeyboardPressTracker.class',
    'com/cheatbreaker/client/util/input/ImeInput.class',
    'com/cheatbreaker/client/util/input/ImeInput$FocusEntry.class',
    'com/cheatbreaker/client/util/input/ImeInput$Imm32.class',
    'com/cheatbreaker/client/util/input/ImeInput$User32.class',
    'com/cheatbreaker/client/util/input/ImeInput$Point.class',
    'com/cheatbreaker/client/util/input/ImeInput$Rect.class',
    'com/cheatbreaker/client/util/input/ImeInput$CandidateForm.class',
    'com/cheatbreaker/client/util/input/ImeInput$CompositionForm.class',
    'com/cheatbreaker/client/config/GlobalSettings.class',
    'com/cheatbreaker/client/config/Setting.class',
    'com/cheatbreaker/client/config/Setting$Type.class',
    'com/cheatbreaker/client/config/ConfigManager.class',
    'com/cheatbreaker/client/config/ConfigManager$1.class',
    'com/cheatbreaker/client/config/ConfigManager$ConfigWriter.class',
    'com/cheatbreaker/client/config/ConfigValueCodec.class',
    'com/cheatbreaker/client/config/ConfigValueCodec$1.class',
    'com/cheatbreaker/client/module/ModuleManager.class',
    'com/cheatbreaker/client/module/ToggleKeybindModule.class',
    'com/cheatbreaker/client/module/type/BlockOverlayModule.class',
    'com/cheatbreaker/client/module/type/HitboxesModule.class',
    'com/cheatbreaker/client/ui/AbstractGui.class',
    'com/cheatbreaker/client/ui/module/CBModulesGui.class',
    'com/cheatbreaker/client/ui/module/CBModulesGui$1.class',
    'com/cheatbreaker/client/ui/element/module/ModuleListElement.class',
    'com/cheatbreaker/client/ui/element/module/ModuleListElement$1.class',
    'com/cheatbreaker/client/ui/element/module/ModulePreviewElement.class',
    'com/cheatbreaker/client/ui/element/type/custom/KeybindElement.class',
    'com/cheatbreaker/client/ui/element/type/ChoiceElement.class',
    'com/cheatbreaker/client/module/type/PotionStatusModule.class',
    'com/cheatbreaker/client/module/type/ScoreboardModule.class',
    'com/cheatbreaker/client/ui/overlay/element/InputFieldElement.class',
    'com/cheatbreaker/client/ui/overlay/OverlayGui.class',
    *(f'com/cheatbreaker/client/util/display/{name}' for name in borderless_classes),
    'com/cheatbreaker/client/ui/overlay/element/DraggableElement.class',
    'net/minecraft/MinecraftMovementInputHelper.class',
    'net/minecraft/client/gui/GuiScreen.class',
    'net/minecraft/client/gui/GuiDownloadTerrain.class',
    'net/minecraft/client/gui/GuiDisconnected.class',
    'net/minecraft/client/multiplayer/GuiConnecting.class',
    'net/minecraft/client/multiplayer/GuiConnecting$1.class',
    'net/minecraft/client/gui/GuiIngameMenu.class',
    'net/minecraft/client/gui/GuiOptions.class',
    'net/minecraft/client/settings/GameSettings.class',
    'net/minecraft/client/settings/GameSettings$1.class',
    'net/minecraft/client/settings/GameSettings$Options.class',
    'net/minecraft/client/settings/GameSettings$Options$1.class',
    'net/minecraft/client/settings/GameSettings$SwitchOptions.class',
    'net/minecraft/client/gui/GuiTextField.class',
    'net/minecraft/client/gui/GuiScreenBook.class',
    'net/minecraft/client/gui/achievement/GuiAchievement.class',
    'net/minecraft/client/gui/inventory/GuiContainer.class',
    'net/minecraft/client/gui/ServerListEntryNormal.class',
    'net/minecraft/client/gui/ServerListEntryNormal$1.class',
    'net/minecraft/client/network/NetHandlerPlayClient.class',
    'net/minecraft/client/network/NetHandlerPlayClient$1.class',
    'net/minecraft/client/network/NetHandlerLoginClient.class',
    'net/minecraft/client/network/NetHandlerLoginClient$1.class',
    'net/minecraft/client/renderer/entity/RenderManager.class',
    'net/minecraft/client/renderer/RenderGlobal.class',
    'net/minecraft/client/renderer/RenderGlobal$1.class',
    'net/minecraft/client/renderer/EntityRenderer.class',
    'net/minecraft/client/renderer/InventoryEffectRenderer.class',
    'net/minecraft/util/MouseHelper.class',
    *(f'net/minecraft/util/{name}' for name in screenshot_classes),
    *(f'net/minecraft/client/{name}' for name in minecraft_classes),
    'net/minecraft/src/Config.class',
}
source_resources = {
    'assets/minecraft/client/icons/mods/block_overlay.png',
}
for name in overlay:
    if not (rebuilt / name).is_file():
        raise SystemExit(f'Missing freshly compiled class: {name}')
for name in source_resources:
    if not (root / 'src/main/resources' / name).is_file():
        raise SystemExit(f'Missing source resource: {name}')

with ZipFile(output, 'w', ZIP_DEFLATED) as jar:
    jar.writestr('META-INF/MANIFEST.MF', 'Manifest-Version: 1.0\r\nMain-Class: Start\r\n\r\n')
    for file in sorted(classes.rglob('*')):
        if not file.is_file():
            continue
        name = file.relative_to(classes).as_posix()
        if name in overlay or name in source_resources or name.upper() == 'META-INF/MANIFEST.MF' or name == 'Start.class':
            continue
        if name.startswith('com/cheatbreaker/client/util/input/RawMouseInput'):
            continue
        jar.write(file, name)
    for name in sorted(overlay):
        jar.write(rebuilt / name, name)
    for name in sorted(source_resources):
        jar.write(root / 'src/main/resources' / name, name)
    start_class = root / 'target' / 'test-classes' / 'Start.class'
    if not start_class.is_file():
        raise SystemExit('Missing target/test-classes/Start.class')
    jar.write(start_class, 'Start.class')

with ZipFile(output) as jar:
    if jar.testzip() is not None:
        raise SystemExit('JAR checksum verification failed')
    names = set(jar.namelist())
    if not overlay <= names:
        raise SystemExit('JAR is missing a freshly compiled class')
    if not source_resources <= names:
        raise SystemExit('JAR is missing a source resource')
    stale = {name for name in names if name.startswith('com/cheatbreaker/client/util/input/RawMouseInput') and name not in overlay}
    if stale:
        raise SystemExit(f'JAR contains stale Raw Input classes: {stale}')

launcher = root / 'target' / 'launcher' / 'CheatBreaker' / 'CheatBreaker.jar'
launcher.parent.mkdir(parents=True, exist_ok=True)
shutil.copy2(output, launcher)
print(f'Built {output} ({output.stat().st_size} bytes)')
print(f'SHA-256 {hashlib.sha256(output.read_bytes()).hexdigest()}')
print(f'Copied local launcher JAR to {launcher}')

if args.install:
    install = Path.home() / 'AppData' / 'Roaming' / '.minecraft' / 'versions' / 'CheatBreaker' / 'CheatBreaker.jar'
    version_json = install.with_suffix('.json')
    if not version_json.is_file():
        raise SystemExit(f'Missing Minecraft version JSON: {version_json}')
    shutil.copy2(output, install)
    if hashlib.sha256(output.read_bytes()).digest() != hashlib.sha256(install.read_bytes()).digest():
        raise SystemExit('Installed JAR hash differs from the build output')
    print(f'Installed {install}')
