package com.cheatbreaker.client.util.input;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenBook;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiEditSign;
import org.lwjgl.opengl.Display;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

/** Associates the Windows IME only while an actual text editor has focus. */
public final class ImeInput {
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");
    private static final Map<GuiScreen, List<FocusEntry>> fields = new WeakHashMap<GuiScreen, List<FocusEntry>>();
    private static Pointer window;
    private static Pointer savedContext;
    private static boolean detached;
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
        List<FocusEntry> entries = fields.get(screen);
        if (entries != null) {
            for (FocusEntry entry : entries) {
                if (entry.focused.getAsBoolean()) {
                    textFocused = true;
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
            }
            if (textFocused) {
                if (detached) {
                    Imm32.INSTANCE.ImmAssociateContext(window, savedContext);
                    detached = false;
                    savedContext = null;
                }
            } else if (!detached) {
                savedContext = Imm32.INSTANCE.ImmAssociateContext(window, Pointer.NULL);
                detached = savedContext != null;
            }
        } catch (Throwable error) {
            unavailable = true;
            System.err.println("[CB] IME focus control unavailable: " + error);
        }
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
    }
}
