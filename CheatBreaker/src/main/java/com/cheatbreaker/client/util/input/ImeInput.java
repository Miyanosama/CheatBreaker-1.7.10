package com.cheatbreaker.client.util.input;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.win32.StdCallLibrary;
import com.cheatbreaker.client.ui.overlay.element.InputFieldElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenBook;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiEditSign;
import org.lwjgl.opengl.Display;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

/** Associates the Windows IME only while an actual text editor has focus. */
public final class ImeInput {
    private static final int CFS_POINT = 0x0002;
    private static final int CFS_EXCLUDE = 0x0080;
    private static final int IACE_DEFAULT = 0x0010;
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");
    private static final Map<GuiScreen, List<FocusEntry>> fields = new WeakHashMap<GuiScreen, List<FocusEntry>>();
    private static Pointer window;
    private static Pointer savedContext;
    private static boolean detached;
    private static boolean caretCreated;
    private static boolean unavailable;

    private ImeInput() {}

    public static void beginScreen(GuiScreen screen) {
        List<FocusEntry> entries = fields.get(screen);
        if (entries != null) {
            for (int index = entries.size() - 1; index >= 0; index--) {
                if (entries.get(index).field instanceof GuiTextField) entries.remove(index);
            }
        }
    }

    public static void focusChanged(Object field, BooleanSupplier focused) {
        Minecraft minecraft = Minecraft.getMinecraft();
        GuiScreen screen = minecraft == null ? null : minecraft.currentScreen;
        if (screen == null) return;
        List<FocusEntry> entries = fields.get(screen);
        if (entries == null) {
            entries = new ArrayList<FocusEntry>();
            fields.put(screen, entries);
        }
        for (FocusEntry entry : entries) {
            if (entry.field == field) {
                update(screen);
                return;
            }
        }
        entries.add(new FocusEntry(field, focused));
        update(screen);
    }

    public static void update(GuiScreen screen) {
        if (!WINDOWS || unavailable || !Display.isCreated()) return;
        boolean textFocused = screen instanceof GuiEditSign
                || screen instanceof GuiScreenBook && ((GuiScreenBook) screen).isEditingText();
        Object focusedField = null;
        List<FocusEntry> entries = fields.get(screen);
        if (entries != null) {
            for (FocusEntry entry : entries) {
                if (entry.focused.getAsBoolean()) {
                    textFocused = true;
                    focusedField = entry.field;
                    break;
                }
            }
        }
        try {
            Pointer currentWindow = windowHandle();
            if (currentWindow == null) return;
            if (window == null || Pointer.nativeValue(window) != Pointer.nativeValue(currentWindow)) {
                window = currentWindow;
                savedContext = null;
                detached = false;
                caretCreated = false;
            }
            if (textFocused) {
                if (detached) {
                    Imm32.INSTANCE.ImmAssociateContext(window, savedContext);
                    detached = false;
                    savedContext = null;
                }
                Pointer context = Imm32.INSTANCE.ImmGetContext(window);
                if (context == null) {
                    // LWJGL can recreate its HWND when switching display modes.
                    // The saved HIMC belongs to the old window, so restore this window's default.
                    if (Imm32.INSTANCE.ImmAssociateContextEx(window, Pointer.NULL, IACE_DEFAULT)) {
                        context = Imm32.INSTANCE.ImmGetContext(window);
                    }
                }
                if (context != null) {
                    try {
                        positionCandidateWindow(screen, focusedField, context);
                    } finally {
                        Imm32.INSTANCE.ImmReleaseContext(window, context);
                    }
                }
            } else if (!detached) {
                if (caretCreated) {
                    User32.INSTANCE.DestroyCaret();
                    caretCreated = false;
                }
                savedContext = Imm32.INSTANCE.ImmAssociateContext(window, Pointer.NULL);
                detached = savedContext != null;
            } else if (caretCreated) {
                User32.INSTANCE.DestroyCaret();
                caretCreated = false;
            }
        } catch (Throwable error) {
            unavailable = true;
            System.err.println("[CB] IME focus control unavailable: " + error);
        }
    }

