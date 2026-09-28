package com.cheatbreaker.client.util.input;

import com.sun.jna.Callback;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Receives Windows Raw Input without replacing LWJGL's window procedure. */
public final class RawMouseInput {
    private static final int WM_INPUT = 0x00FF;
    private static final int RID_INPUT = 0x10000003;
    private static final int RIDEV_INPUTSINK = 0x00000100;

    private static final AtomicInteger deltaX = new AtomicInteger();
    private static final AtomicInteger deltaY = new AtomicInteger();
    private static volatile boolean enabled;
    private static volatile boolean available;
    private static WindowProc receiverProc;
    private static WindowClass receiverClass;

    private RawMouseInput() {}

    public static synchronized void setEnabled(boolean value) {
        if (value && !available) {
            available = startReceiver();
        }
        enabled = value && available;
        deltaX.set(0);
        deltaY.set(0);
        if (value && !available) {
            System.err.println("[CB] Raw Mouse Input is unavailable; using standard LWJGL input.");
        }
    }

    public static boolean consume(net.minecraft.util.MouseHelper mouseHelper) {
        if (!enabled || !available) return false;
        mouseHelper.deltaX = deltaX.getAndSet(0);
        mouseHelper.deltaY = deltaY.getAndSet(0);
        return true;
    }

    private static boolean startReceiver() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win") || !Display.isCreated()) {
            return false;
        }

        final CountDownLatch ready = new CountDownLatch(1);
        Thread receiver = new Thread(() -> runReceiver(ready), "CheatBreaker Raw Input");
        receiver.setDaemon(true);
        receiver.start();
        try {
            return ready.await(5, TimeUnit.SECONDS) && available;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void runReceiver(CountDownLatch ready) {
        try {
            receiverProc = (hwnd, message, wParam, lParam) -> {
                if (message == WM_INPUT && enabled && Display.isActive() && Mouse.isGrabbed()) {
                    readRawMouse(lParam);
                }
                return User32.INSTANCE.DefWindowProcW(hwnd, message, wParam, lParam);
            };

            Pointer instance = Kernel32.INSTANCE.GetModuleHandleW(null);
            WString name = new WString("CheatBreakerRawInput" + System.nanoTime());
            receiverClass = new WindowClass();
            receiverClass.windowProc = receiverProc;
            receiverClass.instance = instance;
            receiverClass.className = name;
            receiverClass.write();
            if (User32.INSTANCE.RegisterClassW(receiverClass) == 0) {
                throw new IllegalStateException("RegisterClassW failed: " + Native.getLastError());
            }

            Pointer window = User32.INSTANCE.CreateWindowExW(0, name, new WString(""), 0,
                    0, 0, 0, 0, null, null, instance, null);
            if (window == null) {
                throw new IllegalStateException("CreateWindowExW failed: " + Native.getLastError());
            }

            RawInputDevice device = new RawInputDevice();
            device.usagePage = 1;
            device.usage = 2;
            device.flags = RIDEV_INPUTSINK;
            device.target = window;
            device.write();
            if (!User32.INSTANCE.RegisterRawInputDevices(device, 1, device.size())) {
                throw new IllegalStateException("RegisterRawInputDevices failed: " + Native.getLastError());
            }

            available = true;
            ready.countDown();

            // Messages for this window are processed on its own thread. LWJGL's window is untouched.
            Memory message = new Memory(64);
            while (User32.INSTANCE.GetMessageW(message, null, 0, 0) > 0) {
                User32.INSTANCE.DispatchMessageW(message);
            }
        } catch (Throwable throwable) {
            available = false;
            System.err.println("[CB] Could not initialize Windows Raw Input: " + throwable);
        } finally {
            ready.countDown();
        }
    }

    private static void readRawMouse(Pointer rawInputHandle) {
        try {
            int headerSize = Native.POINTER_SIZE == 8 ? 24 : 16;
            IntByReference size = new IntByReference();
            int result = User32.INSTANCE.GetRawInputData(rawInputHandle, RID_INPUT, null, size, headerSize);
            if (result != 0 || size.getValue() < headerSize + 20) return;

            Memory buffer = new Memory(size.getValue());
            result = User32.INSTANCE.GetRawInputData(rawInputHandle, RID_INPUT, buffer, size, headerSize);
            if (result == -1 || buffer.getInt(0) != 0) return;

            long mouseOffset = headerSize;
            if ((buffer.getShort(mouseOffset) & 1) != 0) return; // Absolute motion is not a delta.
            deltaX.addAndGet(buffer.getInt(mouseOffset + 12));
            deltaY.addAndGet(-buffer.getInt(mouseOffset + 16)); // Windows Y increases downward.
        } catch (Throwable ignored) {
            // Malformed device data must not interrupt the game's input loop.
        }
    }

    public interface WindowProc extends Callback {
        Pointer invoke(Pointer hwnd, int message, Pointer wParam, Pointer lParam);
    }

    private interface User32 extends StdCallLibrary {
        User32 INSTANCE = (User32) Native.loadLibrary("user32", User32.class);

        short RegisterClassW(WindowClass windowClass);
        Pointer CreateWindowExW(int exStyle, WString className, WString title, int style,
                                int x, int y, int width, int height, Pointer parent, Pointer menu,
                                Pointer instance, Pointer parameter);
        boolean RegisterRawInputDevices(RawInputDevice device, int count, int size);
        int GetRawInputData(Pointer rawInput, int command, Pointer data, IntByReference size, int headerSize);
        int GetMessageW(Pointer message, Pointer window, int minFilter, int maxFilter);
        Pointer DispatchMessageW(Pointer message);
        Pointer DefWindowProcW(Pointer hwnd, int message, Pointer wParam, Pointer lParam);
    }

    private interface Kernel32 extends StdCallLibrary {
        Kernel32 INSTANCE = (Kernel32) Native.loadLibrary("kernel32", Kernel32.class);
        Pointer GetModuleHandleW(WString name);
    }

    public static class WindowClass extends Structure {
        public int style;
        public WindowProc windowProc;
        public int classExtra;
        public int windowExtra;
        public Pointer instance;
        public Pointer icon;
        public Pointer cursor;
        public Pointer background;
        public Pointer menuName;
        public WString className;

        @Override
        protected List getFieldOrder() {
            return Arrays.asList("style", "windowProc", "classExtra", "windowExtra", "instance",
                    "icon", "cursor", "background", "menuName", "className");
        }
    }

    public static class RawInputDevice extends Structure {
        public short usagePage;
        public short usage;
        public int flags;
        public Pointer target;

        @Override
        protected List getFieldOrder() {
            return Arrays.asList("usagePage", "usage", "flags", "target");
        }
    }
}
