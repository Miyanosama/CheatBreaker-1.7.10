package com.cheatbreaker.client.util.input;

import com.sun.jna.Native;
import com.sun.jna.win32.StdCallLibrary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

import java.util.ArrayList;
import java.util.List;

/** Polls the physical key state when an IME consumes LWJGL keyboard events. */
public final class PhysicalKeyboard {
    private static final boolean WINDOWS = System.getProperty("os.name", "")
            .toLowerCase().contains("win");
    private static final int MAPVK_VSC_TO_VK_EX = 3;
    private static final KeyboardPressTracker PRESS_TRACKER = new KeyboardPressTracker();
    private static final int KEY_COUNT = 256;
    private static Boolean nativeAvailable;
    private static final int[] GAME_SHORTCUTS = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11,
            15, 20, 25, 30, 31, 33, 35, 48, 54, 59, 61, 62};

    private PhysicalKeyboard() {}

    public static boolean isBindingDown(KeyBinding binding) {
        if (!Display.isActive()) return false;
        int code = binding.getKeyCode();
        if (code < 0) return Mouse.isButtonDown(code + 100);
        if (code == 0) return false;
        boolean down = isKeyDown(code);
        return down && !PRESS_TRACKER.isSuppressed(code, down);
    }

    public static void setModernKeybindHandling(boolean enabled) {
        PRESS_TRACKER.setModernKeybindHandling(enabled);
    }

    /** Called only when a GUI closes into gameplay. */
    public static void onScreenClosed(GameSettings settings) {
        for (KeyBinding binding : settings.keyBindings) {
            int code = binding.getKeyCode();
            if (code > 0 && code < KEY_COUNT) {
                boolean down = isKeyDown(code);
                PRESS_TRACKER.onScreenClosed(code, down);
                if (PRESS_TRACKER.isSuppressed(code, down)) KeyBinding.setKeyBindState(code, false);
            }
        }
    }

    public static boolean shouldActivateBinding(int code, boolean pressed) {
        if (code <= 0 || !pressed) return false;
        return !PRESS_TRACKER.isSuppressed(code, isKeyDown(code));
    }

    public static boolean isKeyDown(int code) {
        if (code <= 0 || !Display.isActive()) return false;
        if (isNativeAvailable()) {
            try {
                // LWJGL 2 key codes follow set-1 scan codes; codes with bit 7 set use E0.
                int scan = code < 128 ? code : 0xE000 | (code & 0x7F);
                int virtualKey = User32.INSTANCE.MapVirtualKeyW(scan, MAPVK_VSC_TO_VK_EX);
                if (virtualKey != 0) {
                    return (User32.INSTANCE.GetAsyncKeyState(virtualKey) & 0x8000) != 0;
                }
            } catch (Throwable ignored) {
                nativeAvailable = false;
            }
        }
        return Keyboard.isKeyDown(code);
    }

    public static boolean isNativeAvailable() {
        if (!WINDOWS) return false;
        if (nativeAvailable == null) {
            try {
                nativeAvailable = User32.INSTANCE.MapVirtualKeyW(17, MAPVK_VSC_TO_VK_EX) != 0;
            } catch (Throwable ignored) {
                nativeAvailable = false;
            }
        }
        return nativeAvailable;
    }

    public static boolean observeGameKeyEvent(int code, boolean pressed) {
        boolean nativeAvailable = isNativeAvailable();
        boolean physicallyDown = !pressed && isKeyDown(code);
        boolean fresh = PRESS_TRACKER.observe(code, pressed, physicallyDown);
        if (!pressed) PRESS_TRACKER.isSuppressed(code, physicallyDown);
        return nativeAvailable ? fresh : pressed;
    }

    /** GUI key presses must not be replayed as fresh game presses when a screen closes. */
    public static void synchronizeScreenKeys(GameSettings settings) {
        if (!isNativeAvailable()) return;
        for (KeyBinding binding : settings.keyBindings) {
            int code = binding.getKeyCode();
            if (code > 0 && code < KEY_COUNT) {
                boolean down = isKeyDown(code);
                PRESS_TRACKER.synchronize(code, down, code == Keyboard.KEY_ESCAPE);
            }
        }
        for (int code : GAME_SHORTCUTS) {
            boolean down = isKeyDown(code);
            PRESS_TRACKER.synchronize(code, down, code == Keyboard.KEY_ESCAPE);
        }
    }

    /** Reconciles bindings with hardware state and returns presses missed by LWJGL. */
    public static int[] pollMissingGamePresses(GameSettings settings) {
        if (!isNativeAvailable()) return new int[0];
        boolean[] used = new boolean[KEY_COUNT];
        for (KeyBinding binding : settings.keyBindings) {
            int code = binding.getKeyCode();
            if (code > 0 && code < used.length) used[code] = true;
        }
        for (int code : GAME_SHORTCUTS) used[code] = true;

        List<Integer> missing = new ArrayList<Integer>();
        for (int code = 1; code < used.length; code++) {
            if (!used[code]) continue;
            boolean down = isKeyDown(code);
            boolean suppressed = PRESS_TRACKER.isSuppressed(code, down);
            if (down && !suppressed && !PRESS_TRACKER.isDown(code)) {
                missing.add(code);
            }
            PRESS_TRACKER.setDown(code, down);
            KeyBinding.setKeyBindState(code, down && !suppressed);
        }
        int[] result = new int[missing.size()];
        for (int index = 0; index < result.length; index++) result[index] = missing.get(index);
        return result;
    }

    public static boolean isMovementDown(Minecraft minecraft, KeyBinding binding) {
        // Minecraft screens, including chat, take priority over player movement.
        if (minecraft.currentScreen != null) return false;
        int code = binding.getKeyCode();
        if (code > 0 && PRESS_TRACKER.isSuppressed(code, isKeyDown(code))) return false;
        if (!PRESS_TRACKER.isModernKeybindHandling()) return binding.getIsKeyPressed();
        return isBindingDown(binding);
    }

    private interface User32 extends StdCallLibrary {
        User32 INSTANCE = (User32) Native.loadLibrary("user32", User32.class);
        int MapVirtualKeyW(int code, int mapType);
        short GetAsyncKeyState(int virtualKey);
    }
}