    private static void positionCandidateWindow(GuiScreen screen, Object focusedField, Pointer context) {
        if (screen == null) return;
        int[] bounds = focusedField instanceof GuiTextField
                ? ((GuiTextField) focusedField).getImeCaretBounds()
                : focusedField instanceof InputFieldElement
                ? ((InputFieldElement) focusedField).getImeCaretBounds() : null;
        // Sign and book editors own their text input without using a GuiTextField.
        if (bounds == null && (screen instanceof GuiEditSign || screen instanceof GuiScreenBook)) {
            int x = screen.width / 2;
            int y = screen.height / 2;
            bounds = new int[] {x, y, x - 80, y - 8, x + 80, y + 8};
        }
        if (bounds == null) return;

        Minecraft minecraft = Minecraft.getMinecraft();
        int scale = new ScaledResolution(minecraft, minecraft.displayWidth, minecraft.displayHeight).getScaleFactor();
        // Some modern IMEs query the Win32 caret instead of the IMM position.
        // Keep an invisible system caret at the same location as Minecraft's drawn cursor.
        if (!caretCreated) caretCreated = User32.INSTANCE.CreateCaret(window, Pointer.NULL, 1, 8 * scale);
        if (caretCreated && !User32.INSTANCE.SetCaretPos(bounds[0] * scale, bounds[1] * scale)) {
            caretCreated = false;
        }
        CandidateForm candidate = new CandidateForm();
        candidate.dwIndex = 0;
        candidate.dwStyle = CFS_EXCLUDE;
        candidate.ptCurrentPos.x = bounds[0] * scale;
        candidate.ptCurrentPos.y = bounds[1] * scale;
        candidate.rcArea.left = bounds[2] * scale;
        candidate.rcArea.top = bounds[3] * scale;
        candidate.rcArea.right = bounds[4] * scale;
        candidate.rcArea.bottom = bounds[5] * scale;
        Imm32.INSTANCE.ImmSetCandidateWindow(context, candidate);

        CompositionForm composition = new CompositionForm();
        composition.dwStyle = CFS_POINT;
        composition.ptCurrentPos.x = candidate.ptCurrentPos.x;
        composition.ptCurrentPos.y = candidate.ptCurrentPos.y;
        Imm32.INSTANCE.ImmSetCompositionWindow(context, composition);
    }

    private static Pointer windowHandle() throws ReflectiveOperationException {
        Field implementationField = Display.class.getDeclaredField("display_impl");
        implementationField.setAccessible(true);
        Object implementation = implementationField.get(null);
        Field handleField = implementation.getClass().getDeclaredField("hwnd");
        handleField.setAccessible(true);
        long handle = handleField.getLong(implementation);
        return handle == 0 ? null : new Pointer(handle);
    }

    private static final class FocusEntry {
        private final Object field;
        private final BooleanSupplier focused;

        private FocusEntry(Object field, BooleanSupplier focused) {
            this.field = field;
            this.focused = focused;
        }
    }

    private interface Imm32 extends StdCallLibrary {
        Imm32 INSTANCE = (Imm32) Native.loadLibrary("imm32", Imm32.class);
        Pointer ImmAssociateContext(Pointer window, Pointer context);
        boolean ImmAssociateContextEx(Pointer window, Pointer context, int flags);
        Pointer ImmGetContext(Pointer window);
        boolean ImmReleaseContext(Pointer window, Pointer context);
        boolean ImmSetCandidateWindow(Pointer context, CandidateForm form);
        boolean ImmSetCompositionWindow(Pointer context, CompositionForm form);
    }

    private interface User32 extends StdCallLibrary {
        User32 INSTANCE = (User32) Native.loadLibrary("user32", User32.class);
        boolean CreateCaret(Pointer window, Pointer bitmap, int width, int height);
        boolean SetCaretPos(int x, int y);
        boolean DestroyCaret();
    }

    public static class Point extends Structure {
        public int x;
        public int y;

        @Override protected List getFieldOrder() { return Arrays.asList("x", "y"); }
    }

    public static class Rect extends Structure {
        public int left;
        public int top;
        public int right;
        public int bottom;

        @Override protected List getFieldOrder() { return Arrays.asList("left", "top", "right", "bottom"); }
    }

    public static class CandidateForm extends Structure {
        public int dwIndex;
        public int dwStyle;
        public Point ptCurrentPos = new Point();
        public Rect rcArea = new Rect();

        @Override protected List getFieldOrder() {
            return Arrays.asList("dwIndex", "dwStyle", "ptCurrentPos", "rcArea");
        }
    }

    public static class CompositionForm extends Structure {
        public int dwStyle;
        public Point ptCurrentPos = new Point();
        public Rect rcArea = new Rect();

        @Override protected List getFieldOrder() {
            return Arrays.asList("dwStyle", "ptCurrentPos", "rcArea");
        }
    }
}
