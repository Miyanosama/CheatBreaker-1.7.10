package com.cheatbreaker.client.util.input;

/** Tracks physical press edges shared by LWJGL events and Windows IME fallback. */
final class KeyboardPressTracker {
    private final boolean[] down = new boolean[256];
    private final boolean[] suppressUntilRelease = new boolean[256];
    private boolean modernKeybindHandling = true;

    boolean observe(int code, boolean pressed, boolean physicallyDown) {
        if (code <= 0 || code >= down.length) return pressed;
        if (pressed) {
            if (down[code]) return false;
            down[code] = true;
            return true;
        }
        if (!physicallyDown) down[code] = false;
        return false;
    }

    void synchronize(int code, boolean physicallyDown, boolean preserveHeldPress) {
        if (code <= 0 || code >= down.length) return;
        if (!preserveHeldPress || !physicallyDown) down[code] = physicallyDown;
    }

    boolean isDown(int code) {
        return code > 0 && code < down.length && down[code];
    }

    void setDown(int code, boolean physicallyDown) {
        if (code > 0 && code < down.length) down[code] = physicallyDown;
    }

    void setModernKeybindHandling(boolean enabled) {
        modernKeybindHandling = enabled;
        if (enabled) {
            for (int code = 1; code < suppressUntilRelease.length; code++) {
                suppressUntilRelease[code] = false;
            }
        }
    }

    boolean isModernKeybindHandling() {
        return modernKeybindHandling;
    }

    void onScreenClosed(int code, boolean physicallyDown) {
        if (code > 0 && code < suppressUntilRelease.length) {
            suppressUntilRelease[code] = !modernKeybindHandling && physicallyDown;
        }
    }

    boolean isSuppressed(int code, boolean physicallyDown) {
        if (code <= 0 || code >= suppressUntilRelease.length) return false;
        if (!physicallyDown) suppressUntilRelease[code] = false;
        return suppressUntilRelease[code];
    }
}
