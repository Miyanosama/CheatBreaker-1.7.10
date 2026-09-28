package com.cheatbreaker.client.util.display;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.win32.StdCallLibrary;
import org.lwjgl.opengl.Display;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

/** Changes only the Windows game window; LWJGL keeps its OpenGL context and desktop mode. */
public final class BorderlessFullscreen {
    private static final int GWL_STYLE = -16;
    private static final int WS_OVERLAPPEDWINDOW = 0x00CF0000;
    private static final int WS_POPUP = 0x80000000;
    private static final int MONITOR_DEFAULTTONEAREST = 2;
    private static final int SWP_FRAMECHANGED = 0x0020;
    private static final int SWP_SHOWWINDOW = 0x0040;
    private static final int SWP_NOZORDER = 0x0004;
    private static final int SWP_NOOWNERZORDER = 0x0200;

    private static Pointer window;
    private static int originalStyle;
    private static Rect originalRect;
    private static int width;
    private static int height;
    private static boolean active;

    private BorderlessFullscreen() {}

    public static boolean isActive() {
        return active;
    }

    public static int getWidth() {
        return width;
    }

    public static int getHeight() {
        return height;
    }

    public static boolean enter() {
        if (active) return true;
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")
                || Native.POINTER_SIZE != 8 || !Display.isCreated() || Display.isFullscreen()) return false;

        Pointer handle = null;
        Rect savedRect = new Rect();
        int savedStyle = 0;
        boolean styleChanged = false;
        try {
            handle = lwjglWindow();
            if (handle == null || !User32.INSTANCE.GetWindowRect(handle, savedRect)) return false;
            savedStyle = (int) Pointer.nativeValue(User32.INSTANCE.GetWindowLongPtrW(handle, GWL_STYLE));

            Pointer monitor = User32.INSTANCE.MonitorFromWindow(handle, MONITOR_DEFAULTTONEAREST);
            if (monitor == null) return false;
            MonitorInfo info = new MonitorInfo();
            info.cbSize = info.size();
            info.write();
            if (!User32.INSTANCE.GetMonitorInfoW(monitor, info)) return false;
            info.read();
            Rect bounds = info.rcMonitor;
            int monitorWidth = bounds.right - bounds.left;
            int monitorHeight = bounds.bottom - bounds.top;
            if (monitorWidth <= 0 || monitorHeight <= 0) return false;

            int borderlessStyle = (savedStyle & ~WS_OVERLAPPEDWINDOW) | WS_POPUP;
            User32.INSTANCE.SetWindowLongPtrW(handle, GWL_STYLE, new Pointer(borderlessStyle & 0xFFFFFFFFL));
            styleChanged = true;
            if (!User32.INSTANCE.SetWindowPos(handle, Pointer.NULL, bounds.left, bounds.top,
                    monitorWidth, monitorHeight, SWP_FRAMECHANGED | SWP_SHOWWINDOW | SWP_NOOWNERZORDER)) {
                throw new IllegalStateException("SetWindowPos failed: " + Native.getLastError());
            }

            Display.processMessages();
            window = handle;
            originalStyle = savedStyle;
            originalRect = savedRect;
            width = monitorWidth;
            height = monitorHeight;
            active = true;
            return true;
        } catch (Throwable error) {
            System.err.println("[CB] Borderless fullscreen unavailable: " + error);
            if (styleChanged) restore(handle, savedStyle, savedRect);
            active = false;
            window = null;
            originalRect = null;
            width = 0;
            height = 0;
            return false;
        }
    }

    public static boolean exit() {
        if (!active) return false;
        boolean restored = restore(window, originalStyle, originalRect);
        if (!restored) return false;
        active = false;
        window = null;
        originalRect = null;
        width = 0;
        height = 0;
        Display.processMessages();
        return true;
    }

    private static boolean restore(Pointer handle, int style, Rect bounds) {
        if (handle == null || bounds == null) return false;
        try {
            User32.INSTANCE.SetWindowLongPtrW(handle, GWL_STYLE, new Pointer(style & 0xFFFFFFFFL));
            return User32.INSTANCE.SetWindowPos(handle, Pointer.NULL, bounds.left, bounds.top,
                    bounds.right - bounds.left, bounds.bottom - bounds.top,
                    SWP_FRAMECHANGED | SWP_SHOWWINDOW | SWP_NOZORDER | SWP_NOOWNERZORDER);
        } catch (Throwable error) {
            System.err.println("[CB] Could not restore window style: " + error);
            return false;
        }
    }

    private static Pointer lwjglWindow() throws ReflectiveOperationException {
        Field implementationField = Display.class.getDeclaredField("display_impl");
        implementationField.setAccessible(true);
        Object implementation = implementationField.get(null);
        Field handleField = implementation.getClass().getDeclaredField("hwnd");
        handleField.setAccessible(true);
        long handle = handleField.getLong(implementation);
        return handle == 0 ? null : new Pointer(handle);
    }

    private interface User32 extends StdCallLibrary {
        User32 INSTANCE = (User32) Native.loadLibrary("user32", User32.class);

        boolean GetWindowRect(Pointer handle, Rect rectangle);
        Pointer GetWindowLongPtrW(Pointer handle, int index);
        Pointer SetWindowLongPtrW(Pointer handle, int index, Pointer value);
        Pointer MonitorFromWindow(Pointer handle, int flags);
        boolean GetMonitorInfoW(Pointer monitor, MonitorInfo info);
        boolean SetWindowPos(Pointer handle, Pointer insertAfter, int x, int y,
                             int width, int height, int flags);
    }

    public static class Rect extends Structure {
        public int left;
        public int top;
        public int right;
        public int bottom;

        @Override
        protected List getFieldOrder() {
            return Arrays.asList("left", "top", "right", "bottom");
        }
    }

    public static class MonitorInfo extends Structure {
        public int cbSize;
        public Rect rcMonitor = new Rect();
        public Rect rcWork = new Rect();
        public int dwFlags;

        @Override
        protected List getFieldOrder() {
            return Arrays.asList("cbSize", "rcMonitor", "rcWork", "dwFlags");
        }
    }
}
