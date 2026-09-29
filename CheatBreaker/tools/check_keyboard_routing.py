"""Build guard for the single-owner LWJGL keyboard routing contract."""

from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parent.parent
minecraft = (root / 'src/main/java/net/minecraft/client/Minecraft.java').read_text(encoding='utf-8')
gui = (root / 'src/main/java/net/minecraft/client/gui/GuiScreen.java').read_text(encoding='utf-8')
physical = (root / 'src/main/java/com/cheatbreaker/client/util/input/PhysicalKeyboard.java').read_text(encoding='utf-8')
other_keyboard_readers = [str(path.relative_to(root)) for path in (root / 'src/main/java').rglob('*.java')
                          if path.name != 'Minecraft.java' and 'Keyboard.next()' in path.read_text(encoding='utf-8')]

checks = {
    'only Minecraft drains the LWJGL keyboard queue':
        not other_keyboard_readers and minecraft.count('while (Keyboard.next())') == 2,
    'GUI events have one dispatcher':
        minecraft.count('screenAtEvent.handleKeyboardInput();') == 2,
    'GUI key presses do not run global shortcuts in the game loop':
        'if (!keyPressed || screenAtEvent == null && acceptedPress) this.func_152348_aa();' in minecraft,
    'Shift+Tab is routed by the current screen':
        'if (screenAtEvent == null && (PhysicalKeyboard.isKeyDown(42) || PhysicalKeyboard.isKeyDown(54))' in minecraft,
    'IME character events are accepted by GUI handlers':
        'key == 0 && character != 0 && Character.isDefined(character)' in gui,
    'Escape repeats are filtered before GUI actions':
        'boolean acceptedEscape = key != Keyboard.KEY_ESCAPE || freshKeyPress && !Keyboard.isRepeatEvent();' in gui,
    'physical fallback queues actions only while no GUI owns input':
        'if (this.currentScreen != null) break;\n                    KeyBinding.onTick(code);' in minecraft
        and 'KeyBinding.onTick(code);' not in physical,
    'modern keybind handling is applied when a GUI closes':
        'PhysicalKeyboard.onScreenClosed(this.gameSettings);' in minecraft
        and 'PRESS_TRACKER.isModernKeybindHandling()' in physical,
}

for description, passed in checks.items():
    if not passed:
        raise SystemExit(f'Keyboard routing check failed: {description}')

print('Keyboard routing checks passed')

source = root / 'src/main/java/com/cheatbreaker/client/util/input/KeyboardPressTracker.java'
test = root / 'src/test/java/com/cheatbreaker/client/util/input/KeyboardPressTrackerTest.java'
(root / 'target').mkdir(exist_ok=True)
with tempfile.TemporaryDirectory(prefix='keyboard-check-', dir=str(root / 'target')) as output:
    subprocess.run(['javac', '-encoding', 'UTF-8', '-source', '8', '-target', '8',
                    '-d', output, str(source), str(test)], check=True)
    subprocess.run(['java', '-cp', output,
                    'com.cheatbreaker.client.util.input.KeyboardPressTrackerTest'], check=True)
