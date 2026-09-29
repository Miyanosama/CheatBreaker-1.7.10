package com.cheatbreaker.client.util.input;

public final class KeyboardPressTrackerTest {
    private static void check(boolean actual, boolean expected, String description) {
        if (actual != expected) throw new AssertionError(description);
    }

    public static void main(String[] args) {
        KeyboardPressTracker tracker = new KeyboardPressTracker();
        int escape = 1;
        int f11 = 87;

        check(tracker.observe(escape, true, true), true, "first Escape press");
        check(tracker.observe(escape, true, true), false, "held Escape press");
        tracker.synchronize(escape, true, true);
        check(tracker.observe(escape, true, true), false, "screen transition while Escape is held");
        check(tracker.observe(escape, false, true), false, "IME release while physically held");
        check(tracker.observe(escape, true, true), false, "IME duplicate press");
        check(tracker.observe(escape, false, false), false, "physical Escape release");
        check(tracker.observe(escape, true, true), true, "new Escape press");

        tracker.synchronize(f11, true, false);
        check(tracker.observe(f11, true, true), false, "held shortcut at GUI transition");
        tracker.setDown(f11, false);
        check(tracker.observe(f11, true, true), true, "new shortcut press");
        check(tracker.observe(f11, true, true), false, "shortcut does not repeat");

        check(tracker.observe(0, true, false), true, "IME character events remain available");

        int forward = 17;
        tracker.setModernKeybindHandling(false);
        tracker.onScreenClosed(forward, true);
        check(tracker.isSuppressed(forward, true), true, "legacy mode waits for W release");
        check(tracker.isSuppressed(forward, false), false, "releasing W removes legacy suppression");
        tracker.onScreenClosed(forward, true);
        tracker.setModernKeybindHandling(true);
        check(tracker.isSuppressed(forward, true), false, "modern mode resumes held W");
        System.out.println("Keyboard press tracker checks passed");
    }
}
